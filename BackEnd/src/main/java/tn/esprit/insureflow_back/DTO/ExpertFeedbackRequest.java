package tn.esprit.insureflow_back.DTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExpertFeedbackRequest {

    private Long claimId;
    private String reviewedBy;

    // If true, the validated expert answers are saved as learning examples.
    private Boolean useForLearning;

    // Global satisfaction from 1 to 5. It is stored as metadata and used in memory priority.
    private Integer satisfactionScore;

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
    private Boolean estimateurCorrect;
    private String estimateEvaluation;
    private Double finalEstimationMin;
    private Double finalEstimationMoyenne;
    private Double finalEstimationMax;
    private String estimateurComment;
}
