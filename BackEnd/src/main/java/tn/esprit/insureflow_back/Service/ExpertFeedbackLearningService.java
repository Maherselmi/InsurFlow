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
                    buildRouteurAgentOutput(request),
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
                    buildValidationAgentOutput(request),
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
                    buildEstimateurAgentOutput(request),
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

    private String buildRouteurAgentOutput(ExpertFeedbackRequest request) {
        return "Predicted type: " + safe(request.getPredictedType())
                + " | confidence: " + safeDouble(request.getRouteurConfidence());
    }

    private String buildValidationAgentOutput(ExpertFeedbackRequest request) {
        return "Predicted decision: " + safe(request.getPredictedDecision())
                + " | confidence: " + safeDouble(request.getValidationConfidence());
    }

    private String buildEstimateurAgentOutput(ExpertFeedbackRequest request) {
        return "Predicted estimation min: " + safeDouble(request.getPredictedEstimationMin())
                + " | moyenne: " + safeDouble(request.getPredictedEstimationMoyenne())
                + " | max: " + safeDouble(request.getPredictedEstimationMax())
                + " | confidence: " + safeDouble(request.getEstimateurConfidence())
                + " | evaluation: " + safe(request.getEstimateEvaluation());
    }

    private String buildFinalEstimateOutput(ExpertFeedbackRequest request) {
        return "Final estimation min: " + safeDouble(request.getFinalEstimationMin())
                + " | moyenne: " + safeDouble(request.getFinalEstimationMoyenne())
                + " | max: " + safeDouble(request.getFinalEstimationMax());
    }

    private Boolean resolveEstimateurCorrect(ExpertFeedbackRequest request) {
        if (request.getEstimateurCorrect() != null) {
            return request.getEstimateurCorrect();
        }
        String evaluation = safe(request.getEstimateEvaluation()).toUpperCase();
        if (evaluation.contains("CORRECT") || evaluation.contains("ACCEPT")) {
            return true;
        }
        if (evaluation.contains("INCORRECT") || evaluation.contains("SOUS") || evaluation.contains("SUR")) {
            return false;
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
