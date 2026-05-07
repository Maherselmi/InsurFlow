package tn.esprit.insureflow_back.Service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.esprit.insureflow_back.DTO.ExpertFeedbackRequest;
import tn.esprit.insureflow_back.Domain.ENUMS.AgentName;
import tn.esprit.insureflow_back.Domain.Entities.AgentLearningFeedback;
import tn.esprit.insureflow_back.Domain.Entities.Claim;
import tn.esprit.insureflow_back.Domain.Entities.Policy;
import tn.esprit.insureflow_back.Repository.AgentLearningFeedbackRepository;
import tn.esprit.insureflow_back.Repository.ClaimRepository;

@Service
@RequiredArgsConstructor
public class ExpertFeedbackLearningService {

    private final ClaimRepository claimRepository;
    private final AgentLearningFeedbackRepository learningRepository;

    @Transactional
    public int saveExpertFeedback(ExpertFeedbackRequest request) {
        validateRequest(request);

        Claim claim = claimRepository.findById(request.getClaimId())
                .orElseThrow(() -> new IllegalArgumentException("Claim not found: " + request.getClaimId()));

        int saved = 0;

        if (hasText(request.getFinalType())) {
            upsertLearningFeedback(
                    claim,
                    AgentName.AGENT_ROUTEUR,
                    buildCommonInput(claim),
                    buildRouteurAgentOutput(request),   // <-- inclut maintenant la justification IA
                    normalizeType(request.getFinalType()),
                    request.getRouteurCorrect(),
                    request.getRouteurConfidence(),
                    mergeComments(request.getGlobalComment(), request.getRouteurComment()),
                    request
            );
            saved++;
        }

        if (hasText(request.getFinalDecision())) {
            upsertLearningFeedback(
                    claim,
                    AgentName.AGENT_VALIDATION,
                    buildValidationInput(claim, request),
                    buildValidationAgentOutput(request), // <-- inclut maintenant la justification IA
                    normalizeDecision(request.getFinalDecision()),
                    request.getValidationCorrect(),
                    request.getValidationConfidence(),
                    mergeComments(request.getGlobalComment(), request.getValidationComment()),
                    request
            );
            saved++;
        }

        if (hasFinalEstimate(request)) {
            upsertLearningFeedback(
                    claim,
                    AgentName.AGENT_ESTIMATEUR,
                    buildEstimateurInput(claim, request),
                    buildEstimateurAgentOutput(request), // <-- inclut maintenant la justification IA
                    buildFinalEstimateOutput(request),
                    resolveEstimateurCorrect(request),
                    request.getEstimateurConfidence(),
                    mergeComments(request.getGlobalComment(), request.getEstimateurComment()),
                    request
            );
            saved++;
        }

        return saved;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // UPSERT CORE
    // ─────────────────────────────────────────────────────────────────────────

    private void upsertLearningFeedback(
            Claim claim,
            AgentName agentName,
            String inputData,
            String agentOutput,
            String finalValidatedOutput,
            Boolean wasCorrect,
            Double predictedConfidence,
            String expertComment,
            ExpertFeedbackRequest request
    ) {
        AgentLearningFeedback feedback = learningRepository
                .findByClaim_IdAndAgentName(claim.getId(), agentName)
                .orElseGet(AgentLearningFeedback::new);

        feedback.setClaim(claim);
        feedback.setAgentName(agentName);
        feedback.setInputData(inputData);
        feedback.setAgentOutput(agentOutput);
        feedback.setFinalValidatedOutput(finalValidatedOutput);
        feedback.setWasCorrect(Boolean.TRUE.equals(wasCorrect));
        feedback.setUseForLearning(Boolean.TRUE.equals(request.getUseForLearning()));
        feedback.setPredictedConfidence(predictedConfidence);
        feedback.setReviewedBy(safe(request.getReviewedBy()));
        feedback.setExpertComment(expertComment);
        feedback.setSatisfactionScore(normalizeSatisfaction(request.getSatisfactionScore()));

        learningRepository.save(feedback);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // VALIDATION
    // ─────────────────────────────────────────────────────────────────────────

    private void validateRequest(ExpertFeedbackRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Feedback request is required");
        }
        if (request.getClaimId() == null) {
            throw new IllegalArgumentException("claimId is required");
        }
        if (!hasText(request.getFinalType())
                && !hasText(request.getFinalDecision())
                && !hasFinalEstimate(request)) {
            throw new IllegalArgumentException("At least one final expert answer is required");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // INPUT BUILDERS
    // ─────────────────────────────────────────────────────────────────────────

    private String buildCommonInput(Claim claim) {
        StringBuilder sb = new StringBuilder();
        sb.append("Claim ID: ").append(claim.getId()).append('\n');
        sb.append("Description: ").append(safe(claim.getDescription())).append('\n');
        sb.append("Incident date: ").append(claim.getIncidentDate() == null ? "N/A" : claim.getIncidentDate()).append('\n');
        sb.append(buildPolicyLine(claim));
        return sb.toString().trim();
    }

    private String buildValidationInput(Claim claim, ExpertFeedbackRequest request) {
        return buildCommonInput(claim)
                + "\nPredicted routeur type: " + safe(request.getPredictedType())
                + "\nFinal routeur type: " + safe(request.getFinalType());
    }

    private String buildEstimateurInput(Claim claim, ExpertFeedbackRequest request) {
        int documentsCount = claim.getDocuments() == null ? 0 : claim.getDocuments().size();
        return buildCommonInput(claim)
                + "\nFinal validation decision: " + safe(request.getFinalDecision())
                + "\nDocuments count: " + documentsCount;
    }

    private String buildPolicyLine(Claim claim) {
        Policy policy = claim.getPolicy();
        if (policy == null) {
            return "Policy: N/A";
        }
        return "Policy: number=" + safe(policy.getPolicyNumber())
                + ", type=" + safe(policy.getType())
                + ", formule=" + safe(policy.getFormule())
                + ", productCode=" + safe(policy.getProductCode())
                + ", start=" + (policy.getStartDate() == null ? "N/A" : policy.getStartDate())
                + ", end=" + (policy.getEndDate() == null ? "N/A" : policy.getEndDate());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // AGENT OUTPUT BUILDERS — FIX : justification IA incluse
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * CORRECTION : on inclut maintenant la justification textuelle de l'agent ROUTEUR.
     * Avant : seules les valeurs numériques étaient sauvegardées.
     * Maintenant : le raisonnement complet de l'IA est persisté pour le learning.
     */
    private String buildRouteurAgentOutput(ExpertFeedbackRequest request) {
        StringBuilder sb = new StringBuilder();
        sb.append("Predicted type: ").append(safe(request.getPredictedType()));
        sb.append(" | confidence: ").append(safeDouble(request.getRouteurConfidence()));
        if (hasText(request.getRouteurJustification())) {
            sb.append("\nJustification IA:\n").append(request.getRouteurJustification().trim());
        }
        return sb.toString();
    }

    /**
     * CORRECTION : on inclut maintenant la justification textuelle de l'agent VALIDATION.
     */
    private String buildValidationAgentOutput(ExpertFeedbackRequest request) {
        StringBuilder sb = new StringBuilder();
        sb.append("Predicted decision: ").append(safe(request.getPredictedDecision()));
        sb.append(" | confidence: ").append(safeDouble(request.getValidationConfidence()));
        if (hasText(request.getValidationJustification())) {
            sb.append("\nJustification IA:\n").append(request.getValidationJustification().trim());
        }
        return sb.toString();
    }

    /**
     * CORRECTION : on inclut maintenant la justification textuelle de l'agent ESTIMATEUR.
     * C'est la correction principale pour le problème "min 100 moy 400 max 800 → min 100 moy 200 max 300".
     * Le memory block contiendra désormais le raisonnement complet qui explique POURQUOI
     * les valeurs ont été corrigées, ce qui permettra au modèle d'apprendre.
     */
    private String buildEstimateurAgentOutput(ExpertFeedbackRequest request) {
        StringBuilder sb = new StringBuilder();
        sb.append("Predicted estimation min: ").append(safeDouble(request.getPredictedEstimationMin()));
        sb.append(" | moyenne: ").append(safeDouble(request.getPredictedEstimationMoyenne()));
        sb.append(" | max: ").append(safeDouble(request.getPredictedEstimationMax()));
        sb.append(" | confidence: ").append(safeDouble(request.getEstimateurConfidence()));
        sb.append(" | evaluation: ").append(safe(request.getEstimateEvaluation()));
        if (hasText(request.getEstimateurJustification())) {
            sb.append("\nJustification IA:\n").append(request.getEstimateurJustification().trim());
        }
        return sb.toString();
    }

    private String buildFinalEstimateOutput(ExpertFeedbackRequest request) {
        return "Final estimation min: " + safeDouble(request.getFinalEstimationMin())
                + " | moyenne: " + safeDouble(request.getFinalEstimationMoyenne())
                + " | max: " + safeDouble(request.getFinalEstimationMax());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // HELPERS
    // ─────────────────────────────────────────────────────────────────────────

    private Boolean resolveEstimateurCorrect(ExpertFeedbackRequest request) {
        if (request.getEstimateurCorrect() != null) {
            return request.getEstimateurCorrect();
        }

        String evaluation = safe(request.getEstimateEvaluation()).toUpperCase();

        if (evaluation.contains("INCORRECT")
                || evaluation.contains("SOUS")
                || evaluation.contains("SUR")
                || evaluation.contains("SOUS_ESTIME")
                || evaluation.contains("SUR_ESTIME")) {
            return false;
        }

        if (evaluation.contains("CORRECT")
                || evaluation.contains("CORRECTE")
                || evaluation.contains("ACCEPT")) {
            return true;
        }

        return estimatesAreEqual(request);
    }

    private boolean estimatesAreEqual(ExpertFeedbackRequest request) {
        return equalsMoney(request.getPredictedEstimationMin(), request.getFinalEstimationMin())
                && equalsMoney(request.getPredictedEstimationMoyenne(), request.getFinalEstimationMoyenne())
                && equalsMoney(request.getPredictedEstimationMax(), request.getFinalEstimationMax());
    }

    private boolean equalsMoney(Double a, Double b) {
        if (a == null || b == null) return false;
        return Math.abs(a - b) < 0.01;
    }

    private boolean hasFinalEstimate(ExpertFeedbackRequest request) {
        return request != null
                && request.getFinalEstimationMin() != null
                && request.getFinalEstimationMoyenne() != null
                && request.getFinalEstimationMax() != null;
    }

    private String normalizeType(String value) {
        String v = safe(value).toUpperCase();
        if (v.contains("AUTO")) return "AUTO";
        if (v.contains("HABITATION")) return "HABITATION";
        if (v.contains("SANTE")) return "SANTE";
        if (v.contains("VOYAGE")) return "VOYAGE";
        if (v.contains("VIE")) return "VIE";
        if (v.contains("LIFE")) return "VIE";
        return "INCONNU";
    }

    private String normalizeDecision(String value) {
        String v = safe(value).toUpperCase();
        if (v.contains("COUVERT")) return "COUVERT";
        if (v.contains("EXCLU")) return "EXCLU";
        return "INCONNU";
    }

    private Integer normalizeSatisfaction(Integer value) {
        if (value == null) return null;
        return Math.max(1, Math.min(5, value));
    }

    private String mergeComments(String global, String local) {
        String g = safe(global);
        String l = safe(local);
        if (g.isBlank()) return l;
        if (l.isBlank()) return g;
        return g + " | " + l;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private String safeDouble(Double value) {
        return value == null ? "N/A" : String.format(java.util.Locale.US, "%.2f", value);
    }
}