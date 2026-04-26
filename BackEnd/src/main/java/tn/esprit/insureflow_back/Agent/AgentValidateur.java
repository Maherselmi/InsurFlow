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
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class AgentValidateur {

    private static final String AGENT_NAME = "AgentValidateur";
    private static final String CONFIG_KEY = "AGENT_VALIDATION";

    // Tailles prompt
    private static final int MAX_PDF_SNIPPET_LENGTH     = 300;
    private static final int MAX_CONTRACT_SEGMENT_CHARS = 300;
    private static final int MAX_LEARNING_CHARS         = 300;
    private static final int MAX_COVERAGE_CHARS         = 200;
    private static final int MAX_DESCRIPTION_CHARS      = 200;

    private static final int    MAX_RAG_CANDIDATES   = 8;
    private static final int    MAX_RAG_MATCHES      = 3;
    private static final double MIN_RAG_SCORE        = 0.65;
    private static final double HIGH_SCORE_THRESHOLD = 0.70;

    private static final int  MAX_RAG_RETRIES = 2;
    private static final long RETRY_DELAY_MS  = 100L;

    private static final double DEFAULT_CONFIDENCE = 0.50;

    private static final String DECISION_COUVERT = "COUVERT";
    private static final String DECISION_EXCLU   = "EXCLU";
    private static final String DECISION_INCONNU = "INCONNU";

    private static final Set<String> ALLOWED_DECISIONS =
            Set.of(DECISION_COUVERT, DECISION_EXCLU, DECISION_INCONNU);

    private final Map<String, Embedding> embeddingCache = new ConcurrentHashMap<>();

    private final EmbeddingStore<TextSegment> embeddingStore;
    private final EmbeddingModel              embeddingModel;
    private final LLMService                  llmService;
    private final ObjectMapper                objectMapper;
    private final AiAgentConfigService        aiAgentConfigService;
    private final AgentLearningMemoryService  learningMemoryService;


    public AgentResult validate(Claim claim, String claimPdfText, String routedType) {
        if (claim == null) throw new IllegalArgumentException("Le claim ne doit pas être null");

        log.info("{} - validation du sinistre #{}", AGENT_NAME, claim.getId());

        String description = safe(claim.getDescription());
        String pdfText     = safe(claimPdfText);

        if (description.isBlank() && pdfText.isBlank()) {
            log.warn("Dossier #{} sans description ni PDF", claim.getId());
            return buildResult(claim, DECISION_INCONNU, 0.0,
                    "Aucune donnée exploitable", true,
                    buildRawJson(DECISION_INCONNU, 0.0, "Aucune donnée exploitable", true));
        }

        // Pré-contrôle police (dates + cohérence type)
        PolicyCheckResult preCheck = preCheckPolicy(claim, routedType);
        if (preCheck != null) {
            log.info("Pré-contrôle police claim #{} => {}", claim.getId(), preCheck.justification());
            return buildResult(claim,
                    preCheck.decision(), preCheck.confidence(),
                    preCheck.justification(), preCheck.needsHumanReview(),
                    buildRawJson(preCheck.decision(), preCheck.confidence(),
                            preCheck.justification(), preCheck.needsHumanReview()));
        }

        double humanReviewThreshold = aiAgentConfigService.getThreshold(CONFIG_KEY);
        log.info("Seuil confiance {} = {}", CONFIG_KEY, humanReviewThreshold);

        String typeContrat   = resolveTypeContrat(claim, pdfText, routedType);
        String productCode   = resolveProductCode(claim);
        String policyContext = buildPolicyContext(claim);
        String searchQuery   = buildSearchQuery(claim, typeContrat);

        log.info("Claim #{} — typeContrat={} | productCode={}", claim.getId(), typeContrat, productCode);
        log.info("Requête RAG : {}", searchQuery);

        // PARALLÉLISATION : RAG Milvus + LearningMemory
        CompletableFuture<RagResult> ragFuture = CompletableFuture.supplyAsync(
                () -> searchMultiLevel(searchQuery, claim, typeContrat, productCode)
        );
        CompletableFuture<String> learningFuture = CompletableFuture.supplyAsync(
                () -> learningMemoryService.buildMemoryBlock(AgentName.AGENT_VALIDATION, claim.getId())
        );

        RagResult ragResult       = ragFuture.join();
        String    learningExamples = learningFuture.join();

        log.info("RAG claim #{} — niveau={} | clauses={} | productCodeMatch={}",
                claim.getId(), ragResult.level(),
                ragResult.matches().size(), ragResult.productCodeMatched());

        if (ragResult.matches().isEmpty()) {
            log.warn("Aucune clause trouvée pour claim #{} | type={} | product={}",
                    claim.getId(), typeContrat, productCode);
            return buildResult(claim, DECISION_INCONNU, 0.0,
                    "Aucune clause contractuelle trouvée pour le produit "
                            + productCode + " / type " + typeContrat,
                    true,
                    buildRawJson(DECISION_INCONNU, 0.0, "Aucune clause trouvée", true));
        }

        if (learningExamples == null || learningExamples.isBlank()) {
            learningExamples = "";
        } else {
            learningExamples = truncate(learningExamples, MAX_LEARNING_CHARS);
        }

        String contractContext = buildContractContext(ragResult);
        String prompt = buildPrompt(claim, pdfText, contractContext,
                typeContrat, policyContext, learningExamples, ragResult);

        log.info("Envoi prompt LLM ({} chars)...", prompt.length());
        String rawResponse = callLlmSafely(prompt);
        log.info("Réponse brute LLM : {}", rawResponse);

        return parseResponse(rawResponse, claim, humanReviewThreshold);
    }


    private PolicyCheckResult preCheckPolicy(Claim claim, String routedType) {
        Policy policy = claim.getPolicy();
        if (policy == null) {
            return new PolicyCheckResult(DECISION_INCONNU, 0.0,
                    "Aucune police souscrite", true);
        }

        String routeType  = normalizeType(routedType);
        String policyType = normalizeType(policy.getType());

        if (!DECISION_INCONNU.equals(routeType)
                && !DECISION_INCONNU.equals(policyType)
                && !routeType.equals(policyType)) {
            return new PolicyCheckResult(DECISION_INCONNU, 0.40,
                    "Incohérence type détecté (" + routeType
                            + ") vs police (" + policyType + ")", true);
        }

        LocalDate incidentDate = claim.getIncidentDate();
        if (incidentDate != null) {
            if (policy.getStartDate() != null && incidentDate.isBefore(policy.getStartDate())) {
                return new PolicyCheckResult(DECISION_EXCLU, 0.98,
                        "Sinistre (" + incidentDate + ") antérieur au début de la police ("
                                + policy.getStartDate() + ")", false);
            }
            if (policy.getEndDate() != null && incidentDate.isAfter(policy.getEndDate())) {
                return new PolicyCheckResult(DECISION_EXCLU, 0.98,
                        "Sinistre (" + incidentDate + ") postérieur à la fin de la police ("
                                + policy.getEndDate() + ")", false);
            }
        }
        return null;
    }



    private RagResult searchMultiLevel(String searchQuery, Claim claim,
                                       String typeContrat, String productCode) {

        List<EmbeddingMatch<TextSegment>> candidates =
                fetchFromMilvus(searchQuery, claim, MAX_RAG_CANDIDATES);

        if (candidates.isEmpty()) {
            return new RagResult(List.of(), "AUCUN", false);
        }

        // NIVEAU 1 — productCode EXACT + typeContrat
        if (!DECISION_INCONNU.equals(productCode)) {
            List<EmbeddingMatch<TextSegment>> strict =
                    filterByProductCodeAndType(candidates, productCode, typeContrat);
            if (!strict.isEmpty()) {
                List<EmbeddingMatch<TextSegment>> top = top(strict, MAX_RAG_MATCHES);
                log.info("RAG N1 STRICT claim #{} — {} clause(s) productCode={} type={}",
                        claim.getId(), top.size(), productCode, typeContrat);
                logClauses(top, claim.getId());
                return new RagResult(top, "STRICT_PRODUCT_CODE", true);
            }
            log.info("RAG N1 vide (productCode={}) → passage N2", productCode);
        }

        // NIVEAU 2 — typeContrat seul
        if (!DECISION_INCONNU.equals(typeContrat)) {
            List<EmbeddingMatch<TextSegment>> byType = filterByType(candidates, typeContrat);
            if (!byType.isEmpty()) {
                List<EmbeddingMatch<TextSegment>> top = top(byType, MAX_RAG_MATCHES);
                log.info("RAG N2 TYPE claim #{} — {} clause(s) type={}", claim.getId(), top.size(), typeContrat);
                logClauses(top, claim.getId());
                return new RagResult(top, "TYPE_CONTRAT", false);
            }
            log.info("RAG N2 vide (type={}) → passage N3 fallback", typeContrat);
        }

        // NIVEAU 3 — fallback top score
        List<EmbeddingMatch<TextSegment>> top = top(candidates, MAX_RAG_MATCHES);
        log.warn("RAG N3 FALLBACK claim #{} — {} clause(s) sans filtre", claim.getId(), top.size());
        logClauses(top, claim.getId());
        return new RagResult(top, "FALLBACK_SCORE", false);
    }

    private List<EmbeddingMatch<TextSegment>> fetchFromMilvus(String query,
                                                              Claim claim,
                                                              int maxCandidates) {
        for (int attempt = 1; attempt <= MAX_RAG_RETRIES; attempt++) {
            try {
                log.info("Milvus tentative {}/{} claim #{}", attempt, MAX_RAG_RETRIES, claim.getId());

                Embedding embedding = embeddingCache.computeIfAbsent(
                        query, q -> embeddingModel.embed(q).content()
                );

                List<EmbeddingMatch<TextSegment>> candidates =
                        embeddingStore.findRelevant(embedding, maxCandidates, MIN_RAG_SCORE);

                log.info("Milvus → {} candidats (seuil={}) claim #{}",
                        candidates.size(), MIN_RAG_SCORE, claim.getId());

                return candidates;

            } catch (StatusRuntimeException e) {
                log.warn("Erreur gRPC Milvus tentative {}/{} claim #{}: {}",
                        attempt, MAX_RAG_RETRIES, claim.getId(), e.getMessage());
            } catch (Exception e) {
                log.warn("Erreur Milvus tentative {}/{} claim #{}: {}",
                        attempt, MAX_RAG_RETRIES, claim.getId(), e.getMessage());
            }

            if (attempt < MAX_RAG_RETRIES) {
                try { Thread.sleep(RETRY_DELAY_MS); }
                catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return List.of();
                }
            }
        }
        log.error("Toutes les tentatives Milvus échouées claim #{}", claim.getId());
        return List.of();
    }

    // ── Filtre NIVEAU 1 : productCode + typeContrat ───────────────────────────
    private List<EmbeddingMatch<TextSegment>> filterByProductCodeAndType(
            List<EmbeddingMatch<TextSegment>> candidates,
            String expectedProductCode, String expectedType) {

        List<EmbeddingMatch<TextSegment>> result = new ArrayList<>();
        for (EmbeddingMatch<TextSegment> m : candidates) {
            if (m.embedded() == null) continue;
            String pc   = extractMetadata(m.embedded(), "productCode");
            String type = extractMetadata(m.embedded(), "typeContrat");
            boolean pcOk   = expectedProductCode.equalsIgnoreCase(pc);
            boolean typeOk = DECISION_INCONNU.equals(expectedType)
                    || expectedType.equalsIgnoreCase(type);
            if (pcOk && typeOk) result.add(m);
        }
        return result;
    }
    private List<EmbeddingMatch<TextSegment>> filterByType(
            List<EmbeddingMatch<TextSegment>> candidates, String expectedType) {

        if (DECISION_INCONNU.equals(expectedType)) return candidates;
        return candidates.stream()
                .filter(m -> m.embedded() != null)
                .filter(m -> expectedType.equalsIgnoreCase(
                        extractMetadata(m.embedded(), "typeContrat")))
                .collect(Collectors.toList());
    }

    private List<EmbeddingMatch<TextSegment>> top(
            List<EmbeddingMatch<TextSegment>> list, int limit) {
        return list.stream()
                .sorted(Comparator.comparingDouble(EmbeddingMatch<TextSegment>::score).reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }

    private void logClauses(List<EmbeddingMatch<TextSegment>> matches, Long claimId) {
        for (int i = 0; i < matches.size(); i++) {
            EmbeddingMatch<TextSegment> m = matches.get(i);
            if (m.embedded() == null) continue;
            log.info("Clause [{}] claim #{} — fichier={} | page={} | productCode={} | type={} | score={}",
                    i + 1, claimId,
                    extractMetadata(m.embedded(), "file"),
                    extractMetadata(m.embedded(), "pageNumber"),
                    extractMetadata(m.embedded(), "productCode"),
                    extractMetadata(m.embedded(), "typeContrat"),
                    String.format("%.4f", m.score()));
        }
    }
    //  Construction contexte contractuel

    private String buildContractContext(RagResult ragResult) {
        StringBuilder sb = new StringBuilder();
        sb.append("Niveau RAG : ").append(ragResult.level()).append("\n");
        sb.append("Contrat exact du client : ")
                .append(ragResult.productCodeMatched() ? "OUI" : "NON (type similaire ou fallback)")
                .append("\n\n");

        List<EmbeddingMatch<TextSegment>> matches = ragResult.matches();
        for (int i = 0; i < matches.size(); i++) {
            EmbeddingMatch<TextSegment> m = matches.get(i);
            TextSegment seg = m.embedded();

            String text        = seg != null ? truncate(safe(seg.text()), MAX_CONTRACT_SEGMENT_CHARS) : "";
            String file        = seg != null ? extractMetadata(seg, "file")        : "";
            String typeContrat = seg != null ? extractMetadata(seg, "typeContrat") : "";
            String productCode = seg != null ? extractMetadata(seg, "productCode") : "";
            String pageNumber  = seg != null ? extractMetadata(seg, "pageNumber")  : "";
            String articleRef  = seg != null ? extractMetadata(seg, "articleRef")  : "";
            double score       = m.score();

            sb.append(String.format("=== CLAUSE %d ===\n", i + 1));
            sb.append(String.format("Fichier     : %s\n", safe(file)));
            sb.append(String.format("Produit     : %s\n", safe(productCode)));
            sb.append(String.format("Type contrat: %s\n", safe(typeContrat)));
            sb.append(String.format("Page        : %s\n", safe(pageNumber)));
            if (!articleRef.isBlank()) {
                sb.append(String.format("Article     : %s\n", articleRef));
            }
            sb.append(String.format("Pertinence  : %.4f%s\n",
                    score, score >= HIGH_SCORE_THRESHOLD ? " ★ haute pertinence" : ""));
            sb.append(String.format("Contenu     :\n%s\n\n", text));
        }
        return sb.toString();
    }

    //  Contexte police souscrite


    private String buildPolicyContext(Claim claim) {
        Policy policy = claim.getPolicy();
        if (policy == null) return "Police non disponible";
        return String.format(
                "N° Police    : %s\nType         : %s\nFormule      : %s\nProduct Code : %s\n" +
                        "Début        : %s\nFin          : %s\nCouverture   : %s",
                safe(policy.getPolicyNumber()),
                safe(policy.getType()),
                safe(policy.getFormule()),
                safe(policy.getProductCode()),
                policy.getStartDate() != null ? policy.getStartDate() : "N/A",
                policy.getEndDate()   != null ? policy.getEndDate()   : "N/A",
                truncate(safe(policy.getCoverageDetails()), MAX_COVERAGE_CHARS)
        );
    }

    //  Requête RAG sémantique

    private String buildSearchQuery(Claim claim, String typeContrat) {
        Policy policy      = claim.getPolicy();
        String description = truncate(safe(claim.getDescription()), 200);
        String formule     = policy != null ? safe(policy.getFormule())     : "";
        String productCode = policy != null ? safe(policy.getProductCode()) : "";
        String coverage    = policy != null
                ? truncate(safe(policy.getCoverageDetails()), 150) : "";

        return "typeContrat=" + typeContrat
                + " formule=" + formule
                + " productCode=" + productCode
                + " couverture=" + coverage
                + " sinistre=" + description
                + " recherche=garanties exclusions plafonds franchise indemnisation conditions";
    }
    //  PROMPT STRUCTURÉ — police vs clauses RAG

    private String buildPrompt(Claim claim,
                               String claimPdfText,
                               String contractContext,
                               String typeContrat,
                               String policyContext,
                               String learningExamples,
                               RagResult ragResult) {

        String description  = truncate(safe(claim.getDescription()), MAX_DESCRIPTION_CHARS);
        String incidentDate = claim.getIncidentDate() != null
                ? claim.getIncidentDate().toString() : "Non précisée";
        String pdfSnippet   = truncate(claimPdfText, MAX_PDF_SNIPPET_LENGTH);

        String memorySection = (learningExamples == null || learningExamples.isBlank())
                ? "Aucun exemple disponible." : learningExamples;

        String ragWarning = ragResult.productCodeMatched()
                ? "Clauses issues du contrat exact du client."
                : "ATTENTION : clauses issues d'un contrat similaire (productCode non trouvé). Soyez prudent.";

        return "Tu es un expert juriste en assurance " + typeContrat + ".\n\n"
                + "MISSION : Comparer le sinistre avec les clauses contractuelles du client.\n"
                + "Décide : COUVERT / EXCLU / INCONNU\n\n"
                + "═══ 1. POLICE SOUSCRITE ═══\n"
                + policyContext + "\n\n"
                + "═══ 2. SINISTRE DÉCLARÉ ═══\n"
                + "Description : " + description + "\n"
                + "Date        : " + incidentDate + "\n"
                + "Document    : " + (pdfSnippet.isBlank() ? "Aucun." : pdfSnippet) + "\n\n"
                + "═══ 3. CLAUSES CONTRACTUELLES RAG ═══\n"
                + ragWarning + "\n\n"
                + contractContext
                + "═══ 4. EXEMPLES HISTORIQUES ═══\n"
                + memorySection + "\n\n"
                + "═══ 5. INSTRUCTIONS ═══\n"
                + "1. Lis la police (section 1)\n"
                + "2. Lis les clauses RAG (section 3)\n"
                + "3. Compare le sinistre avec ces clauses PRÉCISES\n"
                + "4. Ta justification DOIT citer le fichier et la page de la clause utilisée\n"
                + "5. N'utilise PAS tes connaissances générales — base-toi UNIQUEMENT sur les clauses fournies\n"
                + "6. COUVERT = garantie explicite dans les clauses\n"
                + "   EXCLU   = exclusion explicite dans les clauses\n"
                + "   INCONNU = clauses insuffisantes ou ambiguës\n"
                + "7. needsHumanReview=true si INCONNU ou confidence < 0.70\n\n"
                + "Réponds UNIQUEMENT en JSON valide :\n"
                + "{\"decision\":\"COUVERT|EXCLU|INCONNU\","
                + "\"confidence\":0.0,"
                + "\"justification\":\"référence clause + raisonnement\","
                + "\"needsHumanReview\":false}";
    }

    //  Appel LLM + parsing

    private String callLlmSafely(String prompt) {
        try {
            String r = llmService.genererReponse(prompt);
            return r == null ? "" : r.trim();
        } catch (Exception e) {
            log.error("Erreur appel LLM", e);
            return "";
        }
    }

    private AgentResult parseResponse(String raw, Claim claim, double humanReviewThreshold) {
        if (raw == null || raw.isBlank()) {
            return buildResult(claim, DECISION_INCONNU, 0.0, "Réponse LLM vide", true,
                    buildRawJson(DECISION_INCONNU, 0.0, "Réponse LLM vide", true));
        }

        try {
            String cleanJson = extractJson(raw);
            log.info("JSON extrait claim #{} : {}", claim.getId(), cleanJson);

            JsonNode node = objectMapper.readTree(cleanJson);

            String  decision         = normalizeDecision(node.path("decision").asText(""));
            double  confidence       = normalizeConfidence(node.path("confidence").asDouble(DEFAULT_CONFIDENCE));
            String  justification    = safe(node.path("justification").asText("Justification non fournie"));
            boolean needsHumanReview = node.path("needsHumanReview").asBoolean(false);

            if (!ALLOWED_DECISIONS.contains(decision)) {
                log.warn("Décision invalide : {} claim #{}", decision, claim.getId());
                return fallbackFromText(raw, claim, "Décision invalide");
            }

            if (DECISION_INCONNU.equals(decision))   needsHumanReview = true;
            if (confidence < humanReviewThreshold)    needsHumanReview = true;
            if (justification.isBlank()) {
                justification    = "Justification non fournie";
                needsHumanReview = true;
            }

            log.info("Décision claim #{} : {} | conf={} | seuil={} | humanReview={}",
                    claim.getId(), decision, confidence, humanReviewThreshold, needsHumanReview);

            return buildResult(claim, decision, confidence, justification, needsHumanReview, raw);

        } catch (Exception e) {
            log.error("Erreur parsing LLM claim #{}", claim.getId(), e);
            return fallbackFromText(raw, claim, "Erreur parsing JSON");
        }
    }

    private String extractJson(String raw) {
        String clean = raw
                .replaceAll("(?s)<think>.*?</think>", "")
                .replaceAll("```json", "")
                .replaceAll("```", "")
                .trim();
        int s = clean.indexOf('{');
        int e = clean.lastIndexOf('}');
        if (s < 0 || e <= s) throw new IllegalArgumentException("Aucun JSON trouvé");
        return clean.substring(s, e + 1).trim();
    }

    private AgentResult fallbackFromText(String raw, Claim claim, String reason) {
        String lower = safe(raw).toLowerCase(Locale.ROOT);
        String decision;
        if (containsAny(lower, "inconnu", "ambigu", "insuffisant", "incertain"))
            decision = DECISION_INCONNU;
        else if (containsAny(lower, "couvert", "garanti", "pris en charge"))
            decision = DECISION_COUVERT;
        else
            decision = DECISION_EXCLU;

        boolean needsHuman = !DECISION_COUVERT.equals(decision);
        String  msg        = reason + " - fallback";
        return buildResult(claim, decision, 0.40, msg, needsHuman,
                buildRawJson(decision, 0.40, msg, needsHuman));
    }



    private String resolveTypeContrat(Claim claim, String pdfText, String routedType) {
        String r = normalizeType(routedType);
        if (!DECISION_INCONNU.equals(r)) return r;

        if (claim.getPolicy() != null) {
            String p = normalizeType(claim.getPolicy().getType());
            if (!DECISION_INCONNU.equals(p)) return p;
        }

        String text = (safe(claim.getDescription()) + " " + safe(pdfText)).toLowerCase(Locale.ROOT);
        if (containsAny(text, "hospitalisation", "médical", "cnam"))    return "SANTE";
        if (containsAny(text, "véhicule", "collision", "voiture"))      return "AUTO";
        if (containsAny(text, "habitation", "logement", "dégât"))       return "HABITATION";
        if (containsAny(text, "voyage", "bagage", "étranger"))          return "VOYAGE";

        return DECISION_INCONNU;
    }

    private String resolveProductCode(Claim claim) {
        if (claim.getPolicy() == null) return DECISION_INCONNU;
        String pc = safe(claim.getPolicy().getProductCode());
        return pc.isBlank() ? DECISION_INCONNU : pc.toUpperCase(Locale.ROOT);
    }

    private String normalizeType(String type) {
        String v = safe(type).toUpperCase(Locale.ROOT);
        if (v.contains("AUTO"))       return "AUTO";
        if (v.contains("SANTE"))      return "SANTE";
        if (v.contains("HABITATION")) return "HABITATION";
        if (v.contains("VOYAGE"))     return "VOYAGE";
        return DECISION_INCONNU;
    }

    private AgentResult buildResult(Claim claim, String decision, double confidence,
                                    String justification, boolean needsHuman, String rawResponse) {
        AgentResult r = new AgentResult();
        r.setAgentName(AGENT_NAME);
        r.setClaim(claim);
        r.setConclusion(decision);
        r.setConfidenceScore(confidence);
        r.setRawLlmResponse(rawResponse != null ? rawResponse : justification);
        r.setNeedsHumanReview(needsHuman);
        r.setCreatedAt(LocalDateTime.now());
        return r;
    }

    private String buildRawJson(String decision, double confidence,
                                String justification, boolean needsHumanReview) {
        String j = safe(justification).replace("\"", "\\\"");
        return String.format(
                "{\"decision\":\"%s\",\"confidence\":%.2f,\"justification\":\"%s\",\"needsHumanReview\":%s}",
                decision, confidence, j, needsHumanReview);
    }

    private String extractMetadata(TextSegment segment, String key) {
        try {
            Metadata md = segment.metadata();
            if (md == null) return "";
            String v = md.getString(key);
            return v == null ? "" : v.trim();
        } catch (Exception e) {
            return "";
        }
    }

    private String normalizeDecision(String d) { return safe(d).toUpperCase(Locale.ROOT); }

    private double normalizeConfidence(double c) {
        if (c > 1.0 && c <= 100.0) c /= 100.0;
        return Math.max(0.0, Math.min(1.0, c));
    }

    private boolean containsAny(String text, String... kws) {
        for (String kw : kws) if (text.contains(kw.toLowerCase(Locale.ROOT))) return true;
        return false;
    }

    private String truncate(String text, int max) {
        String s = safe(text);
        return s.length() <= max ? s : s.substring(0, max);
    }

    private String safe(String v) { return v == null ? "" : v.trim(); }

    // ── Records internes ──────────────────────────────────────────────────────

    private record RagResult(
            List<EmbeddingMatch<TextSegment>> matches,
            String  level,
            boolean productCodeMatched
    ) {}

    private record PolicyCheckResult(
            String  decision,
            double  confidence,
            String  justification,
            boolean needsHumanReview
    ) {}
}