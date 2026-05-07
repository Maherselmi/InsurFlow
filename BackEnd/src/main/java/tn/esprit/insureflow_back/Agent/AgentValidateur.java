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

import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class AgentValidateur {

    private static final String AGENT_NAME = "AgentValidateur";
    private static final String CONFIG_KEY = "AGENT_VALIDATION";

    private static final int MAX_PDF_SNIPPET_LENGTH = 500;
    private static final int MAX_CONTRACT_SEGMENT_CHARS = 650;
    private static final int MAX_LEARNING_CHARS = 500;
    private static final int MAX_COVERAGE_CHARS = 350;
    private static final int MAX_DESCRIPTION_CHARS = 450;

    private static final int MAX_RAG_CANDIDATES = 6;
    private static final int MAX_RAG_MATCHES = 3;
    private static final double MIN_RAG_SCORE = 0.60;
    private static final double HIGH_SCORE_THRESHOLD = 0.70;

    private static final int MAX_RAG_RETRIES = 2;
    private static final long RETRY_DELAY_MS = 100L;

    private static final double DEFAULT_CONFIDENCE = 0.50;

    private static final String DECISION_COUVERT = "COUVERT";
    private static final String DECISION_EXCLU = "EXCLU";
    private static final String DECISION_INCONNU = "INCONNU";

    private static final Set<String> ALLOWED_DECISIONS =
            Set.of(DECISION_COUVERT, DECISION_EXCLU, DECISION_INCONNU);

    private final Map<String, Embedding> embeddingCache = new ConcurrentHashMap<>();

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
            return buildResult(
                    claim,
                    DECISION_INCONNU,
                    0.0,
                    "Aucune description ni document de déclaration exploitable.",
                    true,
                    buildRawJson(DECISION_INCONNU, 0.0, "Aucune donnée exploitable", true)
            );
        }

        PolicyCheckResult preCheck = preCheckPolicy(claim, routedType);

        if (preCheck != null) {
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

        String typeContrat = resolveTypeContrat(claim, pdfText, routedType);
        String productCode = resolveProductCode(claim);
        String policyContext = buildPolicyContext(claim);
        String searchQuery = buildSearchQuery(claim, typeContrat);

        log.info("Claim #{} — typeContrat={} | productCode={}", claim.getId(), typeContrat, productCode);
        log.info("Requête RAG : {}", searchQuery);

        CompletableFuture<RagResult> ragFuture = CompletableFuture.supplyAsync(
                () -> searchMultiLevel(searchQuery, claim, typeContrat, productCode)
        );

        CompletableFuture<String> learningFuture = CompletableFuture.supplyAsync(
                () -> learningMemoryService.buildMemoryBlock(AgentName.AGENT_VALIDATION, claim.getId())
        );

        RagResult ragResult = ragFuture.join();
        String learningExamples = safe(learningFuture.join());

        if (ragResult.matches().isEmpty()) {
            return buildResult(
                    claim,
                    DECISION_INCONNU,
                    0.0,
                    "Aucune clause contractuelle pertinente trouvée dans la base vectorielle.",
                    true,
                    buildRawJson(DECISION_INCONNU, 0.0, "Aucune clause RAG trouvée", true)
            );
        }

        if (!learningExamples.isBlank()) {
            learningExamples = truncate(learningExamples, MAX_LEARNING_CHARS);
        }

        String contractContext = buildContractContext(ragResult);

        String prompt = buildPrompt(
                claim,
                pdfText,
                contractContext,
                typeContrat,
                policyContext,
                learningExamples,
                ragResult
        );

        log.info("Envoi prompt LLM validateur ({} chars)", prompt.length());

        String rawResponse = callLlmSafely(prompt);
        log.info("Réponse brute LLM validateur : {}", rawResponse);

        return parseResponse(rawResponse, claim, humanReviewThreshold);
    }

    private PolicyCheckResult preCheckPolicy(Claim claim, String routedType) {
        Policy policy = claim.getPolicy();

        if (policy == null) {
            return new PolicyCheckResult(
                    DECISION_INCONNU,
                    0.0,
                    "Aucune police souscrite trouvée pour ce sinistre.",
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
                    "Incohérence entre le type détecté (" + routeType + ") et le type de police (" + policyType + ").",
                    true
            );
        }

        LocalDate incidentDate = claim.getIncidentDate();

        if (incidentDate != null) {
            if (policy.getStartDate() != null && incidentDate.isBefore(policy.getStartDate())) {
                return new PolicyCheckResult(
                        DECISION_EXCLU,
                        0.98,
                        "Le sinistre est antérieur au début de validité de la police.",
                        false
                );
            }

            if (policy.getEndDate() != null && incidentDate.isAfter(policy.getEndDate())) {
                return new PolicyCheckResult(
                        DECISION_EXCLU,
                        0.98,
                        "Le sinistre est postérieur à la fin de validité de la police.",
                        false
                );
            }
        }

        return null;
    }

    private RagResult searchMultiLevel(String searchQuery, Claim claim, String typeContrat, String productCode) {
        List<EmbeddingMatch<TextSegment>> candidates =
                fetchFromMilvus(searchQuery, claim, MAX_RAG_CANDIDATES);

        if (candidates.isEmpty()) {
            return new RagResult(List.of(), "AUCUN", false);
        }

        if (!DECISION_INCONNU.equals(productCode)) {
            List<EmbeddingMatch<TextSegment>> strict =
                    filterByProductCodeAndType(candidates, productCode, typeContrat);

            if (!strict.isEmpty()) {
                List<EmbeddingMatch<TextSegment>> top = top(strict, MAX_RAG_MATCHES);
                logClauses(top, claim.getId());
                return new RagResult(top, "STRICT_PRODUCT_CODE", true);
            }
        }

        if (!DECISION_INCONNU.equals(typeContrat)) {
            List<EmbeddingMatch<TextSegment>> byType = filterByType(candidates, typeContrat);

            if (!byType.isEmpty()) {
                List<EmbeddingMatch<TextSegment>> top = top(byType, MAX_RAG_MATCHES);
                logClauses(top, claim.getId());
                return new RagResult(top, "TYPE_CONTRAT", false);
            }
        }

        List<EmbeddingMatch<TextSegment>> top = top(candidates, MAX_RAG_MATCHES);
        logClauses(top, claim.getId());
        return new RagResult(top, "FALLBACK_SCORE", false);
    }

    private List<EmbeddingMatch<TextSegment>> fetchFromMilvus(String query, Claim claim, int maxCandidates) {
        for (int attempt = 1; attempt <= MAX_RAG_RETRIES; attempt++) {
            try {
                Embedding embedding = embeddingCache.computeIfAbsent(
                        query,
                        q -> embeddingModel.embed(q).content()
                );

                List<EmbeddingMatch<TextSegment>> candidates =
                        embeddingStore.findRelevant(embedding, maxCandidates, MIN_RAG_SCORE);

                log.info("Milvus -> {} candidat(s) claim #{}", candidates.size(), claim.getId());

                return candidates;

            } catch (StatusRuntimeException e) {
                log.warn("Erreur gRPC Milvus tentative {}/{} claim #{} : {}",
                        attempt, MAX_RAG_RETRIES, claim.getId(), e.getMessage());
            } catch (Exception e) {
                log.warn("Erreur Milvus tentative {}/{} claim #{} : {}",
                        attempt, MAX_RAG_RETRIES, claim.getId(), e.getMessage());
            }

            if (attempt < MAX_RAG_RETRIES) {
                try {
                    Thread.sleep(RETRY_DELAY_MS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return List.of();
                }
            }
        }

        return List.of();
    }

    private List<EmbeddingMatch<TextSegment>> filterByProductCodeAndType(
            List<EmbeddingMatch<TextSegment>> candidates,
            String expectedProductCode,
            String expectedType
    ) {
        List<EmbeddingMatch<TextSegment>> result = new ArrayList<>();

        for (EmbeddingMatch<TextSegment> match : candidates) {
            if (match.embedded() == null) {
                continue;
            }

            String productCode = extractMetadata(match.embedded(), "productCode");
            String typeContrat = extractMetadata(match.embedded(), "typeContrat");

            boolean productOk = expectedProductCode.equalsIgnoreCase(productCode);
            boolean typeOk = DECISION_INCONNU.equals(expectedType)
                    || expectedType.equalsIgnoreCase(typeContrat);

            if (productOk && typeOk) {
                result.add(match);
            }
        }

        return result;
    }

    private List<EmbeddingMatch<TextSegment>> filterByType(
            List<EmbeddingMatch<TextSegment>> candidates,
            String expectedType
    ) {
        if (DECISION_INCONNU.equals(expectedType)) {
            return candidates;
        }

        return candidates.stream()
                .filter(m -> m.embedded() != null)
                .filter(m -> expectedType.equalsIgnoreCase(extractMetadata(m.embedded(), "typeContrat")))
                .collect(Collectors.toList());
    }

    private List<EmbeddingMatch<TextSegment>> top(List<EmbeddingMatch<TextSegment>> list, int limit) {
        return list.stream()
                .sorted(Comparator.comparingDouble(EmbeddingMatch<TextSegment>::score).reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }

    private void logClauses(List<EmbeddingMatch<TextSegment>> matches, Long claimId) {
        for (int i = 0; i < matches.size(); i++) {
            EmbeddingMatch<TextSegment> match = matches.get(i);

            if (match.embedded() == null) {
                continue;
            }

            log.info(
                    "Clause [{}] claim #{} — fichier={} | page={} | productCode={} | type={} | score={}",
                    i + 1,
                    claimId,
                    extractMetadata(match.embedded(), "file"),
                    extractMetadata(match.embedded(), "pageNumber"),
                    extractMetadata(match.embedded(), "productCode"),
                    extractMetadata(match.embedded(), "typeContrat"),
                    String.format(Locale.US, "%.4f", match.score())
            );
        }
    }

    private String buildContractContext(RagResult ragResult) {
        StringBuilder sb = new StringBuilder();

        sb.append("Niveau RAG : ").append(ragResult.level()).append("\n");
        sb.append("Contrat exact du client : ")
                .append(ragResult.productCodeMatched() ? "OUI" : "NON")
                .append("\n\n");

        List<EmbeddingMatch<TextSegment>> matches = ragResult.matches();

        for (int i = 0; i < matches.size(); i++) {
            EmbeddingMatch<TextSegment> match = matches.get(i);
            TextSegment segment = match.embedded();

            if (segment == null) {
                continue;
            }

            String text = truncate(safe(segment.text()), MAX_CONTRACT_SEGMENT_CHARS);
            String file = extractMetadata(segment, "file");
            String typeContrat = extractMetadata(segment, "typeContrat");
            String productCode = extractMetadata(segment, "productCode");
            String pageNumber = extractMetadata(segment, "pageNumber");
            String articleRef = extractMetadata(segment, "articleRef");

            sb.append("=== CLAUSE ").append(i + 1).append(" ===\n");
            sb.append("Fichier     : ").append(safe(file)).append("\n");
            sb.append("Produit     : ").append(safe(productCode)).append("\n");
            sb.append("Type contrat: ").append(safe(typeContrat)).append("\n");
            sb.append("Page        : ").append(safe(pageNumber)).append("\n");

            if (!safe(articleRef).isBlank()) {
                sb.append("Article     : ").append(articleRef).append("\n");
            }

            sb.append("Pertinence  : ")
                    .append(String.format(Locale.US, "%.4f", match.score()))
                    .append(match.score() >= HIGH_SCORE_THRESHOLD ? " haute" : "")
                    .append("\n");

            sb.append("Contenu     :\n").append(text).append("\n\n");
        }

        return sb.toString();
    }

    private String buildPolicyContext(Claim claim) {
        Policy policy = claim.getPolicy();

        if (policy == null) {
            return "Police non disponible.";
        }

        return String.format(
                """
                N° Police    : %s
                Type         : %s
                Formule      : %s
                Product Code : %s
                Début        : %s
                Fin          : %s
                Couverture   : %s
                """,
                safe(policy.getPolicyNumber()),
                safe(policy.getType()),
                safe(policy.getFormule()),
                safe(policy.getProductCode()),
                policy.getStartDate() != null ? policy.getStartDate() : "N/A",
                policy.getEndDate() != null ? policy.getEndDate() : "N/A",
                truncate(safe(policy.getCoverageDetails()), MAX_COVERAGE_CHARS)
        ).trim();
    }

    private String buildSearchQuery(Claim claim, String typeContrat) {
        Policy policy = claim.getPolicy();

        String description = truncate(safe(claim.getDescription()), 350);
        String formule = policy != null ? safe(policy.getFormule()) : "";
        String productCode = policy != null ? safe(policy.getProductCode()) : "";
        String coverage = policy != null ? truncate(safe(policy.getCoverageDetails()), 250) : "";

        return "typeContrat=" + typeContrat
                + " formule=" + formule
                + " productCode=" + productCode
                + " couverture=" + coverage
                + " sinistre=" + description
                + " recherche objet contrat garanties prise en charge accident maladie hospitalisation remboursement exclusions conditions";
    }

    private String buildPrompt(
            Claim claim,
            String claimPdfText,
            String contractContext,
            String typeContrat,
            String policyContext,
            String learningExamples,
            RagResult ragResult
    ) {
        String description = truncate(safe(claim.getDescription()), MAX_DESCRIPTION_CHARS);
        String incidentDate = claim.getIncidentDate() != null ? claim.getIncidentDate().toString() : "Non précisée";
        String pdfSnippet = truncate(safe(claimPdfText), MAX_PDF_SNIPPET_LENGTH);

        String memorySection = safe(learningExamples).isBlank()
                ? "Aucun exemple historique disponible."
                : learningExamples;

        String ragWarning = ragResult.productCodeMatched()
                ? "Les clauses proviennent du contrat exact du client."
                : "Les clauses proviennent du même type de contrat, mais pas forcément du productCode exact. Sois prudent et baisse la confiance si nécessaire.";

        return """
                Tu es un agent validateur de sinistres d'assurance.

                OBJECTIF :
                Décider si le sinistre déclaré est COUVERT, EXCLU ou INCONNU, uniquement à partir :
                - de la police souscrite,
                - de la déclaration du sinistre,
                - des clauses contractuelles RAG fournies.

                TYPES DE DÉCISION :
                - COUVERT : le sinistre entre dans l'objet du contrat ou dans une garantie, ET aucune exclusion fournie ne s'applique explicitement.
                - EXCLU : une exclusion fournie correspond directement et explicitement au sinistre.
                - INCONNU : les clauses sont insuffisantes, contradictoires ou trop ambiguës pour décider.

                RÈGLES CRITIQUES :
                1. Ne réponds JAMAIS EXCLU simplement parce qu'une clause d'exclusion est présente.
                2. Pour répondre EXCLU, tu dois identifier une exclusion qui correspond directement au cas déclaré.
                3. Si une clause dit que certains cas sont exclus, mais que le sinistre déclaré ne correspond pas à ces exclusions, la décision ne doit pas être EXCLU.
                4. Si le contrat couvre les frais liés à un accident, une maladie, des soins, une hospitalisation ou un remboursement médical, et que le sinistre correspond à cela, réponds COUVERT sauf exclusion explicite.
                5. Si le sinistre est probablement couvert mais qu'il manque un document obligatoire, réponds COUVERT avec needsHumanReview=true.
                6. Si les clauses RAG ne contiennent que des exclusions et aucune garantie/objet du contrat utile, réponds INCONNU, pas EXCLU.
                7. Base-toi uniquement sur les clauses fournies. N'invente pas d'article.
                8. Ta justification doit citer au moins une clause avec fichier et page si disponibles.

                POLICE SOUSCRITE :
                %s

                SINISTRE DÉCLARÉ :
                Type routé : %s
                Date       : %s
                Description:
                %s

                TEXTE EXTRAIT DU PDF DE DÉCLARATION :
                %s

                CLAUSES CONTRACTUELLES RAG :
                %s

                EXEMPLES HISTORIQUES VALIDÉS :
                %s

                FORMAT DE RÉPONSE OBLIGATOIRE :
                Réponds uniquement en JSON valide, sans markdown, sans ```json, sans texte avant/après.

                {
                  "decision": "COUVERT|EXCLU|INCONNU",
                  "confidence": 0.0,
                  "justification": "Décision motivée avec référence fichier/page. Expliquer pourquoi couvert/exclu/inconnu.",
                  "needsHumanReview": false
                }
                """.formatted(
                policyContext,
                typeContrat,
                incidentDate,
                description,
                pdfSnippet.isBlank() ? "Aucun texte PDF fourni." : pdfSnippet,
                ragWarning + "\n\n" + contractContext,
                memorySection
        );
    }

    private String callLlmSafely(String prompt) {
        try {
            String response = llmService.genererReponse(prompt);
            return response == null ? "" : response.trim();
        } catch (Exception e) {
            log.error("Erreur appel LLM AgentValidateur", e);
            return "";
        }
    }

    private AgentResult parseResponse(String raw, Claim claim, double humanReviewThreshold) {
        if (raw == null || raw.isBlank()) {
            return buildResult(
                    claim,
                    DECISION_INCONNU,
                    0.0,
                    "Réponse LLM vide.",
                    true,
                    buildRawJson(DECISION_INCONNU, 0.0, "Réponse LLM vide", true)
            );
        }

        try {
            String cleanJson = extractJson(raw);
            JsonNode node = objectMapper.readTree(cleanJson);

            String decision = normalizeDecision(node.path("decision").asText(""));
            double confidence = normalizeConfidence(node.path("confidence").asDouble(DEFAULT_CONFIDENCE));
            String justification = safe(node.path("justification").asText("Justification non fournie."));
            boolean needsHumanReview = node.path("needsHumanReview").asBoolean(false);

            if (!ALLOWED_DECISIONS.contains(decision)) {
                return fallbackFromText(raw, claim, "Décision JSON invalide");
            }

            ValidationParsed corrected = correctContradiction(
                    new ValidationParsed(decision, confidence, justification, needsHumanReview)
            );

            decision = corrected.decision();
            confidence = corrected.confidence();
            justification = corrected.justification();
            needsHumanReview = corrected.needsHumanReview();

            if (DECISION_INCONNU.equals(decision)) {
                needsHumanReview = true;
            }

            if (confidence < humanReviewThreshold) {
                needsHumanReview = true;
            }

            if (justification.isBlank()) {
                justification = "Justification non fournie.";
                needsHumanReview = true;
            }

            log.info(
                    "Décision claim #{} : {} | conf={} | seuil={} | humanReview={}",
                    claim.getId(),
                    decision,
                    confidence,
                    humanReviewThreshold,
                    needsHumanReview
            );

            return buildResult(claim, decision, confidence, justification, needsHumanReview, raw);

        } catch (Exception e) {
            log.error("Erreur parsing LLM claim #{}", claim.getId(), e);
            return fallbackFromText(raw, claim, "Erreur parsing JSON");
        }
    }

    private ValidationParsed correctContradiction(ValidationParsed parsed) {
        String decision = parsed.decision();
        String lower = normalizeForCheck(parsed.justification());

        boolean saysNoExclusion = containsAny(
                lower,
                "aucune exclusion",
                "pas d exclusion",
                "pas exclu",
                "ne correspond pas a ces exclusions",
                "ne correspond pas aux exclusions",
                "aucune exclusion explicite",
                "aucune exclusion applicable",
                "ne releve pas des exclusions"
        );

        boolean saysCoveredByGuarantee = containsAny(
                lower,
                "couvre",
                "couvert",
                "pris en charge",
                "prise en charge",
                "garantie",
                "objet du contrat"
        );

        if (DECISION_EXCLU.equals(decision) && saysNoExclusion) {
            return new ValidationParsed(
                    DECISION_COUVERT,
                    Math.min(parsed.confidence(), 0.75),
                    parsed.justification()
                            + " Correction automatique : la justification indique qu'aucune exclusion explicite ne s'applique.",
                    true
            );
        }

        if (DECISION_EXCLU.equals(decision) && saysCoveredByGuarantee && !hasExplicitExclusionMatch(lower)) {
            return new ValidationParsed(
                    DECISION_COUVERT,
                    Math.min(parsed.confidence(), 0.75),
                    parsed.justification()
                            + " Correction automatique : la justification mentionne une garantie sans exclusion directement applicable.",
                    true
            );
        }

        return parsed;
    }

    private boolean hasExplicitExclusionMatch(String text) {
        return containsAny(
                text,
                "correspond a l exclusion",
                "releve de l exclusion",
                "exclusion applicable",
                "est exclu car",
                "exclu en raison",
                "n est pas couvert car",
                "non couvert car"
        );
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

    private AgentResult fallbackFromText(String raw, Claim claim, String reason) {
        String lower = normalizeForCheck(raw);

        String decision;

        if (containsAny(lower, "inconnu", "ambigu", "insuffisant", "incertain")) {
            decision = DECISION_INCONNU;
        } else if (containsAny(lower, "aucune exclusion", "pas exclu", "couvert", "garanti", "pris en charge")) {
            decision = DECISION_COUVERT;
        } else if (containsAny(lower, "exclusion applicable", "exclu", "non couvert")) {
            decision = DECISION_EXCLU;
        } else {
            decision = DECISION_INCONNU;
        }

        boolean needsHuman = true;
        String msg = reason + " - fallback prudent.";

        return buildResult(
                claim,
                decision,
                0.40,
                msg,
                needsHuman,
                buildRawJson(decision, 0.40, msg, needsHuman)
        );
    }

    private String resolveTypeContrat(Claim claim, String pdfText, String routedType) {
        String routed = normalizeType(routedType);

        if (!DECISION_INCONNU.equals(routed)) {
            return routed;
        }

        if (claim.getPolicy() != null) {
            String policyType = normalizeType(claim.getPolicy().getType());

            if (!DECISION_INCONNU.equals(policyType)) {
                return policyType;
            }
        }

        String text = normalizeForCheck(safe(claim.getDescription()) + " " + safe(pdfText));

        if (containsAny(text, "hospitalisation", "medical", "medecin", "soins", "cnam", "sante")) {
            return "SANTE";
        }

        if (containsAny(text, "vehicule", "collision", "voiture", "auto", "pare brise")) {
            return "AUTO";
        }

        if (containsAny(text, "habitation", "logement", "degat", "incendie", "fuite")) {
            return "HABITATION";
        }

        if (containsAny(text, "voyage", "bagage", "etranger", "vol annule", "retard")) {
            return "VOYAGE";
        }

        if (containsAny(text, "deces", "invalidite", "assurance vie", "beneficiaire", "capital")) {
            return "VIE";
        }

        return DECISION_INCONNU;
    }

    private String resolveProductCode(Claim claim) {
        if (claim.getPolicy() == null) {
            return DECISION_INCONNU;
        }

        String productCode = safe(claim.getPolicy().getProductCode());

        return productCode.isBlank()
                ? DECISION_INCONNU
                : productCode.toUpperCase(Locale.ROOT);
    }

    private String normalizeType(String type) {
        String value = normalizeForCheck(type);

        if (value.contains("auto")) {
            return "AUTO";
        }

        if (value.contains("sante") || value.contains("health")) {
            return "SANTE";
        }

        if (value.contains("habitation") || value.contains("home")) {
            return "HABITATION";
        }

        if (value.contains("voyage") || value.contains("travel")) {
            return "VOYAGE";
        }

        if (value.contains("vie") || value.contains("life")) {
            return "VIE";
        }

        return DECISION_INCONNU;
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

        // IMPORTANT : conclusion doit rester courte.
        // L'orchestrateur teste EXCLU / INCONNU / COUVERT sur ce champ.
        result.setConclusion(decision);

        result.setConfidenceScore(confidence);
        result.setNeedsHumanReview(needsHuman);

        if (rawResponse != null && !rawResponse.isBlank()) {
            result.setRawLlmResponse(rawResponse);
        } else {
            result.setRawLlmResponse(buildRawJson(decision, confidence, justification, needsHuman));
        }

        result.setCreatedAt(LocalDateTime.now());

        return result;
    }

    private String buildRawJson(
            String decision,
            double confidence,
            String justification,
            boolean needsHumanReview
    ) {
        String safeJustification = safe(justification).replace("\"", "\\\"");

        return String.format(
                Locale.US,
                "{\"decision\":\"%s\",\"confidence\":%.2f,\"justification\":\"%s\",\"needsHumanReview\":%s}",
                decision,
                confidence,
                safeJustification,
                needsHumanReview
        );
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
        String normalizedText = text == null ? "" : text;

        for (String keyword : keywords) {
            if (normalizedText.contains(normalizeForCheck(keyword))) {
                return true;
            }
        }

        return false;
    }

    private String normalizeForCheck(String value) {
        String safeValue = safe(value).toLowerCase(Locale.ROOT);

        return Normalizer.normalize(safeValue, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replaceAll("[’']", " ")
                .replaceAll("\\s+", " ")
                .trim();
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

    private record RagResult(
            List<EmbeddingMatch<TextSegment>> matches,
            String level,
            boolean productCodeMatched
    ) {}

    private record PolicyCheckResult(
            String decision,
            double confidence,
            String justification,
            boolean needsHumanReview
    ) {}

    private record ValidationParsed(
            String decision,
            double confidence,
            String justification,
            boolean needsHumanReview
    ) {}
}