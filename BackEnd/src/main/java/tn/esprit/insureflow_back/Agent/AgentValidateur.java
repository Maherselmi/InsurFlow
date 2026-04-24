package tn.esprit.insureflow_back.Agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingStore;
import io.grpc.StatusRuntimeException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tn.esprit.insureflow_back.Domain.ENUMS.AgentName;
import tn.esprit.insureflow_back.Domain.Entities.AgentResult;
import tn.esprit.insureflow_back.Domain.Entities.Claim;
import tn.esprit.insureflow_back.Domain.Entities.Policy;
import tn.esprit.insureflow_back.Service.AgentLearningMemoryService;
import tn.esprit.insureflow_back.Service.AiAgentConfigService;
import tn.esprit.insureflow_back.Service.LLMService;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class AgentValidateur {

    private static final String AGENT_NAME = "AgentValidateur";
    private static final String CONFIG_KEY = "AGENT_VALIDATION";

    private static final int MAX_PDF_SNIPPET_LENGTH = 600;
    private static final int MAX_RAG_MATCHES = 2;
    private static final int MAX_RAG_CANDIDATES = 8;
    private static final double MIN_RAG_SCORE = 0.72;

    private static final int MAX_RAG_RETRIES = 2;
    private static final long RETRY_DELAY_MS = 250L;

    private static final int MAX_LEARNING_CHARS = 600;
    private static final int MAX_CONTRACT_SEGMENT_CHARS = 500;

    private static final double DEFAULT_CONFIDENCE = 0.50;

    private static final String DECISION_COUVERT = "COUVERT";
    private static final String DECISION_EXCLU = "EXCLU";
    private static final String DECISION_INCONNU = "INCONNU";

    private static final Set<String> ALLOWED_DECISIONS =
            Set.of(DECISION_COUVERT, DECISION_EXCLU, DECISION_INCONNU);

    private final EmbeddingStore<TextSegment> embeddingStore;
    private final EmbeddingModel embeddingModel;
    private final LLMService llmService;
    private final ObjectMapper objectMapper;
    private final AiAgentConfigService aiAgentConfigService;
    private final AgentLearningMemoryService learningMemoryService;

    public AgentResult validate(Claim claim, String claimPdfText, String routedType) {
        if (claim == null) {
            throw new IllegalArgumentException("Le claim ne doit pas être null");
        }

        log.info("{} - validation du sinistre #{}", AGENT_NAME, claim.getId());

        String description = safe(claim.getDescription());
        String pdfText = safe(claimPdfText);

        if (description.isBlank() && pdfText.isBlank()) {
            log.warn("Dossier #{} sans description ni contenu PDF exploitable", claim.getId());
            return buildResult(
                    claim,
                    DECISION_INCONNU,
                    0.0,
                    "Aucune donnée exploitable pour analyser le sinistre",
                    true,
                    buildRawJson(DECISION_INCONNU, 0.0, "Aucune donnée exploitable pour analyser le sinistre", true)
            );
        }

        PolicyCheckResult preCheck = preCheckPolicy(claim, routedType);
        if (preCheck != null) {
            log.info("Pré-contrôle police pour claim #{} => {}", claim.getId(), preCheck.justification());
            return buildResult(
                    claim,
                    preCheck.decision(),
                    preCheck.confidence(),
                    preCheck.justification(),
                    preCheck.needsHumanReview(),
                    buildRawJson(
                            preCheck.decision(),
                            preCheck.confidence(),
                            preCheck.justification(),
                            preCheck.needsHumanReview()
                    )
            );
        }

        double humanReviewThreshold = aiAgentConfigService.getThreshold(CONFIG_KEY);
        log.info("Seuil de confiance dynamique pour {} = {}", CONFIG_KEY, humanReviewThreshold);

        String typeContrat = resolveTypeContrat(claim, pdfText, routedType);
        String policyContext = buildPolicyContext(claim);

        log.info("Type contrat retenu pour claim #{} : {} (routeur={})",
                claim.getId(), typeContrat, safe(routedType));

        String searchQuery = buildSearchQuery(claim, typeContrat);
        log.info("Requête RAG compacte : {}", searchQuery);

        List<EmbeddingMatch<TextSegment>> matches = searchRelevantClauses(searchQuery, claim, typeContrat);

        if (matches.isEmpty()) {
            log.warn("Aucune clause pertinente trouvée pour claim #{} | type={}", claim.getId(), typeContrat);
            return buildResult(
                    claim,
                    DECISION_INCONNU,
                    0.0,
                    "Validation contractuelle impossible automatiquement : aucune clause pertinente retrouvée pour ce type de contrat",
                    true,
                    buildRawJson(
                            DECISION_INCONNU,
                            0.0,
                            "Validation contractuelle impossible automatiquement : aucune clause pertinente retrouvée pour ce type de contrat",
                            true
                    )
            );
        }

        log.info("{} clause(s) pertinente(s) trouvée(s)", matches.size());

        String contractContext = buildContractContext(matches);

        String learningExamples =
                learningMemoryService.buildMemoryBlock(AgentName.AGENT_VALIDATION, claim.getId());

        if (learningExamples == null || learningExamples.isBlank()) {
            log.info("Aucun exemple learning disponible pour {}", AGENT_NAME);
            learningExamples = "";
        } else {
            learningExamples = truncate(learningExamples, MAX_LEARNING_CHARS);
            log.info("Exemples learning chargés pour {} (tronqués à {} caractères)", AGENT_NAME, MAX_LEARNING_CHARS);
        }

        String prompt = buildPrompt(
                claim,
                pdfText,
                contractContext,
                typeContrat,
                policyContext,
                learningExamples
        );

        log.info("Envoi du prompt optimisé au LLM...");
        String rawResponse = callLlmSafely(prompt);
        log.info("Réponse brute LLM : {}", rawResponse);

        return parseResponse(rawResponse, claim, humanReviewThreshold);
    }

    private PolicyCheckResult preCheckPolicy(Claim claim, String routedType) {
        Policy policy = claim.getPolicy();

        if (policy == null) {
            return new PolicyCheckResult(
                    DECISION_INCONNU,
                    0.0,
                    "Aucune police souscrite associée au dossier",
                    true
            );
        }

        String routeType = normalizeType(routedType);
        String policyType = normalizeType(policy.getType());

        if (!DECISION_INCONNU.equals(routeType)
                && !DECISION_INCONNU.equals(policyType)
                && !routeType.equals(policyType)) {
            return new PolicyCheckResult(
                    DECISION_INCONNU,
                    0.40,
                    "Incohérence entre le type détecté par le routeur et le type de la police souscrite",
                    true
            );
        }

        LocalDate incidentDate = claim.getIncidentDate();
        if (incidentDate != null) {
            if (policy.getStartDate() != null && incidentDate.isBefore(policy.getStartDate())) {
                return new PolicyCheckResult(
                        DECISION_EXCLU,
                        0.98,
                        "Le sinistre est antérieur à la date de début de validité de la police",
                        false
                );
            }

            if (policy.getEndDate() != null && incidentDate.isAfter(policy.getEndDate())) {
                return new PolicyCheckResult(
                        DECISION_EXCLU,
                        0.98,
                        "Le sinistre est postérieur à la date de fin de validité de la police",
                        false
                );
            }
        }

        return null;
    }

    private List<EmbeddingMatch<TextSegment>> searchRelevantClauses(String searchQuery, Claim claim, String expectedTypeContrat) {
        for (int attempt = 1; attempt <= MAX_RAG_RETRIES; attempt++) {
            try {
                log.info("Recherche RAG tentative {}/{} pour claim #{} | type={}",
                        attempt, MAX_RAG_RETRIES, claim.getId(), expectedTypeContrat);

                Embedding queryEmbedding = embeddingModel.embed(searchQuery).content();

                List<EmbeddingMatch<TextSegment>> candidates =
                        embeddingStore.findRelevant(queryEmbedding, MAX_RAG_CANDIDATES, MIN_RAG_SCORE);

                List<EmbeddingMatch<TextSegment>> filtered = filterByContractType(candidates, expectedTypeContrat);

                if (filtered.isEmpty()) {
                    filtered = candidates.stream()
                            .sorted(Comparator.comparingDouble(EmbeddingMatch<TextSegment>::score).reversed())
                            .limit(MAX_RAG_MATCHES)
                            .collect(Collectors.toList());
                }

                log.info("Recherche RAG réussie pour claim #{} à la tentative {} | candidats={} | filtrés={}",
                        claim.getId(), attempt, candidates.size(), filtered.size());

                return filtered.stream()
                        .sorted(Comparator.comparingDouble(EmbeddingMatch<TextSegment>::score).reversed())
                        .limit(MAX_RAG_MATCHES)
                        .collect(Collectors.toList());

            } catch (StatusRuntimeException e) {
                log.warn("Erreur gRPC Milvus tentative {}/{} pour claim #{} : {}",
                        attempt, MAX_RAG_RETRIES, claim.getId(), e.getMessage());

            } catch (Exception e) {
                log.warn("Erreur RAG tentative {}/{} pour claim #{} : {}",
                        attempt, MAX_RAG_RETRIES, claim.getId(), e.getMessage());
            }

            if (attempt < MAX_RAG_RETRIES) {
                try {
                    Thread.sleep(RETRY_DELAY_MS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    log.error("Thread interrompu pendant le retry RAG pour claim #{}", claim.getId(), ie);
                    return List.of();
                }
            }
        }

        log.error("Toutes les tentatives Milvus ont échoué pour claim #{}", claim.getId());
        return List.of();
    }

    private List<EmbeddingMatch<TextSegment>> filterByContractType(List<EmbeddingMatch<TextSegment>> candidates, String expectedTypeContrat) {
        if (DECISION_INCONNU.equals(expectedTypeContrat)) {
            return candidates;
        }

        List<EmbeddingMatch<TextSegment>> filtered = new ArrayList<>();

        for (EmbeddingMatch<TextSegment> match : candidates) {
            TextSegment segment = match.embedded();
            if (segment == null) {
                continue;
            }

            String typeContrat = extractMetadata(segment, "typeContrat");
            if (expectedTypeContrat.equalsIgnoreCase(typeContrat)) {
                filtered.add(match);
            }
        }

        return filtered;
    }

    private String buildSearchQuery(Claim claim, String typeContrat) {
        Policy policy = claim.getPolicy();

        String description = truncate(safe(claim.getDescription()), 250);
        String formule = policy != null ? safe(policy.getFormule()) : "";
        String productCode = policy != null ? safe(policy.getProductCode()) : "";
        String coverageDetails = policy != null ? truncate(safe(policy.getCoverageDetails()), 200) : "";

        return """
                typeContrat=%s
                formule=%s
                productCode=%s
                couverture=%s
                sinistre=%s
                recherche=garanties exclusions plafonds franchise indemnisation
                """.formatted(
                typeContrat,
                formule,
                productCode,
                coverageDetails,
                description
        );
    }

    private String buildContractContext(List<EmbeddingMatch<TextSegment>> matches) {
        return matches.stream()
                .sorted(Comparator.comparingDouble(EmbeddingMatch<TextSegment>::score).reversed())
                .limit(MAX_RAG_MATCHES)
                .map(match -> {
                    TextSegment segment = match.embedded();
                    String text = segment != null ? truncate(safe(segment.text()), MAX_CONTRACT_SEGMENT_CHARS) : "";
                    String file = segment != null ? extractMetadata(segment, "file") : "";
                    String typeContrat = segment != null ? extractMetadata(segment, "typeContrat") : "";
                    String pageNumber = segment != null ? extractMetadata(segment, "pageNumber") : "";
                    double score = match.score();

                    return """
                            Score : %.3f
                            Fichier : %s
                            Type : %s
                            Page : %s
                            Texte : %s
                            """.formatted(score, safe(file), safe(typeContrat), safe(pageNumber), text);
                })
                .collect(Collectors.joining("\n\n"));
    }

    private String buildPolicyContext(Claim claim) {
        Policy policy = claim.getPolicy();
        if (policy == null) {
            return "Police non disponible";
        }

        return """
                Numéro : %s
                Type : %s
                Formule : %s
                Product code : %s
                Début : %s
                Fin : %s
                Couverture : %s
                """.formatted(
                safe(policy.getPolicyNumber()),
                safe(policy.getType()),
                safe(policy.getFormule()),
                safe(policy.getProductCode()),
                policy.getStartDate() != null ? policy.getStartDate().toString() : "Non précisée",
                policy.getEndDate() != null ? policy.getEndDate().toString() : "Non précisée",
                truncate(safe(policy.getCoverageDetails()), 300)
        );
    }

    private String buildPrompt(Claim claim,
                               String claimPdfText,
                               String contractContext,
                               String typeContrat,
                               String policyContext,
                               String learningExamples) {

        String description = truncate(safe(claim.getDescription()), 400);
        String incidentDate = claim.getIncidentDate() != null ? claim.getIncidentDate().toString() : "Non précisée";
        String pdfSnippet = truncate(claimPdfText, MAX_PDF_SNIPPET_LENGTH);

        String memorySection = learningExamples == null || learningExamples.isBlank()
                ? "Aucun exemple historique validé disponible."
                : learningExamples;

        return """
                Tu es un expert juriste en assurance %s.

                Tu dois décider si le sinistre déclaré est :
                - COUVERT
                - EXCLU
                - INCONNU

                Base obligatoire :
                1. police souscrite
                2. description du sinistre
                3. extrait du document fourni
                4. clauses contractuelles RAG
                5. exemples historiques validés

                POLICE
                %s

                SINISTRE
                Description : %s
                Date : %s

                EXTRAIT DOCUMENT
                %s

                CLAUSES RAG
                %s

                EXEMPLES VALIDÉS
                %s

                Règles :
                - COUVERT si la police et les clauses montrent clairement la garantie
                - EXCLU si la police ou les clauses montrent clairement l’exclusion
                - INCONNU si information insuffisante, ambiguë ou non concluante
                - needsHumanReview=true pour toute décision INCONNU
                - justification courte et contractuelle

                Réponds uniquement en JSON valide :
                {
                  "decision": "COUVERT | EXCLU | INCONNU",
                  "confidence": 0.0,
                  "justification": "justification courte",
                  "needsHumanReview": false
                }
                """.formatted(
                typeContrat,
                policyContext,
                description,
                incidentDate,
                pdfSnippet,
                contractContext,
                memorySection
        );
    }

    private String callLlmSafely(String prompt) {
        try {
            String response = llmService.genererReponse(prompt);
            return response == null ? "" : response.trim();
        } catch (Exception e) {
            log.error("Erreur lors de l'appel au LLM", e);
            return "";
        }
    }

    private AgentResult parseResponse(String raw, Claim claim, double humanReviewThreshold) {
        if (raw == null || raw.isBlank()) {
            log.warn("Réponse LLM vide");
            return buildResult(
                    claim,
                    DECISION_INCONNU,
                    0.0,
                    "Réponse LLM vide",
                    true,
                    buildRawJson(DECISION_INCONNU, 0.0, "Réponse LLM vide", true)
            );
        }

        try {
            String cleanJson = extractJson(raw);
            log.info("JSON extrait : {}", cleanJson);

            JsonNode node = objectMapper.readTree(cleanJson);

            String decision = normalizeDecision(node.path("decision").asText(""));
            double confidence = normalizeConfidence(node.path("confidence").asDouble(DEFAULT_CONFIDENCE));
            String justification = safe(node.path("justification").asText("Justification non fournie"));
            boolean needsHumanReview = node.path("needsHumanReview").asBoolean(false);

            if (!ALLOWED_DECISIONS.contains(decision)) {
                log.warn("Décision invalide reçue : {}", decision);
                return fallbackFromText(raw, claim, "Décision invalide renvoyée par le LLM");
            }

            if (DECISION_INCONNU.equals(decision)) {
                needsHumanReview = true;
            }

            if (confidence < humanReviewThreshold) {
                needsHumanReview = true;
            }

            if (justification.isBlank()) {
                justification = "Justification non fournie";
                needsHumanReview = true;
            }

            log.info("Décision finale : {} | confiance : {} | seuil={} | review humain : {}",
                    decision, confidence, humanReviewThreshold, needsHumanReview);

            return buildResult(
                    claim,
                    decision,
                    confidence,
                    justification,
                    needsHumanReview,
                    raw
            );

        } catch (Exception e) {
            log.error("Erreur parsing LLM", e);
            return fallbackFromText(raw, claim, "Erreur technique lors du parsing JSON");
        }
    }

    private String extractJson(String raw) {
        String clean = raw
                .replaceAll("(?s)<think>.*?</think>", "")
                .replaceAll("```json", "")
                .replaceAll("```", "")
                .trim();

        int jsonStart = clean.indexOf('{');
        int jsonEnd = clean.lastIndexOf('}');

        if (jsonStart < 0 || jsonEnd <= jsonStart) {
            throw new IllegalArgumentException("Aucun JSON valide trouvé dans la réponse LLM");
        }

        return clean.substring(jsonStart, jsonEnd + 1).trim();
    }

    private AgentResult fallbackFromText(String raw, Claim claim, String reason) {
        String lower = safe(raw).toLowerCase(Locale.ROOT);

        String decision;
        if (containsAny(lower, "inconnu", "ambigu", "insuffisant", "manquant", "incertain", "doute")) {
            decision = DECISION_INCONNU;
        } else if (containsAny(lower, "couvert", "garanti", "pris en charge", "prise en charge", "indemnisation accordee", "indemnisation accordée")) {
            decision = DECISION_COUVERT;
        } else {
            decision = DECISION_EXCLU;
        }

        boolean needsHumanReview = !DECISION_COUVERT.equals(decision);

        return buildResult(
                claim,
                decision,
                0.40,
                reason + " - décision déduite via fallback sécurisé",
                needsHumanReview,
                buildRawJson(decision, 0.40, reason + " - décision déduite via fallback sécurisé", needsHumanReview)
        );
    }

    private AgentResult buildResult(
            Claim claim,
            String decision,
            double confidence,
            String justification,
            boolean needsHuman,
            String rawResponse
    ) {
        AgentResult result = new AgentResult();
        result.setAgentName(AGENT_NAME);
        result.setClaim(claim);
        result.setConclusion(decision);
        result.setConfidenceScore(confidence);
        result.setRawLlmResponse(rawResponse != null ? rawResponse : justification);
        result.setNeedsHumanReview(needsHuman);
        result.setCreatedAt(LocalDateTime.now());
        return result;
    }

    private String buildRawJson(String decision, double confidence, String justification, boolean needsHumanReview) {
        String safeJustification = safe(justification).replace("\"", "\\\"");
        return """
                {
                  "decision": "%s",
                  "confidence": %.2f,
                  "justification": "%s",
                  "needsHumanReview": %s
                }
                """.formatted(decision, confidence, safeJustification, needsHumanReview);
    }

    private String resolveTypeContrat(Claim claim, String pdfText, String routedType) {
        String normalizedRoutedType = normalizeType(routedType);
        if (!DECISION_INCONNU.equals(normalizedRoutedType)) {
            return normalizedRoutedType;
        }

        if (claim.getPolicy() != null && claim.getPolicy().getType() != null) {
            String normalizedPolicyType = normalizeType(claim.getPolicy().getType());
            if (!DECISION_INCONNU.equals(normalizedPolicyType)) {
                return normalizedPolicyType;
            }
        }

        String fullText = (safe(claim.getDescription()) + " " + safe(pdfText)).toUpperCase(Locale.ROOT);

        if (fullText.contains("SANTE")) return "SANTE";
        if (fullText.contains("AUTO")) return "AUTO";
        if (containsAny(fullText.toLowerCase(Locale.ROOT), "hospitalisation", "scanner", "radio", "medical", "médical", "cnam")) return "SANTE";
        if (containsAny(fullText.toLowerCase(Locale.ROOT), "vehicule", "véhicule", "immatriculation", "collision", "voiture")) return "AUTO";
        if (containsAny(fullText.toLowerCase(Locale.ROOT), "habitation", "logement", "degat des eaux", "dégât des eaux")) return "HABITATION";
        if (containsAny(fullText.toLowerCase(Locale.ROOT), "voyage", "bagage", "etranger", "étranger")) return "VOYAGE";

        return DECISION_INCONNU;
    }

    private String normalizeType(String type) {
        String value = safe(type).toUpperCase(Locale.ROOT);

        if (value.contains("AUTO")) return "AUTO";
        if (value.contains("SANTE")) return "SANTE";
        if (value.contains("HABITATION")) return "HABITATION";
        if (value.contains("VOYAGE")) return "VOYAGE";

        return DECISION_INCONNU;
    }

    private String extractMetadata(TextSegment segment, String key) {
        try {
            Metadata metadata = segment.metadata();
            if (metadata == null) {
                return "";
            }

            String value = metadata.getString(key);
            return value == null ? "" : value.trim();
        } catch (Exception e) {
            return "";
        }
    }

    private String normalizeDecision(String decision) {
        return safe(decision).toUpperCase(Locale.ROOT);
    }

    private double normalizeConfidence(double confidence) {
        if (confidence > 1.0 && confidence <= 100.0) {
            confidence = confidence / 100.0;
        }
        return Math.max(0.0, Math.min(1.0, confidence));
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private String truncate(String text, int maxLength) {
        String safe = safe(text);
        if (safe.length() <= maxLength) {
            return safe;
        }
        return safe.substring(0, maxLength);
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private record PolicyCheckResult(
            String decision,
            double confidence,
            String justification,
            boolean needsHumanReview
    ) {}
}