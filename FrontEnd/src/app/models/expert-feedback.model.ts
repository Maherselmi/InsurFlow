export type ClaimType = 'AUTO' | 'HABITATION' | 'SANTE' | 'VOYAGE' | 'INCONNU' | '';
export type ValidationDecision = 'COUVERT' | 'EXCLU' | 'INCONNU' | '';
export type EstimateEvaluation = 'CORRECTE' | 'SOUS_ESTIME' | 'SUR_ESTIME' | 'INCERTAINE';

export interface ExpertFeedbackRequest {
  claimId: number;
  reviewedBy: string;
  useForLearning: boolean;

  /** Note de satisfaction expert entre 1 et 5. */
  satisfactionScore: number | null;

  globalComment: string;

  // ROUTEUR
  predictedType: string;
  routeurConfidence: number | null;
  routeurCorrect: boolean | null;
  finalType: string;
  routeurComment: string;

  // VALIDATION
  predictedDecision: string;
  validationConfidence: number | null;
  validationCorrect: boolean | null;
  finalDecision: string;
  validationComment: string;

  // ESTIMATEUR
  predictedEstimationMin: number | null;
  predictedEstimationMoyenne: number | null;
  predictedEstimationMax: number | null;
  estimateurConfidence: number | null;
  estimateurCorrect: boolean | null;
  estimateEvaluation: EstimateEvaluation;
  finalEstimationMin: number | null;
  finalEstimationMoyenne: number | null;
  finalEstimationMax: number | null;
  estimateurComment: string;
}

export interface ExpertFeedbackResponse {
  success: boolean;
  message: string;
  learningItemsSaved: number;
}
