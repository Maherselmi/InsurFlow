package tn.esprit.insureflow_back.Agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.image.Image;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import tn.esprit.insureflow_back.Domain.ENUMS.AgentName;
import tn.esprit.insureflow_back.Domain.Entities.AgentResult;
import tn.esprit.insureflow_back.Domain.Entities.Claim;
import tn.esprit.insureflow_back.Domain.Entities.ClaimDocument;
import tn.esprit.insureflow_back.Service.AgentLearningMemoryService;
import tn.esprit.insureflow_back.Service.AiAgentConfigService;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class AgentEstimateur {

    private static final String AGENT_NAME = "AgentEstimateur";
    private static final String CONFIG_KEY  = "AGENT_ESTIMATEUR";

    private static final double DEFAULT_CONFIDENCE = 0.50;

    private static final int   MAX_IMAGES          = 1;
    private static final int   IMAGE_MAX_DIMENSION = 640;
    private static final float IMAGE_JPEG_QUALITY  = 0.75f;

    private static final int MAX_LEARNING_CHARS    = 180;
    private static final int MAX_DESCRIPTION_CHARS = 140;

    private static final long THRESHOLD_CACHE_TTL_MS = 60_000L;

    private volatile double cachedThreshold        = Double.NaN;
    private volatile long   thresholdCacheExpiresAt = 0L;

    private static final Set<String> SUPPORTED_IMAGE_TYPES = Set.of(
            "image/jpeg",
            "image/jpg",
            "image/png",
            "image/webp"
    );

    private final Map<String, String> imageBase64Cache = new ConcurrentHashMap<>();

    // ─── Patterns de fallback ─────────────────────────────────────────────────

    private static final Pattern RANGE_AMOUNT_PATTERN = Pattern.compile(
            "(\\d{1,6}(?:[.,]\\d{1,2})?)\\s*(?:à|-)\\s*(\\d{1,6}(?:[.,]\\d{1,2})?)\\s*DT",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern SINGLE_AMOUNT_PATTERN = Pattern.compile(
            "(\\d{3,6}(?:[.,]\\d{1,2})?)\\s*DT",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern DINAR_PATTERN = Pattern.compile(
            "(\\d{2,6}(?:[.,]\\d{1,2})?)\\s*(?:dinars?|TND|tnd)",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern NUMBER_RANGE_PATTERN = Pattern.compile(
            "(\\d{3,6})\\s*(?:à|et|-|ou)\\s*(\\d{3,6})",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern STANDALONE_NUMBER_PATTERN = Pattern.compile(
            "(?:environ|estimé?[eés]?|coût|montant|réparation)[^\\d]*(\\d{3,6})",
            Pattern.CASE_INSENSITIVE
    );

    // ─── Clés JSON attendues du LLM ──────────────────────────────────────────

    private static final Set<String> MIN_KEYS = Set.of(
            "estimationMin", "estimation_min", "min", "minimum", "minEstimation"
    );

    private static final Set<String> MAX_KEYS = Set.of(
            "estimationMax", "estimation_max", "max", "maximum", "maxEstimation"
    );

    private static final Set<String> MOY_KEYS = Set.of(
            "estimationMoyenne", "estimation_moyenne", "moyenne",
            "average", "estimationMoy", "moyEstimation", "estimationMean"
    );

    private static final Set<String> CONF_KEYS = Set.of(
            "confidence", "confiance", "score", "certainty"
    );

    private static final Set<String> ANALYSE_KEYS = Set.of(
            "analyse", "analysis", "description",
            "commentaire", "comment", "justification", "explication"
    );

    private static final Set<String> SEVERITY_KEYS = Set.of(
            "severity", "gravite", "gravité",
            "damageSeverity", "niveauGravite", "niveau_gravite"
    );

    private static final Set<String> INDICATOR_KEYS = Set.of(
            "damageIndicators", "indicateurs", "visibleSigns",
            "signesVisibles", "signes_visibles",
            "elementsEndommages", "élémentsEndommagés"
    );

    // ─── Dépendances ─────────────────────────────────────────────────────────

    private final ChatLanguageModel        visionModel;
    private final ObjectMapper             objectMapper;
    private final AiAgentConfigService     aiAgentConfigService;
    private final AgentLearningMemoryService learningMemoryService;

    public AgentEstimateur(
            @Qualifier("visionLanguageModel") ChatLanguageModel visionModel,
            ObjectMapper objectMapper,
            AiAgentConfigService aiAgentConfigService,
            AgentLearningMemoryService learningMemoryService
    ) {
        this.visionModel          = visionModel;
        this.objectMapper         = objectMapper;
        this.aiAgentConfigService = aiAgentConfigService;
        this.learningMemoryService = learningMemoryService;
    }

    // =========================================================================
    // Point d'entrée public
    // =========================================================================

    public AgentResult estimate(
            Claim claim,
            AgentResult routeResult,
            AgentResult validationResult
    ) {
        long startedAt = System.nanoTime();

        if (claim == null) {
            throw new IllegalArgumentException("Le claim ne doit pas être null");
        }

        log.info("{} - analyse des images du sinistre #{}", AGENT_NAME, claim.getId());

        List<ClaimDocument> images = extractValidImages(claim);

        if (images.isEmpty()) {
            log.warn("Aucune image exploitable trouvée pour le dossier #{}", claim.getId());
            return finalizeResult(
                    startedAt, "NO_IMAGE", claim,
                    0.0, 0.0, 0.0, DEFAULT_CONFIDENCE,
                    "Aucune image exploitable fournie", true, null
            );
        }

        String typeDetecte          = extractClaimType(routeResult);
        double humanReviewThreshold = getHumanReviewThreshold();

        long parallelStartedAt = System.nanoTime();

        CompletableFuture<String>             learningFuture = CompletableFuture.supplyAsync(
                () -> learningMemoryService.buildMemoryBlock(AgentName.AGENT_ESTIMATEUR, claim.getId())
        );
        CompletableFuture<List<EncodedImage>> imagesFuture   = CompletableFuture.supplyAsync(
                () -> loadAndEncodeImages(images)
        );

        CompletableFuture.allOf(learningFuture, imagesFuture).join();

        String             learningExamples = learningFuture.join();
        List<EncodedImage> encodedImages    = imagesFuture.join();

        log.info(
                "{} - learning + images claim #{} terminés en {} ms",
                AGENT_NAME, claim.getId(), elapsedMs(parallelStartedAt)
        );

        if (learningExamples == null || learningExamples.isBlank()) {
            learningExamples = "";
        } else {
            learningExamples = truncate(learningExamples, MAX_LEARNING_CHARS);
        }

        if (encodedImages.isEmpty()) {
            log.warn("Aucune image lisible pour le dossier #{}", claim.getId());
            return finalizeResult(
                    startedAt, "IMAGE_READ_ERROR", claim,
                    0.0, 0.0, 0.0, DEFAULT_CONFIDENCE,
                    "Images illisibles ou introuvables sur disque", true, null
            );
        }

        try {
            String        prompt      = buildPrompt(claim, routeResult, validationResult,
                    humanReviewThreshold, learningExamples);
            List<Content> contents    = buildVisionContents(prompt, encodedImages);
            UserMessage   userMessage = UserMessage.from(contents);

            log.info(
                    "{} - envoi modèle vision claim #{} | images={} | prompt={} chars | seuil={}",
                    AGENT_NAME, claim.getId(), encodedImages.size(),
                    prompt.length(), humanReviewThreshold
            );

            String rawResponse = callVisionModelSafely(userMessage, claim.getId());

            if (log.isDebugEnabled()) {
                log.debug("Réponse brute vision claim #{} : {}", claim.getId(), rawResponse);
            }

            AgentResult result = parseResponse(rawResponse, claim, typeDetecte, humanReviewThreshold);

            log.info(
                    "{} - estimation claim #{} terminée en {} ms | conclusion={} | confidence={} | humanReview={}",
                    AGENT_NAME, claim.getId(), elapsedMs(startedAt),
                    result.getConclusion(), result.getConfidenceScore(), result.isNeedsHumanReview()
            );

            return result;

        } catch (Exception e) {
            log.error("Erreur AgentEstimateur dossier #{}: {}", claim.getId(), e.getMessage(), e);
            return finalizeResult(
                    startedAt, "TECHNICAL_ERROR", claim,
                    0.0, 0.0, 0.0, 0.0,
                    "Erreur technique : " + e.getMessage(), true, null
            );
        }
    }

    // =========================================================================
    // Construction du prompt — VERSION CORRIGÉE
    // =========================================================================

    /**
     * CORRECTION PRINCIPALE :
     * Le nouveau prompt ancre les montants par fourchette de gravité pour éviter
     * que le LLM sur-estime des dommages légers. Il force également un raisonnement
     * élément par élément avant de produire le JSON final.
     */
    private String buildPrompt(
            Claim claim,
            AgentResult routeResult,
            AgentResult validationResult,
            double humanReviewThreshold,
            String learningExamples
    ) {
        String typeDetecte       = extractClaimType(routeResult);
        String decisionValidateur = validationResult != null
                ? safe(validationResult.getConclusion())
                : "INCONNU";
        String estimationGuidance = getQualitativeEstimationGuidance(typeDetecte);
        String description        = truncate(safe(claim.getDescription()), MAX_DESCRIPTION_CHARS);
        String incidentDate       = claim.getIncidentDate() != null
                ? claim.getIncidentDate().toString()
                : "Inconnue";
        String memorySection      = (learningExamples == null || learningExamples.isBlank())
                ? "Aucun exemple historique disponible."
                : learningExamples;

        return """
                Tu es un expert en estimation de dommages pour assurance en Tunisie.

                MISSION :
                Analyse l'image fournie et propose une estimation financière RÉALISTE et PRÉCISE
                en dinars tunisiens (DT), strictement basée sur les dommages visibles.

                ═══════════════════════════════════════════════════════════
                RÈGLES D'ESTIMATION PAR NIVEAU DE GRAVITÉ (OBLIGATOIRES)
                ═══════════════════════════════════════════════════════════

                LEGER (dommage esthétique localisé) :
                  → Fourchette réaliste : 100 – 800 DT
                  → Exemples : rayure surface, petite bosse sans déformation, fissure plastique légère
                  → NE jamais dépasser 1 200 DT pour un dommage purement esthétique

                MODERE (pièce(s) à réparer ou remplacer) :
                  → Fourchette réaliste : 800 – 4 000 DT
                  → Exemples : pare-chocs à remplacer, porte enfoncée, phare cassé, dégât localisé

                GRAVE (élément structurel, mécanique, sécurité) :
                  → Fourchette réaliste : 4 000 – 20 000 DT
                  → Exemples : choc frontal sévère, airbag déclenché, fuite radiateur, déformation châssis

                TOTAL_POTENTIEL (irréparable ou perte totale probable) :
                  → Fourchette réaliste : > 15 000 DT
                  → needsHumanReview = true obligatoire

                ═══════════════════════════════════════════════════════════
                RÈGLES ABSOLUES
                ═══════════════════════════════════════════════════════════
                - Estime UNIQUEMENT ce qui est VISIBLE dans l'image.
                - Une rayure sur un pare-chocs = 150-400 DT, PAS 5 000 DT.
                - Un petit choc plastique sans déformation = LEGER, PAS GRAVE.
                - Ne surclasse JAMAIS un dommage sans preuve visuelle suffisante.
                - Ne sous-estime JAMAIS : airbag déclenché, fuite de liquide, déformation châssis.
                - Si l'image est floue ou insuffisante → confidence < %.2f et needsHumanReview = true.

                ═══════════════════════════════════════════════════════════
                CONTEXTE DU DOSSIER
                ═══════════════════════════════════════════════════════════
                - Type de sinistre    : %s
                - Décision validateur : %s
                - Description client  : %s
                - Date incident       : %s

                GUIDE QUALITATIF :
                %s

                EXEMPLES HISTORIQUES :
                %s

                ═══════════════════════════════════════════════════════════
                PROCESSUS D'ANALYSE OBLIGATOIRE (raisonne mentalement, JSON en sortie)
                ═══════════════════════════════════════════════════════════
                Étape 1 — Inventaire : liste TOUS les éléments endommagés visibles dans l'image.
                Étape 2 — Gravité    : évalue la gravité réelle de chaque élément (LEGER/MODERE/GRAVE).
                Étape 3 — Coût       : estime le coût unitaire de réparation/remplacement en DT
                                        selon les fourchettes ci-dessus.
                Étape 4 — Synthèse   : additionne pour obtenir estimationMin, estimationMoyenne, estimationMax.
                Étape 5 — JSON       : produis uniquement le JSON final.

                ═══════════════════════════════════════════════════════════
                FORMAT DE SORTIE OBLIGATOIRE
                ═══════════════════════════════════════════════════════════
                Réponds UNIQUEMENT avec un objet JSON valide.
                Aucun markdown. Aucun texte avant ou après le JSON.

                {
                  "elementsEndommages": "liste précise et courte des éléments visibles",
                  "estimationMin": <nombre entier en DT, coût minimal réaliste>,
                  "estimationMax": <nombre entier en DT, coût maximal réaliste>,
                  "estimationMoyenne": <nombre entier en DT, coût le plus probable>,
                  "confidence": <nombre entre 0.0 et 1.0>,
                  "severity": "LEGER|MODERE|GRAVE|TOTAL_POTENTIEL",
                  "damageIndicators": "signes visuels clés observés dans l'image",
                  "analyse": "explication courte et factuelle des dommages visibles et du raisonnement de coût",
                  "needsHumanReview": <true|false>
                }

                Analyse l'image maintenant et retourne uniquement le JSON.
                """.formatted(
                humanReviewThreshold,
                typeDetecte,
                decisionValidateur,
                description,
                incidentDate,
                estimationGuidance,
                memorySection
        );
    }

    // =========================================================================
    // Guide qualitatif par type
    // =========================================================================

    private String getQualitativeEstimationGuidance(String type) {
        if (type == null) {
            return """
                    Évalue la nature du dommage, son étendue, les éléments touchés,
                    la complexité de réparation, la main-d'œuvre nécessaire et le niveau d'incertitude.
                    """;
        }

        String normalized = type.toUpperCase(Locale.ROOT);

        if (normalized.contains("AUTO")) {
            return """
                    Pour un sinistre AUTO, inspecte obligatoirement dans l'image :
                    pare-chocs, aile, porte, optique/phare, capot, pare-brise, peinture, roue,
                    suspension, radiateur, fuite de liquide, airbag, traverse, châssis, structure.

                    Règles de coût AUTO en Tunisie (fourchettes réalistes) :
                    - Rayure légère sur carrosserie           : 100 – 300 DT
                    - Petite bosse sans déformation notable   : 200 – 500 DT
                    - Remplacement pare-chocs plastique        : 400 – 900 DT
                    - Remplacement phare/optique               : 300 – 800 DT
                    - Remplacement porte                       : 800 – 2 500 DT
                    - Capot légèrement déformé                 : 600 – 1 500 DT
                    - Capot fortement déformé ou à remplacer   : 1 500 – 4 000 DT
                    - Choc frontal modéré (pare-chocs + phare) : 1 500 – 4 000 DT
                    - Choc frontal grave (structure touchée)   : 8 000 – 20 000 DT
                    - Airbag déclenché                         : + 3 000 – 8 000 DT
                    - Fuite radiateur                          : 500 – 2 000 DT

                    Attention :
                    - Un pare-chocs arraché n'est pas une rayure.
                    - Un capot fortement plié indique un choc grave.
                    - Un airbag déclenché = sinistre GRAVE minimum.
                    - Une fuite après choc = risque mécanique ou radiateur.
                    - Si la structure/mécanique est potentiellement touchée → needsHumanReview = true.

                    NE CLASSE JAMAIS un choc frontal sévère comme simple dommage esthétique.
                    NE SURCLASSE JAMAIS une simple rayure ou petit choc plastique comme GRAVE.
                    """;
        }

        if (normalized.contains("HABITATION")) {
            return """
                    Pour un sinistre HABITATION, observe la zone réellement touchée :
                    fuite localisée, eau au sol, parquet ou carrelage touché, meuble abîmé,
                    mur ou plafond humide, moisissure, étendue sur une ou plusieurs pièces.

                    Règles de coût HABITATION (fourchettes réalistes) :
                    - Fuite localisée sous évier, petit dégât d'eau  : 200 – 800 DT
                    - Parquet ou carrelage endommagé (petite surface) : 300 – 1 500 DT
                    - Dommage sur 1 pièce modérément touchée          : 1 000 – 5 000 DT
                    - Dommage sur plusieurs pièces                    : 3 000 – 15 000 DT
                    - Structure, plafond porteur, murs importants     : 10 000 – 100 000 DT

                    Un dégât localisé sous évier NE doit PAS être traité comme un dommage lourd.
                    """;
        }

        if (normalized.contains("SANTE")) {
            return """
                    Pour un sinistre SANTE, analyse le document médical visible :
                    nature du soin, frais indiqués, actes réalisés, niveau de complexité.
                    Si l'image n'est pas un document exploitable → confidence faible.
                    """;
        }

        if (normalized.contains("VOYAGE")) {
            return """
                    Pour un sinistre VOYAGE, observe le type de justificatif :
                    bagage endommagé, document de retard, annulation, facture.
                    L'estimation dépend du justificatif visible et de sa fiabilité.
                    """;
        }

        if (normalized.contains("VIE")) {
            return """
                    Pour un sinistre VIE, l'image seule est rarement suffisante.
                    Cherche un document : décès, invalidité, incapacité, certificat, pièce administrative.
                    Si le document est incomplet ou illisible → confidence faible et needsHumanReview = true.
                    """;
        }

        return """
                Évalue la nature du dommage, son étendue, les éléments touchés,
                la complexité de réparation, la main-d'œuvre nécessaire et le niveau d'incertitude.
                """;
    }

    // =========================================================================
    // Parsing de la réponse
    // =========================================================================

    private AgentResult parseResponse(
            String raw,
            Claim claim,
            String typeDetecte,
            double humanReviewThreshold
    ) {
        if (raw == null || raw.isBlank()) {
            log.warn("Réponse vide du modèle vision claim #{}", claim.getId());
            return buildResult(claim, 0.0, 0.0, 0.0, 0.0,
                    "Réponse vide du modèle de vision", true, raw);
        }

        try {
            String   jsonOnly        = extractJson(raw);
            JsonNode node            = objectMapper.readTree(jsonOnly);

            double estimationMin     = normalizeMoney(findDouble(node, MIN_KEYS, 0.0));
            double estimationMax     = normalizeMoney(findDouble(node, MAX_KEYS, 0.0));
            double estimationMoyenne = normalizeMoney(findDouble(node, MOY_KEYS, 0.0));
            double confidence        = normalizeConfidence(findDouble(node, CONF_KEYS, DEFAULT_CONFIDENCE));
            String analyse           = findText(node, ANALYSE_KEYS, "");
            String severity          = findText(node, SEVERITY_KEYS, "");
            String damageIndicators  = findText(node, INDICATOR_KEYS, "");

            log.info(
                    "JSON parsé claim #{} : min={} moy={} max={} conf={} severity={} indicators='{}' analyse='{}'",
                    claim.getId(), estimationMin, estimationMoyenne, estimationMax,
                    confidence, severity, damageIndicators, analyse
            );

            if (estimationMin == 0.0 && estimationMax == 0.0) {
                log.warn("JSON valide mais estimations = 0.0 claim #{} -> fallback regex", claim.getId());
                return fallbackFromText(raw, claim, typeDetecte, humanReviewThreshold);
            }

            return applyBusinessRules(
                    claim, typeDetecte, humanReviewThreshold,
                    estimationMin, estimationMax, estimationMoyenne,
                    confidence, analyse, severity, damageIndicators,
                    false, raw
            );

        } catch (Exception e) {
            log.warn("JSON absent claim #{} — fallback regex : {}", claim.getId(), e.getMessage());
        }

        return fallbackFromText(raw, claim, typeDetecte, humanReviewThreshold);
    }

    // =========================================================================
    // Règles métier — VERSION CORRIGÉE
    // =========================================================================

    /**
     * CORRECTION PRINCIPALE :
     *  1. On ne surcharge JAMAIS l'estimation si la gravité est LEGER ou MODERE.
     *  2. isSevereAutoDamage ne s'applique que si severityIsLight = false.
     *  3. isSuspiciousLowAutoEstimate a un seuil abaissé à 1500 DT et requiert 4 zones (pas 3).
     *  4. Les montants forcés restent cohérents avec les fourchettes du prompt.
     */
    private AgentResult applyBusinessRules(
            Claim claim,
            String typeDetecte,
            double humanReviewThreshold,
            double estimationMin,
            double estimationMax,
            double estimationMoyenne,
            double confidence,
            String analyse,
            String severity,
            String damageIndicators,
            boolean needsHuman,
            String raw
    ) {
        // ── Cohérence min / max / moyenne ────────────────────────────────────
        if (estimationMin > estimationMax) {
            double tmp = estimationMin;
            estimationMin = estimationMax;
            estimationMax = tmp;
        }

        if (estimationMoyenne < estimationMin || estimationMoyenne > estimationMax) {
            estimationMoyenne = (estimationMin + estimationMax) / 2.0;
        }

        // ── Drapeaux de gravité ───────────────────────────────────────────────
        boolean severityIsLight  = isLightSeverity(severity);
        boolean severityIsHeavy  = isHeavySeverity(severity);

        String safetyText = safe(analyse)
                + " " + safe(severity)
                + " " + safe(damageIndicators)
                + " " + safe(raw);

        // ── GARDE-FOU ABSOLU : vérification indépendante du severity LLM ─────
        // Le LLM peut se tromper sur la gravité (MODERE au lieu de GRAVE).
        // On inspecte le texte brut pour détecter des signaux graves indépendamment
        // de ce que le LLM a mis dans le champ "severity".
        boolean textIndicatesGrave = isSevereAutoDamage(typeDetecte, safetyText);
        boolean estimationCoherent = isEstimationCoherentWithSeverity(severity, estimationMax, typeDetecte);

        if (textIndicatesGrave && !estimationCoherent) {
            // Le LLM a sous-estimé : on corrige l'estimation ET on force la review
            log.warn(
                    "INCOHÉRENCE DÉTECTÉE claim #{} : severity='{}' estimationMax={} "
                            + "mais texte indique un dommage grave -> correction forcée",
                    claim.getId(), severity, estimationMax
            );
            estimationMin     = Math.max(estimationMin, 8_000.0);
            estimationMoyenne = Math.max(estimationMoyenne, 14_000.0);
            estimationMax     = Math.max(estimationMax, 20_000.0);
            needsHuman        = true;
            severityIsLight   = false;
            analyse           = appendRuleNote(
                    analyse,
                    "Garde-fou : incohérence détectée entre severity LLM et dommages visibles dans le texte. Estimation corrigée."
            );
        }

        // ── Règle dommage AUTO grave ──────────────────────────────────────────
        // Ne s'applique QUE si le LLM n'a pas classé LEGER ou MODERE
        if (!severityIsLight && isSevereAutoDamage(typeDetecte, safetyText)) {
            if (estimationMax < 8_000.0) {
                estimationMin     = Math.max(estimationMin, 8_000.0);
                estimationMoyenne = Math.max(estimationMoyenne, 14_000.0);
                estimationMax     = Math.max(estimationMax, 20_000.0);
                needsHuman        = true;
                analyse           = appendRuleNote(
                        analyse,
                        "Règle sécurité auto : dommage grave détecté, validation expert requise."
                );
            } else {
                // Estimation déjà haute → on force juste la review humaine
                needsHuman = true;
            }
        }

        // ── Règle estimation suspecte trop basse ─────────────────────────────
        // Ne s'applique QUE si le LLM n'a pas classé LEGER (un dommage léger
        // peut légitimement valoir moins de 1 500 DT)
        else if (!severityIsLight
                && isSuspiciousLowAutoEstimate(typeDetecte, safetyText, estimationMax)) {
            estimationMin     = Math.max(estimationMin, 2_000.0);
            estimationMoyenne = Math.max(estimationMoyenne, 4_000.0);
            estimationMax     = Math.max(estimationMax, 7_000.0);
            needsHuman        = true;
            analyse           = appendRuleNote(
                    analyse,
                    "Règle sécurité auto : estimation basse pour plusieurs éléments visibles."
            );
        }

        // ── Gravité lourde déclarée par le LLM ───────────────────────────────
        if (severityIsHeavy) {
            needsHuman = true;
        }

        // ── Plafond interne par type ──────────────────────────────────────────
        double maxAllowed = getMaxAllowedByType(typeDetecte);

        if (estimationMax > maxAllowed) {
            log.warn(
                    "Estimation {} DT > plafond interne {} DT pour type={}",
                    estimationMax, maxAllowed, typeDetecte
            );
            estimationMax     = maxAllowed;
            estimationMoyenne = Math.min(estimationMoyenne, maxAllowed);
            estimationMin     = Math.min(estimationMin, maxAllowed);
            needsHuman        = true;
            analyse           = appendRuleNote(analyse, "Plafond interne appliqué.");
        }

        // ── Confidence faible → review humaine ───────────────────────────────
        if (confidence < humanReviewThreshold) {
            needsHuman = true;
        }

        // ── Analyse manquante ─────────────────────────────────────────────────
        if (analyse == null || analyse.isBlank()) {
            analyse    = "Analyse non fournie";
            needsHuman = true;
        }

        log.info(
                "Estimation finale claim #{} | min={} moy={} max={} conf={} severity={} seuil={} review={}",
                claim.getId(), estimationMin, estimationMoyenne, estimationMax,
                confidence, severity, humanReviewThreshold, needsHuman
        );

        return buildResult(
                claim,
                estimationMin, estimationMax, estimationMoyenne,
                confidence, analyse, needsHuman, raw
        );
    }

    // =========================================================================
    // Méthodes de détection — VERSIONS CORRIGÉES
    // =========================================================================

    /**
     * Détecte un dommage AUTO clairement grave d'après le texte brut
     * (analyse + severity + indicators + réponse brute LLM).
     * Fonctionne en français ET en anglais car certains LLM répondent en anglais.
     */
    private boolean isSevereAutoDamage(String typeDetecte, String text) {
        if (!normalizeForRules(typeDetecte).contains("auto")) {
            return false;
        }

        String normalized = normalizeForRules(text);

        // Signaux explicites de gravité élevée (FR + EN)
        if (containsAnyNormalized(normalized,
                // FR
                "grave",
                "total potentiel", "total_potentiel",
                "perte totale",
                "economiquement irreparable",
                "choc frontal severe", "choc frontal sévère",
                "avant detruit", "avant détruit",
                "vehicule detruit", "véhicule détruit",
                "pare-chocs arrache", "pare-choc arrache",
                "pare-chocs arraché", "pare-choc arraché",
                "capot fortement deforme", "capot fortement déformé",
                "capot arrache", "capot arraché",
                "airbag",
                "fuite de liquide", "fuite liquide",
                "radiateur endommage", "radiateur endommagé", "radiateur ecrase",
                "chassis deforme", "châssis déformé",
                "structure",
                "traverse",
                "suspension cassee", "suspension cassée",
                "moteur touche", "moteur touché", "moteur expose", "moteur exposé",
                "mecanique expose", "mécanique exposée",
                // EN
                "total loss", "write-off", "write off",
                "front end destroyed", "front destroyed",
                "severe damage", "major damage", "heavy damage",
                "engine exposed", "engine visible",
                "fluid leak", "coolant leak", "oil leak",
                "hood torn", "hood ripped", "bumper torn",
                "structural damage", "frame damage",
                "deployed airbag", "airbag deployed",
                "radiator damage", "radiator crushed"
        )) {
            return true;
        }

        // CORRECTION : signaux mécaniques/structurels cumulés (seuil = 2)
        int severeSignals = countAnyNormalized(normalized,
                "airbag",
                "fuite", "leak",
                "radiateur", "radiator",
                "chassis", "châssis", "frame",
                "structure", "structural",
                "suspension",
                "traverse",
                "moteur", "engine",
                "mecanique expose", "mécanique exposée", "engine exposed",
                "arrache", "arraché", "torn", "ripped",
                "detruit", "détruit", "destroyed"
        );

        return severeSignals >= 2;
    }

    /**
     * Détecte une estimation AUTO suspecte trop basse compte tenu des éléments touchés.
     * CORRECTION : seuil abaissé à 1 500 DT (était 3 000) et 4 zones requises (était 3).
     */
    private boolean isSuspiciousLowAutoEstimate(
            String typeDetecte,
            String text,
            double estimationMax
    ) {
        if (!normalizeForRules(typeDetecte).contains("auto")) {
            return false;
        }

        // CORRECTION : un dommage à 1 500 DT peut être parfaitement légitime
        if (estimationMax > 1_500.0) {
            return false;
        }

        String normalized   = normalizeForRules(text);

        // CORRECTION : 4 zones endommagées requises au lieu de 3
        int damagedZones = countAnyNormalized(normalized,
                "avant", "arriere", "arrière",
                "pare-choc", "pare-chocs",
                "aile", "porte", "phare", "optique",
                "capot", "roue", "suspension", "radiateur"
        );

        return damagedZones >= 4;
    }

    /**
     * Vérifie si la gravité déclarée par le LLM est UNIQUEMENT LEGER.
     * MODERE n'est PAS considéré comme léger : les règles de sécurité s'appliquent quand même
     * si l'estimation est incohérente avec les dommages visibles détectés dans le texte.
     */
    private boolean isLightSeverity(String severity) {
        String normalized = normalizeForRules(severity);
        return normalized.contains("leger")
                || normalized.contains("léger")
                || normalized.contains("light")
                || normalized.contains("minor");
        // NOTE : MODERE n'est volontairement PAS inclus ici.
        // Un dommage MODERE avec une estimation trop basse doit quand même
        // passer par les règles de sécurité isSevereAutoDamage / isSuspiciousLow.
    }

    /**
     * Vérifie si la gravité est GRAVE ou TOTAL_POTENTIEL.
     */
    private boolean isHeavySeverity(String severity) {
        String normalized = normalizeForRules(severity);
        return normalized.contains("grave")
                || normalized.contains("total")
                || normalized.contains("severe")
                || normalized.contains("sévère");
    }

    /**
     * Vérifie si l'estimation retournée par le LLM est cohérente avec la gravité déclarée.
     * Si severity = MODERE mais estimationMax < 800 DT pour un sinistre AUTO → incohérent.
     * Si severity = GRAVE mais estimationMax < 4 000 DT → incohérent.
     */
    private boolean isEstimationCoherentWithSeverity(
            String severity,
            double estimationMax,
            String typeDetecte
    ) {
        if (!normalizeForRules(typeDetecte).contains("auto")) {
            // Pour les autres types, on fait confiance au LLM
            return true;
        }

        String normalized = normalizeForRules(severity);

        if (normalized.contains("grave") || normalized.contains("total")) {
            // GRAVE doit valoir au moins 4 000 DT
            return estimationMax >= 4_000.0;
        }

        if (normalized.contains("modere") || normalized.contains("modéré")) {
            // MODERE doit valoir au moins 800 DT
            return estimationMax >= 800.0;
        }

        if (normalized.contains("leger") || normalized.contains("léger")) {
            // LEGER peut aller jusqu'à 1 200 DT, acceptable
            return estimationMax <= 1_500.0;
        }

        return true;
    }

    // =========================================================================
    // Fallback regex (inchangé)
    // =========================================================================

    private AgentResult fallbackFromText(
            String raw,
            Claim claim,
            String typeDetecte,
            double humanReviewThreshold
    ) {
        log.info("Fallback regex étendu pour claim #{}", claim.getId());

        double  min     = 0.0;
        double  max     = 0.0;
        double  moyenne = 0.0;
        boolean found   = false;

        Matcher m1 = RANGE_AMOUNT_PATTERN.matcher(raw);
        if (m1.find()) {
            min     = parseAmount(m1.group(1));
            max     = parseAmount(m1.group(2));
            moyenne = (min + max) / 2.0;
            found   = true;
        }

        if (!found) {
            Matcher      m2      = DINAR_PATTERN.matcher(raw);
            List<Double> amounts = new ArrayList<>();
            while (m2.find()) {
                double value = parseAmount(m2.group(1));
                if (value >= 50) amounts.add(value);
            }
            if (!amounts.isEmpty()) {
                min     = Collections.min(amounts);
                max     = Collections.max(amounts);
                moyenne = amounts.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
                found   = true;
            }
        }

        if (!found) {
            Matcher m3 = NUMBER_RANGE_PATTERN.matcher(raw);
            if (m3.find()) {
                double v1 = parseAmount(m3.group(1));
                double v2 = parseAmount(m3.group(2));
                if (v1 >= 50 && v2 >= 50) {
                    min     = Math.min(v1, v2);
                    max     = Math.max(v1, v2);
                    moyenne = (min + max) / 2.0;
                    found   = true;
                }
            }
        }

        if (!found) {
            Matcher      m4      = STANDALONE_NUMBER_PATTERN.matcher(raw);
            List<Double> amounts = new ArrayList<>();
            while (m4.find()) {
                double value = parseAmount(m4.group(1));
                if (value >= 50) amounts.add(value);
            }
            if (!amounts.isEmpty()) {
                moyenne = amounts.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
                min     = moyenne * 0.8;
                max     = moyenne * 1.2;
                found   = true;
            }
        }

        if (!found) {
            Matcher      m5      = SINGLE_AMOUNT_PATTERN.matcher(raw);
            List<Double> amounts = new ArrayList<>();
            while (m5.find()) {
                double value = parseAmount(m5.group(1));
                if (value >= 100) amounts.add(value);
            }
            if (!amounts.isEmpty()) {
                min     = Collections.min(amounts);
                max     = Collections.max(amounts);
                moyenne = amounts.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
                found   = true;
            }
        }

        String  analyse    = extractAnalyseFromText(raw);
        double  confidence = found ? 0.45 : 0.10;
        boolean needsHuman = true;

        if (!found) {
            log.warn("Fallback échec claim #{} — aucun montant extrait", claim.getId());
        }

        return applyBusinessRules(
                claim, typeDetecte, humanReviewThreshold,
                min, max, moyenne, confidence,
                analyse, "", "", needsHuman, raw
        );
    }

    // =========================================================================
    // Utilitaires JSON
    // =========================================================================

    private double findDouble(JsonNode node, Set<String> keys, double defaultValue) {
        for (String key : keys) {
            JsonNode n = node.path(key);
            if (!n.isMissingNode() && !n.isNull()) {
                if (n.isNumber()) return n.asDouble(defaultValue);
                String text = nodeText(n);
                if (!text.isBlank()) {
                    double parsed = parseAmountLoose(text);
                    if (parsed >= 0.0) return parsed;
                }
            }
        }
        return defaultValue;
    }

    private String findText(JsonNode node, Set<String> keys, String defaultValue) {
        for (String key : keys) {
            JsonNode n = node.path(key);
            if (!n.isMissingNode() && !n.isNull()) {
                String value = nodeText(n);
                if (!value.isBlank()) return value;
            }
        }
        return defaultValue;
    }

    private String nodeText(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) return "";
        if (node.isArray()) {
            StringBuilder sb = new StringBuilder();
            for (JsonNode item : node) {
                if (item != null && !item.isNull()) {
                    if (!sb.isEmpty()) sb.append(", ");
                    sb.append(item.asText(""));
                }
            }
            return sb.toString().trim();
        }
        if (node.isObject()) return node.toString();
        return node.asText("").trim();
    }

    private double parseAmountLoose(String raw) {
        if (raw == null || raw.isBlank()) return -1.0;
        try {
            String cleaned = raw
                    .replace("DT", "").replace("TND", "")
                    .replace("dinars", "").replace("dinar", "")
                    .replace(" ", "").replace(",", ".").trim();
            return Double.parseDouble(cleaned);
        } catch (Exception e) {
            return -1.0;
        }
    }

    private String extractJson(String raw) {
        String clean = raw
                .replaceAll("(?s)<think>.*?</think>", "")
                .replaceAll("```json", "")
                .replaceAll("```", "")
                .trim();
        int start = clean.indexOf('{');
        int end   = clean.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new IllegalArgumentException("Aucun JSON trouvé");
        }
        return clean.substring(start, end + 1).trim();
    }

    // =========================================================================
    // Gestion des images
    // =========================================================================

    private List<ClaimDocument> extractValidImages(Claim claim) {
        if (claim.getDocuments() == null || claim.getDocuments().isEmpty()) {
            return List.of();
        }
        return claim.getDocuments().stream()
                .filter(doc -> doc.getFileType() != null)
                .filter(doc -> SUPPORTED_IMAGE_TYPES.contains(
                        doc.getFileType().toLowerCase(Locale.ROOT)))
                .filter(doc -> doc.getFilePath() != null && !doc.getFilePath().isBlank())
                .limit(MAX_IMAGES)
                .toList();
    }

    private List<EncodedImage> loadAndEncodeImages(List<ClaimDocument> docs) {
        long             startedAt = System.nanoTime();
        List<EncodedImage> result  = new ArrayList<>();

        for (ClaimDocument doc : docs) {
            long imageStartedAt = System.nanoTime();
            try {
                String filePath = doc.getFilePath();
                String cached   = imageBase64Cache.get(filePath);

                if (cached != null) {
                    result.add(new EncodedImage(cached, "image/jpeg"));
                    continue;
                }

                Path path = Path.of(filePath);
                if (!Files.exists(path)) {
                    log.warn("Image introuvable : {}", filePath);
                    continue;
                }

                byte[] rawBytes = Files.readAllBytes(path);
                String base64   = resizeAndEncodeJpeg(rawBytes);

                imageBase64Cache.put(filePath, base64);
                result.add(new EncodedImage(base64, "image/jpeg"));

                log.info("{} - image {} chargée en {} ms",
                        AGENT_NAME, doc.getFileName(), elapsedMs(imageStartedAt));

            } catch (Exception e) {
                log.warn("Impossible de charger {} : {}", doc.getFilePath(), e.getMessage());
            }
        }

        log.info("{} - {} image(s) encodée(s) en {} ms",
                AGENT_NAME, result.size(), elapsedMs(startedAt));
        return result;
    }

    private String resizeAndEncodeJpeg(byte[] rawBytes) throws IOException {
        BufferedImage original = ImageIO.read(new ByteArrayInputStream(rawBytes));
        if (original == null) {
            return Base64.getEncoder().encodeToString(rawBytes);
        }

        int width  = original.getWidth();
        int height = original.getHeight();

        if (width > IMAGE_MAX_DIMENSION || height > IMAGE_MAX_DIMENSION) {
            double ratio    = Math.min(
                    (double) IMAGE_MAX_DIMENSION / width,
                    (double) IMAGE_MAX_DIMENSION / height
            );
            int newWidth  = Math.max(1, (int) (width  * ratio));
            int newHeight = Math.max(1, (int) (height * ratio));

            BufferedImage resized   = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB);
            Graphics2D    graphics  = resized.createGraphics();
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING,
                    RenderingHints.VALUE_RENDER_QUALITY);
            graphics.drawImage(original, 0, 0, newWidth, newHeight, null);
            graphics.dispose();
            original = resized;
        }

        ByteArrayOutputStream baos    = new ByteArrayOutputStream();
        Iterator<ImageWriter>  writers = ImageIO.getImageWritersByFormatName("jpeg");

        if (!writers.hasNext()) {
            ImageIO.write(original, "png", baos);
        } else {
            ImageWriter    writer = writers.next();
            ImageWriteParam param  = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(IMAGE_JPEG_QUALITY);
            try (ImageOutputStream ios = ImageIO.createImageOutputStream(baos)) {
                writer.setOutput(ios);
                writer.write(null, new IIOImage(original, null, null), param);
            } finally {
                writer.dispose();
            }
        }

        return Base64.getEncoder().encodeToString(baos.toByteArray());
    }

    private List<Content> buildVisionContents(String prompt, List<EncodedImage> encodedImages) {
        List<Content> contents = new ArrayList<>(1 + encodedImages.size());
        contents.add(TextContent.from(prompt));
        for (EncodedImage img : encodedImages) {
            contents.add(ImageContent.from(
                    Image.builder()
                            .base64Data(img.base64())
                            .mimeType(img.mimeType())
                            .build()
            ));
        }
        return contents;
    }

    private String callVisionModelSafely(UserMessage userMessage, Long claimId) {
        long startedAt = System.nanoTime();
        try {
            Response<AiMessage> response = visionModel.generate(userMessage);
            String raw = (response != null && response.content() != null)
                    ? response.content().text()
                    : "";
            raw = (raw == null) ? "" : raw.trim();
            log.info("{} - réponse vision claim #{} en {} ms | {} chars",
                    AGENT_NAME, claimId, elapsedMs(startedAt), raw.length());
            return raw;
        } catch (Exception e) {
            log.error("Erreur appel modèle vision claim #{}", claimId, e);
            return "";
        }
    }

    // =========================================================================
    // Construction des résultats
    // =========================================================================

    private AgentResult finalizeResult(
            long startedAt, String mode, Claim claim,
            double min, double max, double moyenne,
            double confidence, String analyse,
            boolean needsHuman, String rawResponse
    ) {
        AgentResult result = buildResult(claim, min, max, moyenne, confidence,
                analyse, needsHuman, rawResponse);
        log.info(
                "{} - claim #{} terminé en {} ms | mode={} | conclusion={} | confidence={} | humanReview={}",
                AGENT_NAME, claim.getId(), elapsedMs(startedAt), mode,
                result.getConclusion(), result.getConfidenceScore(), result.isNeedsHumanReview()
        );
        return result;
    }

    private AgentResult buildResult(
            Claim claim,
            double estimationMin,
            double estimationMax,
            double estimationMoyenne,
            double confidence,
            String analyse,
            boolean needsHuman,
            String rawResponse
    ) {
        AgentResult result = new AgentResult();
        result.setAgentName(AGENT_NAME);
        result.setClaim(claim);
        result.setConclusion(buildConclusion(estimationMin, estimationMax, estimationMoyenne));
        result.setConfidenceScore(confidence);
        result.setRawLlmResponse(rawResponse != null ? rawResponse : analyse);
        result.setNeedsHumanReview(needsHuman);
        result.setCreatedAt(LocalDateTime.now());
        return result;
    }

    private String buildConclusion(double min, double max, double moyenne) {
        return "Estimation min: %.2f DT | moyenne: %.2f DT | max: %.2f DT"
                .formatted(min, moyenne, max);
    }

    // =========================================================================
    // Utilitaires divers
    // =========================================================================

    private double getHumanReviewThreshold() {
        long now = System.currentTimeMillis();
        if (!Double.isNaN(cachedThreshold) && now < thresholdCacheExpiresAt) {
            return cachedThreshold;
        }
        synchronized (this) {
            now = System.currentTimeMillis();
            if (!Double.isNaN(cachedThreshold) && now < thresholdCacheExpiresAt) {
                return cachedThreshold;
            }
            double threshold       = aiAgentConfigService.getThreshold(CONFIG_KEY);
            cachedThreshold        = threshold;
            thresholdCacheExpiresAt = now + THRESHOLD_CACHE_TTL_MS;
            return threshold;
        }
    }

    private double getMaxAllowedByType(String type) {
        if (type == null) return 50_000.0;
        String normalized = type.toUpperCase(Locale.ROOT);
        if (normalized.contains("AUTO"))       return 25_000.0;
        if (normalized.contains("SANTE"))      return 50_000.0;
        if (normalized.contains("HABITATION")) return 100_000.0;
        if (normalized.contains("VOYAGE"))     return 50_000.0;
        if (normalized.contains("VIE"))        return 50_000.0;
        return 50_000.0;
    }

    private String extractClaimType(AgentResult routeResult) {
        if (routeResult == null || routeResult.getConclusion() == null) return "INCONNU";
        String upper = routeResult.getConclusion().trim().toUpperCase(Locale.ROOT);
        if (upper.contains("AUTO"))       return "AUTO";
        if (upper.contains("HABITATION")) return "HABITATION";
        if (upper.contains("SANTE"))      return "SANTE";
        if (upper.contains("VOYAGE"))     return "VOYAGE";
        if (upper.contains("VIE"))        return "VIE";
        return routeResult.getConclusion().trim();
    }

    private String appendRuleNote(String analyse, String note) {
        String base = safe(analyse);
        if (base.isBlank())        return "[" + note + "]";
        if (base.contains(note))   return base;
        return base + " [" + note + "]";
    }

    private double normalizeMoney(double value) {
        return Math.max(0.0, value);
    }

    private double normalizeConfidence(double confidence) {
        if (confidence > 1.0 && confidence <= 100.0) confidence /= 100.0;
        return Math.max(0.0, Math.min(1.0, confidence));
    }

    private double parseAmount(String raw) {
        try {
            return Double.parseDouble(raw.replace(",", ".").trim());
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private String extractAnalyseFromText(String raw) {
        if (raw == null || raw.isBlank()) return "Analyse extraite depuis réponse texte.";
        String   clean     = raw.replaceAll("\\s+", " ").trim();
        String[] sentences = clean.split("(?<=[.!?])\\s+");
        StringBuilder sb   = new StringBuilder();
        for (int i = 0; i < Math.min(2, sentences.length); i++) {
            sb.append(sentences[i]).append(" ");
        }
        String result = sb.toString().trim();
        return result.length() > 300 ? result.substring(0, 300) : result;
    }

    private String normalizeForRules(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .trim()
                .replaceAll("\\s+", " ");
    }

    private boolean containsAnyNormalized(String text, String... keywords) {
        if (text == null || text.isBlank()) return false;
        String normalizedText = normalizeForRules(text);
        for (String keyword : keywords) {
            if (normalizedText.contains(normalizeForRules(keyword))) return true;
        }
        return false;
    }

    private int countAnyNormalized(String text, String... keywords) {
        if (text == null || text.isBlank()) return 0;
        String normalizedText = normalizeForRules(text);
        int count = 0;
        for (String keyword : keywords) {
            if (normalizedText.contains(normalizeForRules(keyword))) count++;
        }
        return count;
    }

    private String truncate(String text, int max) {
        String value = safe(text);
        return value.length() <= max ? value : value.substring(0, max);
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private long elapsedMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }

    // =========================================================================
    // Record interne
    // =========================================================================

    private record EncodedImage(String base64, String mimeType) {}
}