package tn.esprit.insureflow_back.Agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.image.Image;
import dev.langchain4j.data.message.AiMessage;
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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Slf4j
@Component
public class AgentEstimateur {

    private static final String AGENT_NAME = "AgentEstimateur";
    private static final String CONFIG_KEY = "AGENT_ESTIMATEUR";

    private static final double DEFAULT_CONFIDENCE = 0.50;
    private static final int MAX_IMAGES = 4;

    private static final Set<String> SUPPORTED_IMAGE_TYPES = Set.of(
            "image/jpeg",
            "image/jpg",
            "image/png",
            "image/webp"
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

    public AgentResult estimate(Claim claim,
                                AgentResult routeResult,
                                AgentResult validationResult) {

        if (claim == null) {
            throw new IllegalArgumentException("Le claim ne doit pas être null");
        }

        log.info("{} - analyse des images du sinistre #{}", AGENT_NAME, claim.getId());

        List<ClaimDocument> images = extractValidImages(claim);

        if (images.isEmpty()) {
            log.warn("Aucune image exploitable trouvée pour le dossier #{}", claim.getId());
            return buildResult(
                    claim,
                    0.0,
                    0.0,
                    0.0,
                    DEFAULT_CONFIDENCE,
                    "Aucune image exploitable fournie",
                    true,
                    null
            );
        }

        log.info("{} image(s) exploitable(s) trouvée(s)", images.size());

        String typeDetecte = extractClaimType(routeResult);
        double humanReviewThreshold = aiAgentConfigService.getThreshold(CONFIG_KEY);

        log.info("Seuil de confiance dynamique pour {} = {}", CONFIG_KEY, humanReviewThreshold);

        String learningExamples =
                learningMemoryService.buildMemoryBlock(AgentName.AGENT_ESTIMATEUR, claim.getId());

        if (learningExamples == null || learningExamples.isBlank()) {
            log.info("Aucun exemple learning disponible pour {}", AGENT_NAME);
            learningExamples = "";
        } else {
            log.info("Exemples learning chargés pour {}", AGENT_NAME);
        }

        try {
            String prompt = buildPrompt(
                    claim,
                    routeResult,
                    validationResult,
                    humanReviewThreshold,
                    learningExamples
            );

            List<dev.langchain4j.data.message.Content> contents = buildVisionContents(prompt, images);
            UserMessage userMessage = UserMessage.from(contents);

            log.info("Envoi au modèle de vision");
            Response<AiMessage> response = visionModel.generate(userMessage);

            String rawResponse = response != null && response.content() != null
                    ? response.content().text()
                    : "";

            log.info("Réponse brute vision LLM : {}", rawResponse);

            return parseResponse(rawResponse, claim, typeDetecte, humanReviewThreshold);

        } catch (Exception e) {
            log.error("Erreur dans AgentEstimateur pour le dossier #{}: {}", claim.getId(), e.getMessage(), e);
            return buildResult(
                    claim,
                    0.0,
                    0.0,
                    0.0,
                    0.0,
                    "Erreur technique lors de l'analyse des images : " + e.getMessage(),
                    true,
                    null
            );
        }
    }

    private List<ClaimDocument> extractValidImages(Claim claim) {
        if (claim.getDocuments() == null || claim.getDocuments().isEmpty()) {
            return List.of();
        }

        return claim.getDocuments().stream()
                .filter(doc -> doc.getFileType() != null)
                .filter(doc -> SUPPORTED_IMAGE_TYPES.contains(doc.getFileType().toLowerCase(Locale.ROOT)))
                .filter(doc -> doc.getFilePath() != null && !doc.getFilePath().isBlank())
                .limit(MAX_IMAGES)
                .toList();
    }

    private List<dev.langchain4j.data.message.Content> buildVisionContents(String prompt,
                                                                           List<ClaimDocument> images) throws IOException {
        List<dev.langchain4j.data.message.Content> contents = new ArrayList<>();
        contents.add(TextContent.from(prompt));

        for (ClaimDocument imageDoc : images) {
            Path imagePath = Path.of(imageDoc.getFilePath());

            if (!Files.exists(imagePath)) {
                log.warn("Fichier image introuvable : {}", imageDoc.getFilePath());
                continue;
            }

            log.info("Ajout image : {}", imageDoc.getFileName());

            byte[] imageBytes = Files.readAllBytes(imagePath);
            String base64 = Base64.getEncoder().encodeToString(imageBytes);

            contents.add(
                    ImageContent.from(
                            Image.builder()
                                    .base64Data(base64)
                                    .mimeType(imageDoc.getFileType())
                                    .build()
                    )
            );
        }

        return contents;
    }

    private String buildPrompt(Claim claim,
                               AgentResult routeResult,
                               AgentResult validationResult,
                               double humanReviewThreshold,
                               String learningExamples) {

        String typeDetecte = extractClaimType(routeResult);
        String decisionValidateur = validationResult != null
                ? safe(validationResult.getConclusion())
                : "INCONNU";

        String justificationValidation = validationResult != null
                ? safe(validationResult.getRawLlmResponse())
                : "";

        String plafonds = getPlafondsByType(typeDetecte);
        String reglesMetier = getReglesMetierByType(typeDetecte);

        String memorySection = learningExamples == null || learningExamples.isBlank()
                ? "Aucun exemple historique validé disponible."
                : learningExamples;

        return """
                Tu es un expert en estimation de sinistres d'assurance en Tunisie.

                Tu dois analyser les images du dossier et produire une estimation réaliste en dinars tunisiens (DT).

                CONTEXTE METIER
                Type de sinistre détecté : %s
                Décision du validateur : %s
                Justification du validateur : %s

                PLAFONDS / REFERENCES CONTRACTUELLES
                %s

                REGLES METIER OBLIGATOIRES
                %s

                EXEMPLES HISTORIQUES VALIDES PAR EXPERT
                %s

                DONNEES DU SINISTRE
                Description : %s
                Date de l'incident : %s

                INSTRUCTIONS OBLIGATOIRES
                - Analyse uniquement les dommages visibles sur les images
                - Tiens compte du type de sinistre et des plafonds indiqués
                - Utilise des montants réalistes pour le contexte tunisien (DT)
                - Inspire-toi des exemples historiques validés, sans les recopier mécaniquement
                - Ne mets jamais la même valeur pour min et max
                - Si les images sont floues, insuffisantes ou non pertinentes, baisse la confidence en dessous de %.2f
                - Si aucun dommage visible n'est identifiable, renvoie une estimation faible avec needsHumanReview=true
                - Précise dans l'analyse si c'est une perte totale ou une réparation (pour AUTO)
                - Ne jamais dépasser le plafond maximum indiqué

                FORMAT JSON STRICT
                {
                  "estimationMin": 0.0,
                  "estimationMax": 0.0,
                  "estimationMoyenne": 0.0,
                  "confidence": 0.0,
                  "analyse": "Description précise des dommages visibles",
                  "needsHumanReview": false
                }

                Contraintes :
                - Réponds uniquement avec du JSON valide, sans texte avant ou après
                - confidence doit être entre 0.0 et 1.0
                - estimationMin < estimationMoyenne < estimationMax
                - needsHumanReview = true si confidence < %.2f ou images insuffisantes
                """.formatted(
                typeDetecte,
                decisionValidateur,
                justificationValidation,
                plafonds,
                reglesMetier,
                memorySection,
                safe(claim.getDescription()),
                claim.getIncidentDate() != null ? claim.getIncidentDate() : "Non précisée",
                humanReviewThreshold,
                humanReviewThreshold
        );
    }

    private String extractClaimType(AgentResult routeResult) {
        if (routeResult == null || routeResult.getConclusion() == null) {
            return "INCONNU";
        }

        String conclusion = routeResult.getConclusion().trim();

        if (conclusion.startsWith("Type de sinistre classifié :")) {
            return conclusion.replace("Type de sinistre classifié :", "").trim();
        }

        return conclusion;
    }

    private String getPlafondsByType(String type) {
        if (type == null) {
            return "Plafond général : 50 000 DT";
        }

        String typeUpper = type.toUpperCase(Locale.ROOT);

        if (typeUpper.contains("AUTO")) {
            return """
                    - Franchise minimum obligatoire : 300 DT
                    - Dommages légers (rayures, pare-chocs, rétroviseur) : 300 - 2 000 DT
                    - Dommages modérés (carrosserie + pièces mécaniques) : 2 000 - 8 000 DT
                    - Dommages graves (structure endommagée, moteur exposé) : 8 000 - 18 000 DT
                    - Perte totale (véhicule irréparable) : valeur vénale estimée 10 000 - 25 000 DT
                    - Plafond maximum absolu : 25 000 DT pour un véhicule standard
                    - Vol / Incendie total : 20 000 DT max
                    """;
        }

        if (typeUpper.contains("SANTE")) {
            return """
                    - Soins courants / consultation : 50 - 300 DT
                    - Analyses médicales / radiologie : 100 - 800 DT
                    - Hospitalisation courte (1-3 nuits) : 500 - 4 000 DT
                    - Hospitalisation longue (> 3 nuits) : 4 000 - 15 000 DT
                    - Intervention chirurgicale légère : 1 500 - 5 000 DT
                    - Intervention chirurgicale lourde : 5 000 - 20 000 DT
                    - Médicaments prescrits : 50 - 500 DT
                    - Plafond annuel hospitalisation : 50 000 DT (remboursé à 90%)
                    """;
        }

        if (typeUpper.contains("HABITATION")) {
            return """
                    - Dégâts légers (fissures, infiltrations mineures) : 500 - 5 000 DT
                    - Dégâts modérés (dégâts des eaux, vol partiel) : 5 000 - 20 000 DT
                    - Sinistre grave (incendie partiel, effondrement) : 20 000 - 80 000 DT
                    - Destruction totale : valeur déclarée (max 100 000 DT)
                    - Franchise dégâts des eaux : 200 DT
                    - Vol par effraction : 20 000 DT max
                    """;
        }

        if (typeUpper.contains("VOYAGE")) {
            return """
                    - Retard vol (> 4h) : 100 - 500 DT
                    - Perte / vol bagages : 200 - 2 000 DT
                    - Annulation voyage : 500 - 5 000 DT
                    - Assistance médicale à l'étranger : 1 000 - 50 000 DT
                    - Rapatriement médical : 5 000 - 30 000 DT
                    """;
        }

        return "Plafond général : 50 000 DT";
    }

    private String getReglesMetierByType(String type) {
        if (type == null) {
            return "Appliquer une estimation prudente et demander une révision humaine.";
        }

        String typeUpper = type.toUpperCase(Locale.ROOT);

        if (typeUpper.contains("AUTO")) {
            return """
                    1. PERTE TOTALE : Si les dommages visibles dépassent 60%% de la structure du véhicule
                       Classer comme perte totale
                       estimation = valeur vénale estimée du véhicule (entre 10 000 et 25 000 DT selon l'état et l'âge)
                       Ne PAS estimer le coût de réparation dans ce cas
                    2. REPARATION POSSIBLE : Si les dommages sont localisés (avant, arrière, côté)
                       Estimer uniquement le coût de réparation (pièces + main d'oeuvre)
                       Appliquer la franchise de 300 DT minimum
                    3. JAMAIS dépasser 25 000 DT pour un véhicule particulier standard
                    4. Préciser dans l'analyse : "Perte totale probable" ou "Réparation possible"
                    """;
        }

        if (typeUpper.contains("SANTE")) {
            return """
                    1. Estimer uniquement les actes médicaux visibles dans les documents
                    2. Appliquer un taux de remboursement de 90%% sur l'hospitalisation
                    3. Les médicaments sont remboursés dans la limite de 500 DT/mois
                    4. Toujours déduire la franchise si applicable
                    """;
        }

        if (typeUpper.contains("HABITATION")) {
            return """
                    1. Estimer la valeur de remplacement à neuf des biens endommagés
                    2. Appliquer la franchise de 200 DT pour dégâts des eaux
                    3. Vérifier si le sinistre est couvert (incendie, vol, eau)
                    4. Ne pas dépasser la valeur déclarée du bien assuré
                    """;
        }

        if (typeUpper.contains("VOYAGE")) {
            return """
                    1. Vérifier la durée du retard ou la nature du sinistre
                    2. Appliquer les plafonds contractuels par catégorie
                    3. Les soins médicaux à l'étranger sont remboursés sur factures originales
                    """;
        }

        return "Appliquer une estimation prudente basée sur les dommages visibles.";
    }

    private AgentResult parseResponse(String raw, Claim claim, String typeDetecte, double humanReviewThreshold) {
        if (raw == null || raw.isBlank()) {
            return buildResult(
                    claim,
                    0.0,
                    0.0,
                    0.0,
                    0.0,
                    "Réponse vide du modèle de vision",
                    true,
                    raw
            );
        }

        try {
            String jsonOnly = extractJson(raw);
            log.info("JSON extrait : {}", jsonOnly);

            JsonNode node = objectMapper.readTree(jsonOnly);

            double estimationMin = normalizeMoney(node.path("estimationMin").asDouble(0.0));
            double estimationMax = normalizeMoney(node.path("estimationMax").asDouble(0.0));
            double estimationMoyenne = normalizeMoney(node.path("estimationMoyenne").asDouble(0.0));
            double confidence = normalizeConfidence(node.path("confidence").asDouble(DEFAULT_CONFIDENCE));
            String analyse = safe(node.path("analyse").asText("Analyse non fournie"));
            boolean needsHuman = node.path("needsHumanReview").asBoolean(false);

            if (estimationMin > estimationMax) {
                double temp = estimationMin;
                estimationMin = estimationMax;
                estimationMax = temp;
            }

            if (estimationMoyenne < estimationMin || estimationMoyenne > estimationMax) {
                estimationMoyenne = (estimationMin + estimationMax) / 2.0;
            }

            double maxAllowed = getMaxAllowedByType(typeDetecte);
            if (estimationMax > maxAllowed) {
                log.warn("Estimation trop élevée ({} DT) pour type={}, plafonnée à {} DT",
                        estimationMax, typeDetecte, maxAllowed);
                estimationMax = maxAllowed;
                estimationMoyenne = Math.min(estimationMoyenne, maxAllowed);
                estimationMin = Math.min(estimationMin, maxAllowed);
                needsHuman = true;
                analyse += " [Plafond contractuel appliqué : " + maxAllowed + " DT]";
            }

            if (confidence < humanReviewThreshold) {
                needsHuman = true;
            }

            if (analyse.isBlank()) {
                analyse = "Analyse non fournie";
                needsHuman = true;
            }

            log.info("Estimation finale dossier #{} | min={} DT | moy={} DT | max={} DT | confidence={} | seuil={}",
                    claim.getId(), estimationMin, estimationMoyenne, estimationMax, confidence, humanReviewThreshold);

            return buildResult(
                    claim,
                    estimationMin,
                    estimationMax,
                    estimationMoyenne,
                    confidence,
                    analyse,
                    needsHuman,
                    raw
            );

        } catch (Exception e) {
            log.error("Erreur parsing AgentEstimateur : {}", e.getMessage(), e);
            return buildResult(
                    claim,
                    0.0,
                    0.0,
                    0.0,
                    0.0,
                    "Erreur de parsing de la réponse vision : " + e.getMessage(),
                    true,
                    raw
            );
        }
    }

    private String extractJson(String raw) {
        String clean = raw
                .replaceAll("(?s)<think>.*?</think>", "")
                .replaceAll("```json", "")
                .replaceAll("```", "")
                .trim();

        int jsonStart = clean.indexOf("{");
        int jsonEnd = clean.lastIndexOf("}");

        if (jsonStart < 0 || jsonEnd <= jsonStart) {
            throw new IllegalArgumentException("Aucun JSON valide trouvé dans la réponse");
        }

        return clean.substring(jsonStart, jsonEnd + 1).trim();
    }

    private double getMaxAllowedByType(String type) {
        if (type == null) return 50000.0;

        String typeUpper = type.toUpperCase(Locale.ROOT);

        if (typeUpper.contains("AUTO"))       return 25000.0;
        if (typeUpper.contains("SANTE"))      return 50000.0;
        if (typeUpper.contains("HABITATION")) return 100000.0;
        if (typeUpper.contains("VOYAGE"))     return 50000.0;

        return 50000.0;
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

    private AgentResult buildResult(Claim claim,
                                    double estimationMin,
                                    double estimationMax,
                                    double estimationMoyenne,
                                    double confidence,
                                    String analyse,
                                    boolean needsHuman,
                                    String rawResponse) {

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

    private String buildConclusion(double estimationMin, double estimationMax, double estimationMoyenne) {
        return "Estimation min: %.2f DT | moyenne: %.2f DT | max: %.2f DT"
                .formatted(estimationMin, estimationMoyenne, estimationMax);
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}