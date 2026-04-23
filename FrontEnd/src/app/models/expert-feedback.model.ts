export interface ExpertFeedbackRequest {
  claimId: number;
  reviewedBy: string;
  useForLearning: boolean;
  globalComment: string;

  predictedType: string;
  routeurConfidence: number;
  routeurCorrect: boolean | null;
  finalType: string;
  routeurComment: string;

  predictedDecision: string;
  validationConfidence: number;
  validationCorrect: boolean | null;
  finalDecision: string;
  validationComment: string;

  predictedEstimationMin: number;
  predictedEstimationMoyenne: number;
  predictedEstimationMax: number;
  estimateurConfidence: number;
  estimateEvaluation: string;
  finalEstimationMin: number;
  finalEstimationMoyenne: number;
  finalEstimationMax: number;
  estimateurComment: string;
}
