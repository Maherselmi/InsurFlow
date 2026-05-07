package tn.esprit.insureflow_back.DTO;

public record HumanDecisionRequest(
        String comment,
        Double finalEstimationMin,
        Double finalEstimationMoyenne,
        Double finalEstimationMax
) {}