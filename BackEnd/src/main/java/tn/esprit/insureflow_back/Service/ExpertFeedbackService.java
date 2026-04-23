package tn.esprit.insureflow_back.Service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tn.esprit.insureflow_back.DTO.ExpertFeedbackRequest;
import tn.esprit.insureflow_back.Domain.ENUMS.AgentName;
import tn.esprit.insureflow_back.Domain.Entities.AgentLearningFeedback;
import tn.esprit.insureflow_back.Domain.Entities.Claim;
import tn.esprit.insureflow_back.Domain.Entities.ExpertFeedback;
import tn.esprit.insureflow_back.Repository.AgentLearningFeedbackRepository;
import tn.esprit.insureflow_back.Repository.ClaimRepository;
import tn.esprit.insureflow_back.Repository.ExpertFeedbackRepository;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ExpertFeedbackService {

    private final ExpertFeedbackRepository expertFeedbackRepository;
    private final AgentLearningFeedbackRepository learningFeedbackRepository;
    private final ClaimRepository claimRepository;

    public ExpertFeedback saveFeedback(ExpertFeedbackRequest request) {
        Claim claim = claimRepository.findById(request.getClaimId())
                .orElseThrow(() -> new IllegalArgumentException("Claim introuvable avec id = " + request.getClaimId()));

        ExpertFeedback feedback = expertFeedbackRepository.findByClaimId(claim.getId())
                .orElse(ExpertFeedback.builder().claim(claim).build());

        mapRequestToFeedback(feedback, request);
        ExpertFeedback savedFeedback = expertFeedbackRepository.save(feedback);

        syncLearningFeedback(savedFeedback);

        return savedFeedback;
    }

    public ExpertFeedback getByClaimId(Long claimId) {
        return expertFeedbackRepository.findByClaimId(claimId)
                .orElseThrow(() -> new IllegalArgumentException("Aucun feedback trouvé pour claimId = " + claimId));
    }

    private void mapRequestToFeedback(ExpertFeedback feedback, ExpertFeedbackRequest request) {
        feedback.setReviewedBy(request.getReviewedBy());
        feedback.setReviewedAt(LocalDateTime.now());
        feedback.setUseForLearning(Boolean.TRUE.equals(request.getUseForLearning()));
        feedback.setGlobalComment(request.getGlobalComment());

        // Routeur
        feedback.setPredictedType(request.getPredictedType());
        feedback.setRouteurConfidence(request.getRouteurConfidence());
        feedback.setRouteurCorrect(request.getRouteurCorrect());
        feedback.setFinalType(request.getFinalType());
        feedback.setRouteurComment(request.getRouteurComment());

        // Validation
        feedback.setPredictedDecision(request.getPredictedDecision());
        feedback.setValidationConfidence(request.getValidationConfidence());
        feedback.setValidationCorrect(request.getValidationCorrect());
        feedback.setFinalDecision(request.getFinalDecision());
        feedback.setValidationComment(request.getValidationComment());

        // Estimateur
        feedback.setPredictedEstimationMin(request.getPredictedEstimationMin());
        feedback.setPredictedEstimationMoyenne(request.getPredictedEstimationMoyenne());
        feedback.setPredictedEstimationMax(request.getPredictedEstimationMax());
        feedback.setEstimateurConfidence(request.getEstimateurConfidence());
        feedback.setEstimateEvaluation(request.getEstimateEvaluation());
        feedback.setFinalEstimationMin(request.getFinalEstimationMin());
        feedback.setFinalEstimationMoyenne(request.getFinalEstimationMoyenne());
        feedback.setFinalEstimationMax(request.getFinalEstimationMax());
        feedback.setEstimateurComment(request.getEstimateurComment());
    }

    private void syncLearningFeedback(ExpertFeedback feedback) {
        createOrUpdateRouteurFeedback(feedback);
        createOrUpdateValidationFeedback(feedback);
        createOrUpdateEstimateurFeedback(feedback);
    }

    private void createOrUpdateRouteurFeedback(ExpertFeedback feedback) {
        AgentLearningFeedback entity = learningFeedbackRepository
                .findByClaimIdAndAgentName(feedback.getClaim().getId(), AgentName.AGENT_ROUTEUR)
                .orElse(
                        AgentLearningFeedback.builder()
                                .claim(feedback.getClaim())
                                .agentName(AgentName.AGENT_ROUTEUR)
                                .createdAt(LocalDateTime.now())
                                .build()
                );

        entity.setInputData(buildBaseInput(feedback.getClaim()));
        entity.setAgentOutput("Type prédit = " + safe(feedback.getPredictedType()));
        entity.setFinalValidatedOutput("Type final = " + safe(feedback.getFinalType()));
        entity.setConfidenceScore(feedback.getRouteurConfidence());
        entity.setHumanReviewed(true);
        entity.setWasCorrect(Boolean.TRUE.equals(feedback.getRouteurCorrect()));
        entity.setUseForLearning(Boolean.TRUE.equals(feedback.getUseForLearning()));
        entity.setUpdatedAt(LocalDateTime.now());

        learningFeedbackRepository.save(entity);
    }

    private void createOrUpdateValidationFeedback(ExpertFeedback feedback) {
        AgentLearningFeedback entity = learningFeedbackRepository
                .findByClaimIdAndAgentName(feedback.getClaim().getId(), AgentName.AGENT_VALIDATION)
                .orElse(
                        AgentLearningFeedback.builder()
                                .claim(feedback.getClaim())
                                .agentName(AgentName.AGENT_VALIDATION)
                                .createdAt(LocalDateTime.now())
                                .build()
                );

        entity.setInputData(buildBaseInput(feedback.getClaim()));
        entity.setAgentOutput("Décision prédite = " + safe(feedback.getPredictedDecision()));
        entity.setFinalValidatedOutput("Décision finale = " + safe(feedback.getFinalDecision()));
        entity.setConfidenceScore(feedback.getValidationConfidence());
        entity.setHumanReviewed(true);
        entity.setWasCorrect(Boolean.TRUE.equals(feedback.getValidationCorrect()));
        entity.setUseForLearning(Boolean.TRUE.equals(feedback.getUseForLearning()));
        entity.setUpdatedAt(LocalDateTime.now());

        learningFeedbackRepository.save(entity);
    }

    private void createOrUpdateEstimateurFeedback(ExpertFeedback feedback) {
        AgentLearningFeedback entity = learningFeedbackRepository
                .findByClaimIdAndAgentName(feedback.getClaim().getId(), AgentName.AGENT_ESTIMATEUR)
                .orElse(
                        AgentLearningFeedback.builder()
                                .claim(feedback.getClaim())
                                .agentName(AgentName.AGENT_ESTIMATEUR)
                                .createdAt(LocalDateTime.now())
                                .build()
                );

        entity.setInputData(buildBaseInput(feedback.getClaim()));
        entity.setAgentOutput(
                "Estimation prédite = min:" + safeNumber(feedback.getPredictedEstimationMin())
                        + ", moy:" + safeNumber(feedback.getPredictedEstimationMoyenne())
                        + ", max:" + safeNumber(feedback.getPredictedEstimationMax())
        );
        entity.setFinalValidatedOutput(
                "Estimation finale = min:" + safeNumber(feedback.getFinalEstimationMin())
                        + ", moy:" + safeNumber(feedback.getFinalEstimationMoyenne())
                        + ", max:" + safeNumber(feedback.getFinalEstimationMax())
                        + ", évaluation expert:" + safe(feedback.getEstimateEvaluation())
        );
        entity.setConfidenceScore(feedback.getEstimateurConfidence());
        entity.setHumanReviewed(true);
        entity.setWasCorrect("CORRECTE".equalsIgnoreCase(safe(feedback.getEstimateEvaluation())));
        entity.setUseForLearning(Boolean.TRUE.equals(feedback.getUseForLearning()));
        entity.setUpdatedAt(LocalDateTime.now());

        learningFeedbackRepository.save(entity);
    }

    private String buildBaseInput(Claim claim) {
        String description = claim.getDescription() != null ? claim.getDescription() : "";
        String incidentDate = claim.getIncidentDate() != null ? claim.getIncidentDate().toString() : "";
        String policyNumber = claim.getPolicy() != null && claim.getPolicy().getPolicyNumber() != null
                ? claim.getPolicy().getPolicyNumber()
                : "";
        String policyType = claim.getPolicy() != null && claim.getPolicy().getType() != null
                ? claim.getPolicy().getType()
                : "";

        return """
                Description: %s
                Date incident: %s
                Police: %s
                Type police: %s
                """.formatted(description, incidentDate, policyNumber, policyType);
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private String safeNumber(Double value) {
        return value == null ? "0.0" : String.valueOf(value);
    }
}