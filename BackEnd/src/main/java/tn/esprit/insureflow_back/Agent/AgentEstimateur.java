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
import java.awt.Color;
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
import java.util.Comparator;
import java.util.HashSet;
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
    private static final String CONFIG_KEY = "AGENT_ESTIMATEUR";

    private static final double DEFAULT_CONFIDENCE = 0.50;

    private static final int MAX_IMAGES = 1;
    private static final int IMAGE_MAX_DIMENSION = 640;
    private static final float IMAGE_JPEG_QUALITY = 0.75f;

    private static final int MAX_LEARNING_CHARS = 5000;
    private static final int MAX_DESCRIPTION_CHARS = 500;

    private static final double MIN_SIMILARITY_SCORE = 0.08;

    private static final long THRESHOLD_CACHE_TTL_MS = 60_000L;

    private volatile double cachedThreshold = Double.NaN;
    private volatile long thresholdCacheExpiresAt = 0L;

    private static final Set<String> SUPPORTED_IMAGE_TYPES = Set.of(
            "image/jpeg",
            "image/jpg",
            "image/png",
            "image/webp"
    );

    private final Map<String, String> imageBase64Cache = new ConcurrentHashMap<>();

    private static final Pattern RANGE_AMOUNT_PATTERN = Pattern.compile(
            "(\\d{1,6}(?:[.,]\\d{1,2})?)\\s*(?:à|a|-|et)\\s*(\\d{1,6}(?:[.,]\\d{1,2})?)\\s*(?:DT|TND|dinars?)",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern SINGLE_AMOUNT_PATTERN = Pattern.compile(
            "(\\d{2,6}(?:[.,]\\d{1,2})?)\\s*(?:DT|TND|dinars?|dt|tnd)",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern DINAR_PATTERN = Pattern.compile(
            "(\\d{2,6}(?:[.,]\\d{1,2})?)\\s*(?:dinars?|TND|tnd|DT|dt)",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern NUMBER_RANGE_PATTERN = Pattern.compile(
            "(\\d{2,6})\\s*(?:à|a|et|-|ou)\\s*(\\d{2,6})",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern STANDALONE_NUMBER_PATTERN = Pattern.compile(
            "(?:environ|estimé?[eés]?|coût|montant|réparation|frais)[^\\d]*(\\d{2,6})",
            Pattern.CASE_INSENSITIVE
    );

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
            "analyse", "analysis", "description", "commentaire",
            "comment", "explication", "reasoning"
    );

    private static final Set<String> IMAGE_ANALYSIS_KEYS = Set.of(
            "imageAnalysis", "image_analysis", "analyseImage", "analyse_image"
    );

    private static final Set<String> JUSTIFICATION_KEYS = Set.of(
            "justification", "reason", "raison", "explication"
    );

    private static final Set<String> COST_BREAKDOWN_KEYS = Set.of(
            "costBreakdown", "cost_breakdown", "detailCout",
            "detail_cout", "postesCout", "breakdown"
    );

    private static final Set<String> SEVERITY_KEYS = Set.of(
            "severity", "gravite", "gravité", "damageSeverity",
            "niveauGravite", "niveau_gravite"
    );

    private static final Set<String> INDICATOR_KEYS = Set.of(
            "damageIndicators", "indicateurs", "visibleSigns",
            "signesVisibles", "signes_visibles", "elementsEndommages", "élémentsEndommagés"
    );

    private static final Set<String> LEARNING_APPLIED_KEYS = Set.of(
            "learningApplied", "learning_applied", "apprentissageUtilise", "learningUsed"
    );

    private static final Set<String> LEARNING_REASON_KEYS = Set.of(
            "learningReason", "learning_reason", "raisonLearning", "apprentissageRaison"
    );

    private final ChatLanguageModel visionModel;
    private final ObjectMapper objectMapper;
    private final AiAgentConfigService aiAgentConfigService;
    private final AgentLearningMemoryService learningMemoryService;

    public AgentEstimateur(
            @Qualifier("visionLanguageModel") ChatLanguageModel visionModel,
            ObjectMapper objectMapper,
            AiAgentConfigService aiAgentConfigService,
            AgentLearningMemoryService learningMemoryService
    ) {
        this.visionModel = visionModel;
        this.objectMapper = objectMapper;
        this.aiAgentConfigService = aiAgentConfigService;
        this.learningMemoryService = learningMemoryService;
    }

    public AgentResult estimate(
            Claim claim,
            AgentResult routeResult,
            AgentResult validationResult
    ) {
        long startedAt = System.nanoTime();

        if (claim == null) {
            throw new IllegalArgumentException("Le claim ne doit pas être null");
        }

        String typeDetecte = extractClaimType(routeResult);
        double humanReviewThreshold = getHumanReviewThreshold();

        log.info("{} - estimation claim #{} | type={}", AGENT_NAME, claim.getId(), typeDetecte);

        List<ClaimDocument> images = extractValidImages(claim);

        if (images.isEmpty()) {
            return finalizeResult(
                    startedAt,
                    "NO_IMAGE",
                    claim,
                    0.0,
                    0.0,
                    0.0,
                    DEFAULT_CONFIDENCE,
                    "Aucune image exploitable fournie.",
                    "",
                    "",
                    "",
                    false,
                    "Aucun learning appliqué car aucune image exploitable.",
                    true,
                    null
            );
        }

        CompletableFuture<String> learningFuture = CompletableFuture.supplyAsync(
                () -> learningMemoryService.buildMemoryBlock(AgentName.AGENT_ESTIMATEUR, claim.getId())
        );

        CompletableFuture<List<EncodedImage>> imagesFuture = CompletableFuture.supplyAsync(
                () -> loadAndEncodeImages(images)
        );

        CompletableFuture.allOf(learningFuture, imagesFuture).join();

        String rawLearningExamples = safe(learningFuture.join());

        log.info(
                "{} - learning RAW avant filtre | chars={} | claim #{}",
                AGENT_NAME,
                rawLearningExamples.length(),
                claim.getId()
        );

        String learningExamples = filterLearningByTypeAndSimilarity(
                rawLearningExamples,
                typeDetecte,
                freshClaimContextForLearning(claim)
        );

        learningExamples = truncate(learningExamples, MAX_LEARNING_CHARS);

        List<EncodedImage> encodedImages = imagesFuture.join();

        log.info(
                "{} - learning filtré type+similarité | type={} | chars={} | images={} | claim #{}",
                AGENT_NAME,
                typeDetecte,
                learningExamples.length(),
                encodedImages.size(),
                claim.getId()
        );

        if (encodedImages.isEmpty()) {
            return finalizeResult(
                    startedAt,
                    "IMAGE_READ_ERROR",
                    claim,
                    0.0,
                    0.0,
                    0.0,
                    DEFAULT_CONFIDENCE,
                    "Images illisibles ou introuvables sur disque.",
                    "",
                    "",
                    "",
                    false,
                    "Aucun learning appliqué car image illisible.",
                    true,
                    null
            );
        }

        try {
            String prompt = buildPrompt(
                    claim,
                    typeDetecte,
                    validationResult,
                    humanReviewThreshold,
                    learningExamples
            );

            List<Content> contents = buildVisionContents(prompt, encodedImages);
            UserMessage userMessage = UserMessage.from(contents);

            log.info(
                    "{} - envoi modèle vision claim #{} | prompt={} chars | seuil={}",
                    AGENT_NAME,
                    claim.getId(),
                    prompt.length(),
                    humanReviewThreshold
            );

            String rawResponse = callVisionModelSafely(userMessage, claim.getId());

            AgentResult result = parseResponse(
                    rawResponse,
                    claim,
                    typeDetecte,
                    humanReviewThreshold
            );

            log.info(
                    "{} - estimation claim #{} terminée en {} ms | conclusion={} | confidence={} | humanReview={}",
                    AGENT_NAME,
                    claim.getId(),
                    elapsedMs(startedAt),
                    result.getConclusion(),
                    result.getConfidenceScore(),
                    result.isNeedsHumanReview()
            );

            return result;

        } catch (Exception e) {
            log.error("Erreur AgentEstimateur claim #{}: {}", claim.getId(), e.getMessage(), e);

            return finalizeResult(
                    startedAt,
                    "TECHNICAL_ERROR",
                    claim,
                    0.0,
                    0.0,
                    0.0,
                    0.0,
                    "Erreur technique : " + e.getMessage(),
                    "",
                    "",
                    "",
                    false,
                    "Aucun learning appliqué à cause d'une erreur technique.",
                    true,
                    null
            );
        }
    }

    private String buildPrompt(
            Claim claim,
            String typeDetecte,
            AgentResult validationResult,
            double humanReviewThreshold,
            String learningExamples
    ) {
        String decisionValidateur = validationResult != null
                ? safe(validationResult.getConclusion())
                : "INCONNU";

        String description = truncate(safe(claim.getDescription()), MAX_DESCRIPTION_CHARS);

        String incidentDate = claim.getIncidentDate() != null
                ? claim.getIncidentDate().toString()
                : "Inconnue";

        String memorySection = learningExamples == null || learningExamples.isBlank()
                ? "Aucun exemple expert vraiment similaire disponible."
                : learningExamples;

        return """
                Tu es un agent estimateur d'assurance en Tunisie.

                OBJECTIF :
                Générer une estimation financière réaliste en dinars tunisiens (DT)
                à partir de l'image actuelle, de la description, du type de sinistre et des corrections expert similaires.

                TYPE DU DOSSIER ACTUEL :
                %s

                DÉCISION VALIDATEUR :
                %s

                DATE INCIDENT :
                %s

                DESCRIPTION CLIENT :
                %s

                EXEMPLES LEARNING FILTRÉS PAR TYPE ET SIMILARITÉ :
                %s

                RÈGLE IMPORTANTE SUR LE LEARNING :
                - Les exemples ci-dessus sont uniquement des références de calibration.
                - Tu dois d'abord analyser l'image actuelle.
                - Ne copie jamais automatiquement les anciens montants.
                - Même type ne veut pas dire même estimation.
                - Un dossier santé avec plâtre ne doit pas calibrer un dossier santé avec hospitalisation.
                - Un dossier santé simple ne doit pas produire des dizaines de milliers de dinars sans chirurgie grave ou hospitalisation longue.
                - Un dossier auto avec rayure ne doit pas calibrer un choc frontal.
                - Si aucun exemple n'est vraiment similaire, learningApplied=false et estime uniquement par ton analyse IA.
                - Si un exemple est similaire, learningApplied=true et explique précisément son influence dans learningReason.
                - Même si un exemple est similaire, adapte les montants selon l'image actuelle.

                ANALYSE IMAGE OBLIGATOIRE :
                Tu dois analyser l'image ou le document visuel :
                - ce qui est visible,
                - ce qui n'est pas visible,
                - les indices utilisés,
                - le niveau d'incertitude,
                - les limites de l'analyse.

                RÈGLES PAR TYPE :
                - AUTO : pièces visibles, carrosserie, pare-chocs, phare, capot, peinture, main-d'œuvre, structure éventuelle.
                - SANTE : consultation, radio, plâtre, médicaments, hospitalisation, chirurgie, soins.
                  Pour un cas santé simple sans chirurgie lourde, sans hospitalisation longue, sans invalidité,
                  ne propose jamais des montants de dizaines de milliers de dinars.
                  Les montants très élevés sont réservés uniquement aux cas clairement graves :
                  chirurgie lourde, hospitalisation longue, invalidité, réanimation, prothèse ou soins intensifs.
                - HABITATION : fuite, incendie, mur, plafond, sol, meuble, surface touchée, réparation.
                - VOYAGE : bagage, retard, annulation, transport, hébergement, frais médicaux à l'étranger.
                - VIE : décès, invalidité, capital, documents administratifs ; needsHumanReview souvent true.

                MÉTHODE DE RAISONNEMENT :
                1. Analyse l'image actuelle.
                2. Identifie les éléments visibles ou documents analysés.
                3. Déduis la gravité : LEGER, MODERE, GRAVE ou TOTAL_POTENTIEL.
                4. Décompose les postes de coût.
                5. Vérifie si un exemple learning est vraiment similaire.
                6. Donne estimationMin, estimationMoyenne, estimationMax.
                7. Justifie clairement l'estimation.
                8. Vérifie la cohérence du montant avec les éléments visibles.

                REVIEW HUMAINE :
                Si confidence < %.2f, mets needsHumanReview=true.
                Si image insuffisante ou incertitude forte, mets needsHumanReview=true.
                Si le montant est élevé ou sensible, mets needsHumanReview=true.

                FORMAT OBLIGATOIRE :
                Réponds uniquement en JSON valide, sans markdown, sans texte avant ou après.

                {
                  "elementsEndommages": "éléments visibles ou documents analysés",
                  "imageAnalysis": "analyse détaillée de l'image ou du document visuel actuel",
                  "damageIndicators": "indices visuels utilisés",
                  "severity": "LEGER|MODERE|GRAVE|TOTAL_POTENTIEL",
                  "costBreakdown": "détail des postes de coût estimés",
                  "estimationMin": 0,
                  "estimationMoyenne": 0,
                  "estimationMax": 0,
                  "confidence": 0.0,
                  "justification": "justification complète du montant estimé",
                  "analyse": "raisonnement IA final",
                  "learningApplied": false,
                  "learningReason": "expliquer si un exemple expert similaire a influencé l'estimation",
                  "needsHumanReview": true
                }
                """.formatted(
                typeDetecte,
                decisionValidateur,
                incidentDate,
                description,
                memorySection,
                humanReviewThreshold
        );
    }

    private AgentResult parseResponse(
            String raw,
            Claim claim,
            String typeDetecte,
            double humanReviewThreshold
    ) {
        if (raw == null || raw.isBlank()) {
            return buildResult(
                    claim,
                    0.0,
                    0.0,
                    0.0,
                    0.0,
                    "Réponse vide du modèle de vision.",
                    "",
                    "",
                    "",
                    false,
                    "Aucun learning appliqué : réponse vide.",
                    true,
                    raw
            );
        }

        try {
            String jsonOnly = extractJson(raw);
            JsonNode node = objectMapper.readTree(jsonOnly);

            double estimationMin = normalizeMoney(findDouble(node, MIN_KEYS, 0.0));
            double estimationMax = normalizeMoney(findDouble(node, MAX_KEYS, 0.0));
            double estimationMoyenne = normalizeMoney(findDouble(node, MOY_KEYS, 0.0));
            double confidence = normalizeConfidence(findDouble(node, CONF_KEYS, DEFAULT_CONFIDENCE));

            String analyse = findText(node, ANALYSE_KEYS, "");
            String imageAnalysis = findText(node, IMAGE_ANALYSIS_KEYS, "");
            String justification = findText(node, JUSTIFICATION_KEYS, "");
            String costBreakdown = findText(node, COST_BREAKDOWN_KEYS, "");
            String severity = findText(node, SEVERITY_KEYS, "");
            String damageIndicators = findText(node, INDICATOR_KEYS, "");

            boolean learningApplied = findBoolean(node, LEARNING_APPLIED_KEYS, false);
            String learningReason = findText(node, LEARNING_REASON_KEYS, "");

            boolean needsHuman = node.path("needsHumanReview").asBoolean(false);

            if (estimationMin == 0.0 && estimationMax == 0.0) {
                return fallbackFromText(raw, claim, typeDetecte, humanReviewThreshold);
            }

            return applyBusinessRules(
                    claim,
                    typeDetecte,
                    humanReviewThreshold,
                    estimationMin,
                    estimationMax,
                    estimationMoyenne,
                    confidence,
                    analyse,
                    imageAnalysis,
                    justification,
                    costBreakdown,
                    learningReason,
                    severity,
                    damageIndicators,
                    learningApplied,
                    needsHuman,
                    raw
            );

        } catch (Exception e) {
            log.warn("JSON absent claim #{} — fallback regex : {}", claim.getId(), e.getMessage());

            return fallbackFromText(raw, claim, typeDetecte, humanReviewThreshold);
        }
    }

    private AgentResult applyBusinessRules(
            Claim claim,
            String typeDetecte,
            double humanReviewThreshold,
            double estimationMin,
            double estimationMax,
            double estimationMoyenne,
            double confidence,
            String analyse,
            String imageAnalysis,
            String justification,
            String costBreakdown,
            String learningReason,
            String severity,
            String damageIndicators,
            boolean learningApplied,
            boolean needsHuman,
            String raw
    ) {
        if (estimationMin > estimationMax) {
            double tmp = estimationMin;
            estimationMin = estimationMax;
            estimationMax = tmp;
        }

        if (estimationMoyenne < estimationMin || estimationMoyenne > estimationMax) {
            estimationMoyenne = (estimationMin + estimationMax) / 2.0;
        }

        String safetyText = safe(analyse)
                + " " + safe(imageAnalysis)
                + " " + safe(justification)
                + " " + safe(costBreakdown)
                + " " + safe(learningReason)
                + " " + safe(severity)
                + " " + safe(damageIndicators)
                + " " + safe(raw);

        boolean severityIsLight = isLightSeverity(severity);
        boolean severityIsHeavy = isHeavySeverity(severity);

        boolean graveAutoSignals = isSevereAutoDamage(typeDetecte, safetyText);

        boolean suspiciousLowAuto = !severityIsLight
                && isSuspiciousLowAutoEstimate(typeDetecte, safetyText, estimationMax);

        boolean incoherentSeverity = !isEstimationCoherentWithSeverity(
                severity,
                estimationMax,
                typeDetecte
        );

        boolean unrealisticHealth = isUnrealisticHealthEstimate(
                typeDetecte,
                safetyText,
                estimationMax
        );

        // Pas de montants forcés ici.
        // On garde les montants générés par le LLM, mais on baisse la confiance et on demande review humaine.
        if (graveAutoSignals || suspiciousLowAuto || incoherentSeverity || unrealisticHealth) {
            needsHuman = true;

            if (unrealisticHealth) {
                confidence = Math.min(confidence, 0.45);
                analyse = appendRuleNote(
                        analyse,
                        "Alerte anti-hallucination : estimation santé très élevée sans indice clair de chirurgie lourde, hospitalisation longue ou invalidité. Validation expert obligatoire."
                );
            } else {
                confidence = Math.min(confidence, 0.70);
                analyse = appendRuleNote(
                        analyse,
                        "Alerte cohérence : estimation à vérifier par expert selon les signes visibles et/ou la gravité déclarée."
                );
            }
        }

        if (severityIsHeavy) {
            needsHuman = true;
        }

        if (confidence < humanReviewThreshold) {
            needsHuman = true;
        }

        if (safe(analyse).isBlank() && safe(imageAnalysis).isBlank()) {
            analyse = "Analyse non fournie.";
            needsHuman = true;
        }

        double maxAllowed = getMaxAllowedByType(typeDetecte);

        if (estimationMax > maxAllowed) {
            needsHuman = true;
            confidence = Math.min(confidence, 0.70);
            analyse = appendRuleNote(
                    analyse,
                    "Alerte plafond : estimation très élevée pour ce type de sinistre, validation expert recommandée."
            );
        }

        String finalAnalyse = buildFinalAnalyse(
                analyse,
                imageAnalysis,
                justification,
                costBreakdown,
                learningApplied,
                learningReason,
                severity,
                damageIndicators
        );

        return buildResult(
                claim,
                estimationMin,
                estimationMax,
                estimationMoyenne,
                confidence,
                finalAnalyse,
                imageAnalysis,
                justification,
                costBreakdown,
                learningApplied,
                learningReason,
                needsHuman,
                raw
        );
    }

    private AgentResult fallbackFromText(
            String raw,
            Claim claim,
            String typeDetecte,
            double humanReviewThreshold
    ) {
        double min = 0.0;
        double max = 0.0;
        double moyenne = 0.0;
        boolean found = false;

        Matcher m1 = RANGE_AMOUNT_PATTERN.matcher(raw);
        if (m1.find()) {
            min = parseAmount(m1.group(1));
            max = parseAmount(m1.group(2));
            moyenne = (min + max) / 2.0;
            found = true;
        }

        if (!found) {
            Matcher m2 = DINAR_PATTERN.matcher(raw);
            List<Double> amounts = new ArrayList<>();

            while (m2.find()) {
                double value = parseAmount(m2.group(1));

                if (value >= 50) {
                    amounts.add(value);
                }
            }

            if (!amounts.isEmpty()) {
                min = Collections.min(amounts);
                max = Collections.max(amounts);
                moyenne = amounts.stream()
                        .mapToDouble(Double::doubleValue)
                        .average()
                        .orElse(0.0);
                found = true;
            }
        }

        if (!found) {
            Matcher m3 = NUMBER_RANGE_PATTERN.matcher(raw);

            if (m3.find()) {
                double v1 = parseAmount(m3.group(1));
                double v2 = parseAmount(m3.group(2));

                if (v1 >= 50 && v2 >= 50) {
                    min = Math.min(v1, v2);
                    max = Math.max(v1, v2);
                    moyenne = (min + max) / 2.0;
                    found = true;
                }
            }
        }

        if (!found) {
            Matcher m4 = SINGLE_AMOUNT_PATTERN.matcher(raw);
            List<Double> amounts = new ArrayList<>();

            while (m4.find()) {
                double value = parseAmount(m4.group(1));

                if (value >= 50) {
                    amounts.add(value);
                }
            }

            if (!amounts.isEmpty()) {
                min = Collections.min(amounts);
                max = Collections.max(amounts);
                moyenne = amounts.stream()
                        .mapToDouble(Double::doubleValue)
                        .average()
                        .orElse(0.0);
                found = true;
            }
        }

        if (!found) {
            Matcher m5 = STANDALONE_NUMBER_PATTERN.matcher(raw);
            List<Double> amounts = new ArrayList<>();

            while (m5.find()) {
                double value = parseAmount(m5.group(1));

                if (value >= 50) {
                    amounts.add(value);
                }
            }

            if (!amounts.isEmpty()) {
                moyenne = amounts.stream()
                        .mapToDouble(Double::doubleValue)
                        .average()
                        .orElse(0.0);
                min = moyenne * 0.8;
                max = moyenne * 1.2;
                found = true;
            }
        }

        String analyse = extractAnalyseFromText(raw);
        double confidence = found ? 0.45 : 0.10;

        return applyBusinessRules(
                claim,
                typeDetecte,
                humanReviewThreshold,
                min,
                max,
                moyenne,
                confidence,
                analyse,
                "",
                "",
                "",
                "Fallback texte utilisé.",
                "",
                "",
                false,
                true,
                raw
        );
    }

    private String freshClaimContextForLearning(Claim claim) {
        StringBuilder sb = new StringBuilder();

        sb.append(safe(claim.getDescription())).append(" ");

        if (claim.getPolicy() != null) {
            sb.append(safe(claim.getPolicy().getType())).append(" ");
            sb.append(safe(claim.getPolicy().getFormule())).append(" ");
            sb.append(safe(claim.getPolicy().getCoverageDetails())).append(" ");
            sb.append(safe(claim.getPolicy().getProductCode())).append(" ");
        }

        if (claim.getDocuments() != null) {
            for (ClaimDocument doc : claim.getDocuments()) {
                sb.append(safe(doc.getFileName())).append(" ");
                sb.append(safe(doc.getFileType())).append(" ");
            }
        }

        return sb.toString().trim();
    }

    private String filterLearningByTypeAndSimilarity(
            String memoryBlock,
            String claimType,
            String currentClaimContext
    ) {
        String type = normalizeTypeForLearning(claimType);

        if (memoryBlock == null || memoryBlock.isBlank()) {
            return "";
        }

        if (type.equals("INCONNU")) {
            return "";
        }

        String[] examples = memoryBlock.split(
                "(?=CORRECTION EXPERT A APPRENDRE|EXEMPLE VALIDE PAR EXPERT|CORRECTION EXPERT À APPRENDRE|EXEMPLE VALIDÉ|CORRECTIONS EXPERTES A APPLIQUER|EXEMPLES VALIDES PAR EXPERT)"
        );

        List<ScoredLearningExample> scored = new ArrayList<>();

        for (String example : examples) {
            if (example == null || example.isBlank()) {
                continue;
            }

            if (!learningExampleHasSameType(example, type)) {
                continue;
            }

            double similarity = calculateTextSimilarity(currentClaimContext, example);

            if (similarity >= MIN_SIMILARITY_SCORE) {
                scored.add(new ScoredLearningExample(example.trim(), similarity));
            }
        }

        scored.sort(Comparator.comparingDouble(ScoredLearningExample::score).reversed());

        StringBuilder filtered = new StringBuilder();

        scored.stream()
                .limit(3)
                .forEach(item -> filtered
                        .append("SIMILARITÉ DOSSIER: ")
                        .append(String.format(Locale.US, "%.2f", item.score()))
                        .append("\n")
                        .append(item.text())
                        .append("\n\n")
                );

        return filtered.toString().trim();
    }

    private boolean learningExampleHasSameType(String example, String type) {
        String normalized = normalizeForRules(example);
        String t = normalizeForRules(type);

        boolean explicitType =
                normalized.contains("type=" + t)
                        || normalized.contains("type: " + t)
                        || normalized.contains("type contrat: " + t)
                        || normalized.contains("type police: " + t)
                        || normalized.contains("final routeur type: " + t)
                        || normalized.contains("predicted routeur type: " + t)
                        || normalized.contains("type de sinistre: " + t)
                        || (normalized.contains("policy:") && normalized.contains("type=" + t));

        if (explicitType) {
            return true;
        }

        return type.equals(inferTypeFromText(example));
    }

    private String inferTypeFromText(String text) {
        String n = normalizeForRules(text);

        if (containsAny(
                n,
                "voiture",
                "vehicule",
                "véhicule",
                "vehicle",
                "auto",
                "pare choc",
                "pare-choc",
                "capot",
                "phare",
                "carrosserie",
                "collision",
                "accident voiture"
        )) {
            return "AUTO";
        }

        if (containsAny(
                n,
                "sante",
                "santé",
                "medical",
                "médical",
                "consultation",
                "radio",
                "radiographie",
                "platre",
                "plâtre",
                "hospitalisation",
                "chirurgie",
                "medicament",
                "médicament",
                "fracture",
                "soins"
        )) {
            return "SANTE";
        }

        if (containsAny(
                n,
                "habitation",
                "maison",
                "logement",
                "fuite",
                "degat des eaux",
                "dégât des eaux",
                "incendie",
                "mur",
                "plafond",
                "sol",
                "meuble"
        )) {
            return "HABITATION";
        }

        if (containsAny(
                n,
                "voyage",
                "bagage",
                "vol retard",
                "retard avion",
                "annulation",
                "hotel",
                "hôtel",
                "transport",
                "etranger",
                "étranger"
        )) {
            return "VOYAGE";
        }

        if (containsAny(
                n,
                "deces",
                "décès",
                "invalidite",
                "invalidité",
                "assurance vie",
                "beneficiaire",
                "bénéficiaire",
                "capital"
        )) {
            return "VIE";
        }

        return "INCONNU";
    }

    private double calculateTextSimilarity(String current, String example) {
        Set<String> a = importantTokens(current);
        Set<String> b = importantTokens(example);

        if (a.isEmpty() || b.isEmpty()) {
            return 0.0;
        }

        Set<String> intersection = new HashSet<>(a);
        intersection.retainAll(b);

        Set<String> union = new HashSet<>(a);
        union.addAll(b);

        return (double) intersection.size() / (double) union.size();
    }

    private Set<String> importantTokens(String text) {
        String normalized = normalizeForRules(text);

        Set<String> stopWords = Set.of(
                "le", "la", "les", "un", "une", "des", "du", "de", "d", "et",
                "a", "au", "aux", "en", "pour", "par", "avec", "sans", "sur",
                "dans", "claim", "id", "policy", "type", "date", "description",
                "sinistre", "dossier", "client", "information", "non", "disponible",
                "final", "estimation", "moyenne", "corrigee", "corrige", "expert",
                "sortie", "initiale", "validee", "valide", "commentaire", "satisfaction"
        );

        Set<String> tokens = new HashSet<>();

        for (String token : normalized.split("[^a-z0-9]+")) {
            if (token.length() < 4) {
                continue;
            }

            if (stopWords.contains(token)) {
                continue;
            }

            tokens.add(token);
        }

        return tokens;
    }

    private String normalizeTypeForLearning(String value) {
        String v = normalizeForRules(value);

        if (v.contains("auto")) {
            return "AUTO";
        }

        if (v.contains("sante") || v.contains("health")) {
            return "SANTE";
        }

        if (v.contains("habitation") || v.contains("home")) {
            return "HABITATION";
        }

        if (v.contains("voyage") || v.contains("travel")) {
            return "VOYAGE";
        }

        if (v.contains("vie") || v.contains("life")) {
            return "VIE";
        }

        return "INCONNU";
    }

    private AgentResult finalizeResult(
            long startedAt,
            String mode,
            Claim claim,
            double min,
            double max,
            double moyenne,
            double confidence,
            String analyse,
            String imageAnalysis,
            String justification,
            String costBreakdown,
            boolean learningApplied,
            String learningReason,
            boolean needsHuman,
            String rawResponse
    ) {
        AgentResult result = buildResult(
                claim,
                min,
                max,
                moyenne,
                confidence,
                analyse,
                imageAnalysis,
                justification,
                costBreakdown,
                learningApplied,
                learningReason,
                needsHuman,
                rawResponse
        );

        log.info(
                "{} - claim #{} terminé en {} ms | mode={} | conclusion={} | confidence={} | humanReview={}",
                AGENT_NAME,
                claim.getId(),
                elapsedMs(startedAt),
                mode,
                result.getConclusion(),
                result.getConfidenceScore(),
                result.isNeedsHumanReview()
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
            String imageAnalysis,
            String justification,
            String costBreakdown,
            boolean learningApplied,
            String learningReason,
            boolean needsHuman,
            String rawResponse
    ) {
        AgentResult result = new AgentResult();

        result.setAgentName(AGENT_NAME);
        result.setClaim(claim);
        result.setConclusion(buildConclusion(estimationMin, estimationMax, estimationMoyenne));
        result.setConfidenceScore(confidence);
        result.setNeedsHumanReview(needsHuman);
        result.setCreatedAt(LocalDateTime.now());

        if (rawResponse != null && !rawResponse.isBlank()) {
            result.setRawLlmResponse(rawResponse);
        } else {
            result.setRawLlmResponse(buildRawJson(
                    estimationMin,
                    estimationMoyenne,
                    estimationMax,
                    confidence,
                    analyse,
                    imageAnalysis,
                    justification,
                    costBreakdown,
                    learningReason,
                    learningApplied,
                    needsHuman
            ));
        }

        return result;
    }

    private String buildConclusion(double min, double max, double moyenne) {
        return "Estimation min: %.2f DT | moyenne: %.2f DT | max: %.2f DT"
                .formatted(min, moyenne, max);
    }

    private String buildRawJson(
            double min,
            double moyenne,
            double max,
            double confidence,
            String analyse,
            String imageAnalysis,
            String justification,
            String costBreakdown,
            String learningReason,
            boolean learningApplied,
            boolean needsHuman
    ) {
        return """
                {
                  "estimationMin": %.2f,
                  "estimationMoyenne": %.2f,
                  "estimationMax": %.2f,
                  "confidence": %.2f,
                  "imageAnalysis": "%s",
                  "analyse": "%s",
                  "costBreakdown": "%s",
                  "justification": "%s",
                  "learningApplied": %s,
                  "learningReason": "%s",
                  "needsHumanReview": %s
                }
                """.formatted(
                min,
                moyenne,
                max,
                confidence,
                escapeJson(imageAnalysis),
                escapeJson(analyse),
                escapeJson(costBreakdown),
                escapeJson(justification),
                learningApplied,
                escapeJson(learningReason),
                needsHuman
        ).trim();
    }

    private String buildFinalAnalyse(
            String analyse,
            String imageAnalysis,
            String justification,
            String costBreakdown,
            boolean learningApplied,
            String learningReason,
            String severity,
            String damageIndicators
    ) {
        StringBuilder sb = new StringBuilder();

        if (!safe(imageAnalysis).isBlank()) {
            sb.append("Analyse image:\n").append(imageAnalysis).append("\n\n");
        }

        if (!safe(analyse).isBlank()) {
            sb.append("Analyse IA:\n").append(analyse).append("\n\n");
        }

        if (!safe(costBreakdown).isBlank()) {
            sb.append("Détail des coûts:\n").append(costBreakdown).append("\n\n");
        }

        if (!safe(justification).isBlank()) {
            sb.append("Justification estimation:\n").append(justification).append("\n\n");
        }

        if (!safe(severity).isBlank()) {
            sb.append("Gravité: ").append(severity).append("\n");
        }

        if (!safe(damageIndicators).isBlank()) {
            sb.append("Indicateurs: ").append(damageIndicators).append("\n");
        }

        sb.append("Learning appliqué: ").append(learningApplied ? "OUI" : "NON").append("\n");

        if (!safe(learningReason).isBlank()) {
            sb.append("Raison learning: ").append(learningReason);
        }

        return sb.toString().trim();
    }

    private List<ClaimDocument> extractValidImages(Claim claim) {
        if (claim.getDocuments() == null || claim.getDocuments().isEmpty()) {
            return List.of();
        }

        return claim.getDocuments()
                .stream()
                .filter(doc -> doc.getFileType() != null)
                .filter(doc -> SUPPORTED_IMAGE_TYPES.contains(doc.getFileType().toLowerCase(Locale.ROOT)))
                .filter(doc -> doc.getFilePath() != null && !doc.getFilePath().isBlank())
                .limit(MAX_IMAGES)
                .toList();
    }

    private List<EncodedImage> loadAndEncodeImages(List<ClaimDocument> docs) {
        List<EncodedImage> result = new ArrayList<>();

        for (ClaimDocument doc : docs) {
            try {
                String filePath = doc.getFilePath();
                String cached = imageBase64Cache.get(filePath);

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
                String base64 = resizeAndEncodeJpeg(rawBytes);

                imageBase64Cache.put(filePath, base64);
                result.add(new EncodedImage(base64, "image/jpeg"));

            } catch (Exception e) {
                log.warn("Impossible de charger {} : {}", doc.getFilePath(), e.getMessage());
            }
        }

        return result;
    }

    private String resizeAndEncodeJpeg(byte[] rawBytes) throws IOException {
        BufferedImage original = ImageIO.read(new ByteArrayInputStream(rawBytes));

        if (original == null) {
            return Base64.getEncoder().encodeToString(rawBytes);
        }

        BufferedImage working = original;

        int width = working.getWidth();
        int height = working.getHeight();

        if (width > IMAGE_MAX_DIMENSION || height > IMAGE_MAX_DIMENSION) {
            double ratio = Math.min(
                    (double) IMAGE_MAX_DIMENSION / width,
                    (double) IMAGE_MAX_DIMENSION / height
            );

            int newWidth = Math.max(1, (int) (width * ratio));
            int newHeight = Math.max(1, (int) (height * ratio));

            BufferedImage resized = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB);

            Graphics2D graphics = resized.createGraphics();
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.drawImage(working, 0, 0, newWidth, newHeight, null);
            graphics.dispose();

            working = resized;
        }

        if (working.getType() != BufferedImage.TYPE_INT_RGB) {
            BufferedImage rgb = new BufferedImage(
                    working.getWidth(),
                    working.getHeight(),
                    BufferedImage.TYPE_INT_RGB
            );

            Graphics2D graphics = rgb.createGraphics();
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, rgb.getWidth(), rgb.getHeight());
            graphics.drawImage(working, 0, 0, null);
            graphics.dispose();

            working = rgb;
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");

        if (!writers.hasNext()) {
            ImageIO.write(working, "png", baos);
        } else {
            ImageWriter writer = writers.next();
            ImageWriteParam param = writer.getDefaultWriteParam();

            if (param.canWriteCompressed()) {
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(IMAGE_JPEG_QUALITY);
            }

            try (ImageOutputStream ios = ImageIO.createImageOutputStream(baos)) {
                writer.setOutput(ios);
                writer.write(null, new IIOImage(working, null, null), param);
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

            String raw = response != null && response.content() != null
                    ? response.content().text()
                    : "";

            raw = raw == null ? "" : raw.trim();

            log.info(
                    "{} - réponse vision claim #{} en {} ms | {} chars",
                    AGENT_NAME,
                    claimId,
                    elapsedMs(startedAt),
                    raw.length()
            );

            return raw;
        } catch (Exception e) {
            log.error("Erreur appel modèle vision claim #{}", claimId, e);
            return "";
        }
    }

    private double findDouble(JsonNode node, Set<String> keys, double defaultValue) {
        for (String key : keys) {
            JsonNode n = node.path(key);

            if (!n.isMissingNode() && !n.isNull()) {
                if (n.isNumber()) {
                    return n.asDouble(defaultValue);
                }

                String text = nodeText(n);

                if (!text.isBlank()) {
                    double parsed = parseAmountLoose(text);

                    if (parsed >= 0.0) {
                        return parsed;
                    }
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

                if (!value.isBlank()) {
                    return value;
                }
            }
        }

        return defaultValue;
    }

    private boolean findBoolean(JsonNode node, Set<String> keys, boolean defaultValue) {
        for (String key : keys) {
            JsonNode n = node.path(key);

            if (!n.isMissingNode() && !n.isNull()) {
                if (n.isBoolean()) {
                    return n.asBoolean(defaultValue);
                }

                String value = nodeText(n).toLowerCase(Locale.ROOT);

                if (value.equals("true") || value.equals("oui") || value.equals("yes")) {
                    return true;
                }

                if (value.equals("false") || value.equals("non") || value.equals("no")) {
                    return false;
                }
            }
        }

        return defaultValue;
    }

    private String nodeText(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return "";
        }

        if (node.isArray()) {
            StringBuilder sb = new StringBuilder();

            for (JsonNode item : node) {
                if (item != null && !item.isNull()) {
                    if (sb.length() > 0) {
                        sb.append(", ");
                    }

                    sb.append(item.asText(""));
                }
            }

            return sb.toString().trim();
        }

        if (node.isObject()) {
            return node.toString();
        }

        return node.asText("").trim();
    }

    private String extractJson(String raw) {
        String clean = raw
                .replaceAll("(?s)<think>.*?</think>", "")
                .replace("```json", "")
                .replace("```", "")
                .trim();

        int start = clean.indexOf('{');
        int end = clean.lastIndexOf('}');

        if (start < 0 || end <= start) {
            throw new IllegalArgumentException("Aucun JSON trouvé");
        }

        return clean.substring(start, end + 1).trim();
    }

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

            double threshold = aiAgentConfigService.getThreshold(CONFIG_KEY);

            cachedThreshold = threshold;
            thresholdCacheExpiresAt = now + THRESHOLD_CACHE_TTL_MS;

            return threshold;
        }
    }

    private boolean isSevereAutoDamage(String typeDetecte, String text) {
        if (!normalizeForRules(typeDetecte).contains("auto")) {
            return false;
        }

        int signals = countAnyNormalized(
                text,
                "grave",
                "choc frontal",
                "pare choc arrache",
                "pare-choc arrache",
                "pare chocs arrache",
                "pare-chocs arrache",
                "capot fortement deforme",
                "airbag",
                "fuite",
                "radiateur",
                "chassis",
                "structure",
                "traverse",
                "moteur",
                "engine",
                "radiator",
                "frame",
                "structural",
                "destroyed",
                "severe damage"
        );

        return signals >= 2;
    }

    private boolean isSuspiciousLowAutoEstimate(
            String typeDetecte,
            String text,
            double estimationMax
    ) {
        if (!normalizeForRules(typeDetecte).contains("auto")) {
            return false;
        }

        if (estimationMax > 1500.0) {
            return false;
        }

        int damagedZones = countAnyNormalized(
                text,
                "avant",
                "pare choc",
                "pare-choc",
                "pare chocs",
                "pare-chocs",
                "aile",
                "porte",
                "phare",
                "optique",
                "capot",
                "roue",
                "suspension",
                "radiateur"
        );

        return damagedZones >= 4;
    }

    private boolean isHealthType(String typeDetecte) {
        String n = normalizeForRules(typeDetecte);

        return n.contains("sante") || n.contains("health");
    }

    private boolean isSevereHealthCase(String text) {
        String n = normalizeForRules(text);

        return containsAny(
                n,
                "hospitalisation",
                "operation",
                "opération",
                "chirurgie",
                "intervention chirurgicale",
                "urgence",
                "reanimation",
                "réanimation",
                "scanner",
                "irm",
                "prothese",
                "prothèse",
                "fracture grave",
                "multiple fracture",
                "invalidite",
                "invalidité",
                "incapacite permanente",
                "incapacité permanente",
                "soins intensifs"
        );
    }

    private boolean isUnrealisticHealthEstimate(
            String typeDetecte,
            String safetyText,
            double estimationMax
    ) {
        if (!isHealthType(typeDetecte)) {
            return false;
        }

        if (isSevereHealthCase(safetyText)) {
            return false;
        }

        return estimationMax > 3000.0;
    }

    private boolean isLightSeverity(String severity) {
        String normalized = normalizeForRules(severity);

        return normalized.contains("leger")
                || normalized.contains("light")
                || normalized.contains("minor");
    }

    private boolean isHeavySeverity(String severity) {
        String normalized = normalizeForRules(severity);

        return normalized.contains("grave")
                || normalized.contains("total")
                || normalized.contains("severe");
    }

    private boolean isEstimationCoherentWithSeverity(
            String severity,
            double estimationMax,
            String typeDetecte
    ) {
        String normalizedType = normalizeForRules(typeDetecte);
        String normalizedSeverity = normalizeForRules(severity);

        if (normalizedSeverity.isBlank()) {
            return true;
        }

        if (normalizedType.contains("auto")) {
            if (normalizedSeverity.contains("grave") || normalizedSeverity.contains("total")) {
                return estimationMax >= 3000.0;
            }

            if (normalizedSeverity.contains("modere")) {
                return estimationMax >= 500.0;
            }
        }

        return true;
    }

    private double getMaxAllowedByType(String type) {
        if (type == null) {
            return 500_000.0;
        }

        String normalized = type.toUpperCase(Locale.ROOT);

        if (normalized.contains("AUTO")) {
            return 100_000.0;
        }

        if (normalized.contains("SANTE")) {
            return 200_000.0;
        }

        if (normalized.contains("HABITATION")) {
            return 500_000.0;
        }

        if (normalized.contains("VOYAGE")) {
            return 100_000.0;
        }

        if (normalized.contains("VIE")) {
            return 500_000.0;
        }

        return 500_000.0;
    }

    private String extractClaimType(AgentResult routeResult) {
        if (routeResult == null || routeResult.getConclusion() == null) {
            return "INCONNU";
        }

        String upper = routeResult.getConclusion()
                .trim()
                .toUpperCase(Locale.ROOT);

        if (upper.contains("AUTO")) {
            return "AUTO";
        }

        if (upper.contains("HABITATION")) {
            return "HABITATION";
        }

        if (upper.contains("SANTE") || upper.contains("SANTÉ")) {
            return "SANTE";
        }

        if (upper.contains("VOYAGE")) {
            return "VOYAGE";
        }

        if (upper.contains("VIE")) {
            return "VIE";
        }

        return routeResult.getConclusion().trim();
    }

    private String appendRuleNote(String analyse, String note) {
        String base = safe(analyse);

        if (base.isBlank()) {
            return "[" + note + "]";
        }

        if (base.contains(note)) {
            return base;
        }

        return base + " [" + note + "]";
    }

    private double normalizeMoney(double value) {
        return Math.max(0.0, value);
    }

    private double normalizeConfidence(double confidence) {
        if (confidence > 1.0 && confidence <= 100.0) {
            confidence = confidence / 100.0;
        }

        return Math.max(0.0, Math.min(1.0, confidence));
    }

    private double parseAmount(String raw) {
        try {
            return Double.parseDouble(raw.replace(",", ".").trim());
        } catch (Exception e) {
            return 0.0;
        }
    }

    private double parseAmountLoose(String raw) {
        if (raw == null || raw.isBlank()) {
            return -1.0;
        }

        try {
            String cleaned = raw
                    .replace("DT", "")
                    .replace("dt", "")
                    .replace("TND", "")
                    .replace("tnd", "")
                    .replace("dinars", "")
                    .replace("dinar", "")
                    .replace(" ", "")
                    .replace(",", ".")
                    .trim();

            return Double.parseDouble(cleaned);
        } catch (Exception e) {
            return -1.0;
        }
    }

    private String extractAnalyseFromText(String raw) {
        if (raw == null || raw.isBlank()) {
            return "Analyse extraite depuis réponse texte.";
        }

        String clean = raw.replaceAll("\\s+", " ").trim();
        String[] sentences = clean.split("(?<=[.!?])\\s+");

        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < Math.min(2, sentences.length); i++) {
            sb.append(sentences[i]).append(" ");
        }

        String result = sb.toString().trim();

        return result.length() > 300 ? result.substring(0, 300) : result;
    }

    private String normalizeForRules(String value) {
        if (value == null) {
            return "";
        }

        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .trim()
                .replaceAll("\\s+", " ");
    }

    private int countAnyNormalized(String text, String... keywords) {
        if (text == null || text.isBlank()) {
            return 0;
        }

        String normalizedText = normalizeForRules(text);
        int count = 0;

        for (String keyword : keywords) {
            if (normalizedText.contains(normalizeForRules(keyword))) {
                count++;
            }
        }

        return count;
    }

    private boolean containsAny(String text, String... keywords) {
        String n = normalizeForRules(text);

        for (String keyword : keywords) {
            if (n.contains(normalizeForRules(keyword))) {
                return true;
            }
        }

        return false;
    }

    private String truncate(String text, int max) {
        String value = safe(text);

        if (value.length() <= max) {
            return value;
        }

        return value.substring(0, max);
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private String escapeJson(String value) {
        return safe(value)
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", " ")
                .replace("\r", " ");
    }

    private long elapsedMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }

    private record ScoredLearningExample(String text, double score) {
    }

    private record EncodedImage(String base64, String mimeType) {
    }
}