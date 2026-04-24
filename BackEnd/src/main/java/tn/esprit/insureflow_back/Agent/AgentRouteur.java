package tn.esprit.insureflow_back.Agent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tn.esprit.insureflow_back.Domain.ENUMS.AgentName;
import tn.esprit.insureflow_back.Domain.Entities.AgentResult;
import tn.esprit.insureflow_back.Domain.Entities.Claim;
import tn.esprit.insureflow_back.Service.AgentLearningMemoryService;
import tn.esprit.insureflow_back.Service.AiAgentConfigService;
import tn.esprit.insureflow_back.Service.LLMService;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class AgentRouteur {

    private final LLMService llmService;
    private final AiAgentConfigService aiAgentConfigService;
    private final AgentLearningMemoryService learningMemoryService;

    private static final String AGENT_NAME = "AgentRouteur";
    private static final String CONFIG_KEY = "AGENT_ROUTEUR";

    private static final double DEFAULT_CONFIDENCE = 0.50;
    private static final String DEFAULT_TYPE = "INCONNU";

    private static final double FAST_PATH_CONFIDENCE = 0.92;
    private static final int MAX_LEARNING_CHARS = 400;
    private static final int MAX_DESCRIPTION_CHARS = 700;

    private static final Set<String> ALLOWED_TYPES = Set.of(
            "AUTO",
            "HABITATION",
            "SANTE",
            "VOYAGE"
    );

    private static final Pattern TYPE_PATTERN =
            Pattern.compile("\"type\"\\s*:\\s*\"(AUTO|HABITATION|SANTE|VOYAGE)\"", Pattern.CASE_INSENSITIVE);

    private static final Pattern CONFIDENCE_PATTERN =
            Pattern.compile("\"confidence\"\\s*:\\s*([0-9]*\\.?[0-9]+)");

    private static final Pattern JUSTIFICATION_PATTERN =
            Pattern.compile("\"justification\"\\s*:\\s*\"(.*?)\"", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    public AgentResult classifier(Claim claim) {
        if (claim == null) {
            throw new IllegalArgumentException("Le claim ne doit pas être null");
        }

        log.info("{} - classification du dossier #{}", AGENT_NAME, claim.getId());

        String description = safeText(claim.getDescription());
        if (description.isBlank()) {
            log.warn("Description vide pour le dossier #{}", claim.getId());
            return buildResult(
                    claim,
                    DEFAULT_TYPE,
                    DEFAULT_CONFIDENCE,
                    true,
                    "Description vide ou absente",
                    buildRawJson(DEFAULT_TYPE, DEFAULT_CONFIDENCE, "Description vide ou absente")
            );
        }

        double humanReviewThreshold = aiAgentConfigService.getThreshold(CONFIG_KEY);
        log.info("Seuil de confiance dynamique pour {} = {}", CONFIG_KEY, humanReviewThreshold);

        ParsedClassification fastResult = fastPath(description);
        if (fastResult != null) {
            boolean needsHumanReview = fastResult.confidence() < humanReviewThreshold;

            log.info(
                    "Fast-path routeur pour dossier #{} | type={} | confidence={} | threshold={} | humanReview={}",
                    claim.getId(),
                    fastResult.type(),
                    fastResult.confidence(),
                    humanReviewThreshold,
                    needsHumanReview
            );

            return buildResult(
                    claim,
                    fastResult.type(),
                    fastResult.confidence(),
                    needsHumanReview,
                    fastResult.justification(),
                    buildRawJson(fastResult.type(), fastResult.confidence(), fastResult.justification())
            );
        }

        String learningExamples =
                learningMemoryService.buildMemoryBlock(AgentName.AGENT_ROUTEUR, claim.getId());

        if (learningExamples == null || learningExamples.isBlank()) {
            log.info("Aucun exemple learning disponible pour {}", AGENT_NAME);
            learningExamples = "";
        } else {
            learningExamples = truncate(learningExamples, MAX_LEARNING_CHARS);
            log.info("Exemples learning chargés pour {} (tronqués à {} caractères)", AGENT_NAME, MAX_LEARNING_CHARS);
        }

        String prompt = buildPrompt(description, learningExamples);
        String rawResponse = callLlmSafely(prompt);

        ParsedClassification parsed = parseResponse(rawResponse);

        boolean needsHumanReview =
                parsed.type().equals(DEFAULT_TYPE) ||
                        parsed.confidence() < humanReviewThreshold;

        log.info(
                "Résultat classification dossier #{} | type={} | confidence={} | threshold={} | humanReview={}",
                claim.getId(),
                parsed.type(),
                parsed.confidence(),
                humanReviewThreshold,
                needsHumanReview
        );

        return buildResult(
                claim,
                parsed.type(),
                parsed.confidence(),
                needsHumanReview,
                parsed.justification(),
                rawResponse
        );
    }

    private ParsedClassification fastPath(String description) {
        String lower = normalize(description);

        if (containsAny(lower,
                "voiture", "vehicule", "vehicules", "vehicle", "collision", "carrosserie",
                "pare brise", "parebrise", "bris de glace", "retro", "retroviseur",
                "accident", "auto", "immatriculation")) {
            return new ParsedClassification("AUTO", FAST_PATH_CONFIDENCE, "Classification rapide par mots-clés AUTO");
        }

        if (containsAny(lower,
                "maison", "habitation", "logement", "incendie", "fuite", "canalisation",
                "degat des eaux", "degats des eaux", "cambriolage", "effraction",
                "inondation", "vandalism", "vandalisme")) {
            return new ParsedClassification("HABITATION", FAST_PATH_CONFIDENCE, "Classification rapide par mots-clés HABITATION");
        }

        if (containsAny(lower,
                "hopital", "hospitalisation", "maladie", "medecin", "fracture",
                "operation", "soins", "frais medicaux", "consultation",
                "scanner", "radio", "ordonnance", "medicament", "sante")) {
            return new ParsedClassification("SANTE", FAST_PATH_CONFIDENCE, "Classification rapide par mots-clés SANTE");
        }

        if (containsAny(lower,
                "voyage", "bagage", "bagages", "annulation", "etranger",
                "deplacement", "retard vol", "retard avion", "rapatriement")) {
            return new ParsedClassification("VOYAGE", FAST_PATH_CONFIDENCE, "Classification rapide par mots-clés VOYAGE");
        }

        return null;
    }

    private String buildPrompt(String description, String learningExamples) {
        String memorySection = learningExamples == null || learningExamples.isBlank()
                ? "Aucun exemple historique validé disponible."
                : learningExamples;

        return """
                Tu classes un sinistre d’assurance en un seul type parmi :
                AUTO, HABITATION, SANTE, VOYAGE.

                Réponds uniquement en JSON valide :
                {
                  "type": "AUTO|HABITATION|SANTE|VOYAGE",
                  "confidence": 0.0,
                  "justification": "courte justification"
                }

                Règles :
                - un seul type
                - confidence entre 0.0 et 1.0
                - justification courte
                - aucun texte avant ou après le JSON

                Exemples validés :
                %s

                Description :
                "%s"
                """.formatted(memorySection, escapeForPrompt(truncate(description, MAX_DESCRIPTION_CHARS)));
    }

    private String callLlmSafely(String prompt) {
        try {
            String response = llmService.genererReponse(prompt);
            log.info("Réponse brute LLM routeur: {}", response);
            return response == null ? "" : response.trim();
        } catch (Exception e) {
            log.error("Erreur lors de l'appel au LLM", e);
            return "";
        }
    }

    private ParsedClassification parseResponse(String rawResponse) {
        if (rawResponse == null || rawResponse.isBlank()) {
            log.warn("Réponse LLM vide");
            return fallbackClassification("", "Réponse LLM vide");
        }

        String normalizedResponse = extractJsonBlock(rawResponse);

        String type = parseType(normalizedResponse);
        double confidence = parseConfidence(normalizedResponse);
        String justification = parseJustification(normalizedResponse);

        if (DEFAULT_TYPE.equals(type)) {
            ParsedClassification fallback = fallbackClassification(rawResponse, "Type non parsé depuis le JSON");
            return new ParsedClassification(
                    fallback.type(),
                    Math.min(Math.max(confidence, DEFAULT_CONFIDENCE), fallback.confidence()),
                    fallback.justification()
            );
        }

        return new ParsedClassification(type, confidence, justification);
    }

    private String extractJsonBlock(String response) {
        if (response == null || response.isBlank()) {
            return "";
        }

        String cleaned = response
                .replaceAll("(?s)<think>.*?</think>", "")
                .replace("```json", "")
                .replace("```", "")
                .trim();

        int firstBrace = cleaned.indexOf('{');
        int lastBrace = cleaned.lastIndexOf('}');

        if (firstBrace >= 0 && lastBrace > firstBrace) {
            return cleaned.substring(firstBrace, lastBrace + 1).trim();
        }

        return cleaned;
    }

    private String parseType(String response) {
        try {
            Matcher matcher = TYPE_PATTERN.matcher(response);
            if (matcher.find()) {
                String type = matcher.group(1).toUpperCase(Locale.ROOT);
                if (ALLOWED_TYPES.contains(type)) {
                    return type;
                }
            }
        } catch (Exception e) {
            log.warn("Erreur parsing type", e);
        }

        return DEFAULT_TYPE;
    }

    private double parseConfidence(String response) {
        try {
            Matcher matcher = CONFIDENCE_PATTERN.matcher(response);
            if (matcher.find()) {
                double value = Double.parseDouble(matcher.group(1));
                return clamp(value, 0.0, 1.0);
            }
        } catch (Exception e) {
            log.warn("Erreur parsing confidence", e);
        }

        return DEFAULT_CONFIDENCE;
    }

    private String parseJustification(String response) {
        try {
            Matcher matcher = JUSTIFICATION_PATTERN.matcher(response);
            if (matcher.find()) {
                String justification = matcher.group(1).trim();
                return justification.isBlank() ? "Justification indisponible" : justification;
            }
        } catch (Exception e) {
            log.warn("Erreur parsing justification", e);
        }

        return "Justification indisponible";
    }

    private ParsedClassification fallbackClassification(String text, String reason) {
        String lower = normalize(text);

        if (containsAny(lower, "voiture", "vehicule", "collision", "carrosserie", "bris de glace", "accident", "auto")) {
            return new ParsedClassification("AUTO", 0.55, reason + " - fallback mots-clés AUTO");
        }

        if (containsAny(lower, "maison", "habitation", "incendie", "degat des eaux", "fuite", "canalisation", "cambriolage", "logement")) {
            return new ParsedClassification("HABITATION", 0.55, reason + " - fallback mots-clés HABITATION");
        }

        if (containsAny(lower, "hopital", "hospitalisation", "maladie", "medecin", "fracture", "operation", "frais medicaux", "sante")) {
            return new ParsedClassification("SANTE", 0.55, reason + " - fallback mots-clés SANTE");
        }

        if (containsAny(lower, "voyage", "bagage", "bagages", "annulation", "etranger", "deplacement")) {
            return new ParsedClassification("VOYAGE", 0.55, reason + " - fallback mots-clés VOYAGE");
        }

        log.warn("Aucun type fiable détecté via JSON ou fallback");
        return new ParsedClassification(DEFAULT_TYPE, DEFAULT_CONFIDENCE, reason + " - aucun type fiable détecté");
    }

    private AgentResult buildResult(
            Claim claim,
            String type,
            double confidence,
            boolean needsHumanReview,
            String justification,
            String rawResponse
    ) {
        return AgentResult.builder()
                .agentName(AGENT_NAME)
                .conclusion(buildConclusion(type, justification))
                .confidenceScore(confidence)
                .needsHumanReview(needsHumanReview)
                .rawLlmResponse(rawResponse)
                .claim(claim)
                .build();
    }

    private String buildConclusion(String type, String justification) {
        return "Type de sinistre classifié : " + type + " | Justification : " + justification;
    }

    private String buildRawJson(String type, double confidence, String justification) {
        String safeJustification = justification == null ? "" : justification.replace("\"", "\\\"");
        return """
                {
                  "type": "%s",
                  "confidence": %.2f,
                  "justification": "%s"
                }
                """.formatted(type, confidence, safeJustification).trim();
    }

    private String safeText(String value) {
        return value == null ? "" : value.trim();
    }

    private String escapeForPrompt(String text) {
        return text.replace("\"", "\\\"");
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(normalize(keyword))) {
                return true;
            }
        }
        return false;
    }

    private String normalize(String text) {
        if (text == null) {
            return "";
        }

        String normalized = Normalizer.normalize(text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .trim();

        return normalized.replaceAll("\\s+", " ");
    }

    private String truncate(String text, int maxLength) {
        String value = safeText(text);
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private record ParsedClassification(String type, double confidence, String justification) {}
}