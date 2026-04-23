package tn.esprit.insureflow_back.DTO;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExpertFeedbackRequest {

    private Long claimId;
    private String reviewedBy;
    private Boolean useForLearning;
    private String globalComment;

    // ROUTEUR
    private String predictedType;
    private Double routeurConfidence;
    private Boolean routeurCorrect;
    private String finalType;
    private String routeurComment;

    // VALIDATION
    private String predictedDecision;
    private Double validationConfidence;
    private Boolean validationCorrect;
    private String finalDecision;
    private String validationComment;

    // ESTIMATEUR
    private Double predictedEstimationMin;
    private Double predictedEstimationMoyenne;
    private Double predictedEstimationMax;
    private Double estimateurConfidence;
    private String estimateEvaluation;
    private Double finalEstimationMin;
    private Double finalEstimationMoyenne;
    private Double finalEstimationMax;
    private String estimateurComment;
}