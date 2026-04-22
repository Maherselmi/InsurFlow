package tn.esprit.insureflow_back.Agent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tn.esprit.insureflow_back.Domain.Entities.AgentResult;
import tn.esprit.insureflow_back.Domain.Entities.Claim;
import tn.esprit.insureflow_back.Service.LLMService;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class AgentRouteur {

    private final LLMService llmService;

    private static final String AGENT_NAME = "AgentRouteur";
    private static final double HUMAN_REVIEW_THRESHOLD = 0.70;
    private static final double DEFAULT_CONFIDENCE = 0.50;
    private static final String DEFAULT_TYPE = "INCONNU";

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

    public AgentResult classifier(Claim claim) {
        if (claim == null) {
            throw new IllegalArgumentException("Le claim ne doit pas être null");
        }

        log.info("🔄 {} — classification du dossier #{}", AGENT_NAME, claim.getId());

        String description = safeText(claim.getDescription());
        if (description.isBlank()) {
            log.warn("⚠️ Description vide pour le dossier #{}", claim.getId());
            return buildResult(claim, DEFAULT_TYPE, DEFAULT_CONFIDENCE,
                    true, "Description vide ou absente", null);
        }

        String prompt = buildPrompt(description);
        String rawResponse = callLlmSafely(prompt);

        ParsedClassification parsed = parseResponse(rawResponse);

        boolean needsHumanReview =
                parsed.type().equals(DEFAULT_TYPE) ||
                        parsed.confidence() < HUMAN_REVIEW_THRESHOLD;

        log.info("✅ Résultat classification dossier #{} | type={} | confidence={} | humanReview={}",
                claim.getId(), parsed.type(), parsed.confidence(), needsHumanReview);

        return buildResult(
                claim,
                parsed.type(),
                parsed.confidence(),
                needsHumanReview,
                parsed.justification(),
                rawResponse
        );
    }



    private String buildPrompt(String description) {
        return """
        Tu es un agent expert en classification de sinistres d’assurance.

        Ta mission :
        analyser la description du sinistre et retourner UN SEUL type parmi :
        - AUTO
        - HABITATION
        - SANTE
        - VOYAGE

        Définitions :
        - AUTO : dommages matériels au véhicule, collision, carrosserie, bris de glace,
                 vol de véhicule, rayures, accident de voiture
        - HABITATION : incendie maison, dégât des eaux, fuite canalisation,
                       cambriolage, dommage logement
        - SANTE : hospitalisation, maladie, fracture, opération chirurgicale,
                  soins, frais médicaux
        - VOYAGE : annulation voyage, perte de bagages, assistance à l’étranger,
                   incident pendant déplacement

        Règles obligatoires :
        - Retourne uniquement un JSON valide
        - N’ajoute aucun texte avant ou après le JSON
        - Le champ "type" doit être exactement l’un des 4 types autorisés
        - Le champ "confidence" doit être un nombre entre 0.0 et 1.0
        - Le champ "justification" doit être court

        Description du sinistre :
        "%s"

        Format JSON strict attendu :
        {
          "type": "AUTO|HABITATION|SANTE|VOYAGE",
          "confidence": 0.0,
          "justification": "..."
        }
        """.formatted(escapeForPrompt(description));
    }

    // ─────────────────────────────────────────────────────────────
    // LLM
    // ─────────────────────────────────────────────────────────────

    private String callLlmSafely(String prompt) {
        try {
            String response = llmService.genererReponse(prompt);
            log.info("📥 Réponse brute LLM: {}", response);
            return response == null ? "" : response.trim();
        } catch (Exception e) {
            log.error("❌ Erreur lors de l'appel au LLM", e);
            return "";
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Parsing
    // ─────────────────────────────────────────────────────────────

    private ParsedClassification parseResponse(String rawResponse) {
        if (rawResponse == null || rawResponse.isBlank()) {
            log.warn("⚠️ Réponse LLM vide");
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
                    Math.min(confidence, fallback.confidence()),
                    fallback.justification()
            );
        }

        return new ParsedClassification(type, confidence, justification);
    }

    private String extractJsonBlock(String response) {
        int firstBrace = response.indexOf('{');
        int lastBrace = response.lastIndexOf('}');

        if (firstBrace >= 0 && lastBrace > firstBrace) {
            return response.substring(firstBrace, lastBrace + 1).trim();
        }

        return response.trim();
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
            log.warn("⚠️ Erreur parsing type", e);
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
            log.warn("⚠️ Erreur parsing confidence", e);
        }

        return DEFAULT_CONFIDENCE;
    }

    private String parseJustification(String response) {
        try {
            Pattern justificationPattern =
                    Pattern.compile("\"justification\"\\s*:\\s*\"(.*?)\"", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
            Matcher matcher = justificationPattern.matcher(response);
            if (matcher.find()) {
                return matcher.group(1).trim();
            }
        } catch (Exception e) {
            log.warn("⚠️ Erreur parsing justification", e);
        }

        return "Justification indisponible";
    }

    // ─────────────────────────────────────────────────────────────
    // Fallback métier
    // ─────────────────────────────────────────────────────────────

    private ParsedClassification fallbackClassification(String text, String reason) {
        String lower = normalize(text);

        if (containsAny(lower, "voiture", "vehicule", "véhicule", "collision", "carrosserie", "bris de glace", "accident", "auto")) {
            return new ParsedClassification("AUTO", 0.55, reason + " — fallback mots-clés AUTO");
        }

        if (containsAny(lower, "maison", "habitation", "incendie", "degat des eaux", "dégât des eaux", "fuite", "canalisation", "cambriolage", "logement")) {
            return new ParsedClassification("HABITATION", 0.55, reason + " — fallback mots-clés HABITATION");
        }

        if (containsAny(lower, "hopital", "hôpital", "hospitalisation", "maladie", "medecin", "médecin", "fracture", "operation", "opération", "frais medicaux", "frais médicaux", "sante", "santé")) {
            return new ParsedClassification("SANTE", 0.55, reason + " — fallback mots-clés SANTE");
        }

        if (containsAny(lower, "voyage", "bagage", "bagages", "annulation", "etranger", "étranger", "deplacement", "déplacement")) {
            return new ParsedClassification("VOYAGE", 0.55, reason + " — fallback mots-clés VOYAGE");
        }

        log.warn("⚠️ Aucun type fiable détecté via JSON ou fallback");
        return new ParsedClassification(DEFAULT_TYPE, DEFAULT_CONFIDENCE, reason + " — aucun type fiable détecté");
    }

    // ─────────────────────────────────────────────────────────────
    // Construction résultat
    // ─────────────────────────────────────────────────────────────

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

    // ─────────────────────────────────────────────────────────────
    // Utils
    // ─────────────────────────────────────────────────────────────

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
        return text == null ? "" : text.toLowerCase(Locale.ROOT).trim();
    }

    private record ParsedClassification(String type, double confidence, String justification) {}
}