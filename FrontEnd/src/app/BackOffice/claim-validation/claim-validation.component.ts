import { Component, OnInit } from '@angular/core';
import { CommonModule, DatePipe, SlicePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';
import { forkJoin, of, switchMap } from 'rxjs';
import { catchError } from 'rxjs/operators';

import { Claim, ClaimDocument } from '../../models/Claim/claim.model';
import { ClaimService } from '../../services/claim.service';
import { AgentResult, AgentResultService } from '../../services/agent-result.service';
import { ExpertFeedbackService } from '../../services/expert-feedback.service';
import { ExpertFeedbackRequest } from '../../models/expert-feedback.model';
import {ClaimValidationService, ReviewData} from "../../services/Claim Validation";

@Component({
  selector: 'app-claim-validation',
  standalone: true,
  imports: [
    CommonModule,
    DatePipe,
    SlicePipe,
    FormsModule
  ],
  templateUrl: './claim-validation.component.html',
  styleUrls: ['./claim-validation.component.css']
})
export class ClaimValidationComponent implements OnInit {

  pendingClaims: Claim[] = [];
  selectedClaim: Claim | null = null;
  reviewData: ReviewData | null = null;
  claimDetails: Claim | null = null;
  agentResults: AgentResult[] = [];

  loading = false;
  reviewLoading = false;
  actionLoading = false;
  actionType: 'approve' | 'reject' | null = null;

  gestionnairComment = '';

  feedbackForm: ExpertFeedbackRequest = this.createEmptyFeedbackForm(0);

  toast = {
    visible: false,
    message: '',
    type: 'success' as 'success' | 'error'
  };

  constructor(
    private claimValidationService: ClaimValidationService,
    private claimService: ClaimService,
    private agentResultService: AgentResultService,
    private expertFeedbackService: ExpertFeedbackService,
    private sanitizer: DomSanitizer
  ) {}

  ngOnInit(): void {
    this.loadPendingClaims();
  }

  loadPendingClaims(): void {
    this.loading = true;

    this.claimValidationService.getPendingClaims().subscribe({
      next: (data) => {
        this.pendingClaims = data || [];
        this.loading = false;
      },
      error: (err) => {
        console.error(err);
        this.loading = false;
        this.showToast('Erreur lors du chargement des dossiers', 'error');
      }
    });
  }

  selectClaim(claim: Claim): void {
    if (!claim?.id) {
      this.showToast('Dossier invalide', 'error');
      return;
    }

    this.selectedClaim = claim;
    this.reviewData = null;
    this.claimDetails = null;
    this.agentResults = [];
    this.gestionnairComment = '';
    this.reviewLoading = true;

    this.resetFeedbackForm(claim.id);

    forkJoin({
      review: this.claimValidationService.getClaimReview(claim.id).pipe(
        catchError(() => of(null))
      ),
      details: this.claimService.getClaimById(claim.id).pipe(
        catchError(() => of(null))
      ),
      agentResults: this.agentResultService.getResultsByClaimId(claim.id).pipe(
        catchError(() => of([] as AgentResult[]))
      ),
      existingFeedback: this.expertFeedbackService.getFeedbackByClaimId(claim.id).pipe(
        catchError(() => of(null))
      )
    }).subscribe({
      next: ({ review, details, agentResults, existingFeedback }) => {
        this.reviewData = review;
        this.claimDetails = details;
        this.agentResults = agentResults || [];

        this.prefillFromAgentResults(this.agentResults);

        if (existingFeedback) {
          this.prefillFromExistingFeedback(existingFeedback);
        }

        this.reviewLoading = false;
      },
      error: (err) => {
        console.error(err);
        this.reviewLoading = false;
        this.showToast('Impossible de charger les données du dossier', 'error');
      }
    });
  }

  approveClaim(): void {
    this.submitDecision('approve');
  }

  rejectClaim(): void {
    this.submitDecision('reject');
  }

  private submitDecision(action: 'approve' | 'reject'): void {
    if (!this.selectedClaim || this.actionLoading) return;

    this.actionLoading = true;
    this.actionType = action;

    const payload = this.prepareFeedbackPayload();

    this.expertFeedbackService.saveFeedback(payload).pipe(
      switchMap(() => {
        if (action === 'approve') {
          return this.claimValidationService.approveClaim(
            this.selectedClaim!.id,
            this.gestionnairComment
          );
        }

        return this.claimValidationService.rejectClaim(
          this.selectedClaim!.id,
          this.gestionnairComment
        );
      })
    ).subscribe({
      next: (res) => {
        this.showToast(
          action === 'approve'
            ? `Dossier #${res.claimId} approuvé avec feedback enregistré`
            : `Dossier #${res.claimId} rejeté avec feedback enregistré`,
          'success'
        );

        this.removeClaim(this.selectedClaim!.id);
        this.closeSelectedClaim();

        this.actionLoading = false;
        this.actionType = null;
      },
      error: (err) => {
        console.error(err);
        this.showToast("Erreur lors de l'enregistrement de la revue expert", 'error');

        this.actionLoading = false;
        this.actionType = null;
      }
    });
  }

  private prepareFeedbackPayload(): ExpertFeedbackRequest {
    if (!this.selectedClaim) {
      return this.feedbackForm;
    }

    const payload: ExpertFeedbackRequest = {
      ...this.feedbackForm,
      claimId: this.selectedClaim.id,
      globalComment: this.feedbackForm.globalComment?.trim() || this.gestionnairComment?.trim() || '',
      reviewedBy: this.feedbackForm.reviewedBy?.trim() || 'Expert',
      useForLearning: this.feedbackForm.useForLearning ?? true,
      satisfactionScore: this.feedbackForm.satisfactionScore ?? 5
    };

    if (payload.routeurCorrect === null || payload.routeurCorrect === undefined) {
      payload.routeurCorrect = this.sameText(payload.predictedType, payload.finalType);
    }

    if (payload.validationCorrect === null || payload.validationCorrect === undefined) {
      payload.validationCorrect = this.sameText(payload.predictedDecision, payload.finalDecision);
    }

    if (payload.estimateurCorrect === null || payload.estimateurCorrect === undefined) {
      payload.estimateurCorrect = payload.estimateEvaluation === 'CORRECTE';
    }

    payload.predictedEstimationMin = this.toNumber(payload.predictedEstimationMin);
    payload.predictedEstimationMoyenne = this.toNumber(payload.predictedEstimationMoyenne);
    payload.predictedEstimationMax = this.toNumber(payload.predictedEstimationMax);
    payload.finalEstimationMin = this.toNumber(payload.finalEstimationMin);
    payload.finalEstimationMoyenne = this.toNumber(payload.finalEstimationMoyenne);
    payload.finalEstimationMax = this.toNumber(payload.finalEstimationMax);
    payload.routeurConfidence = this.toNumber(payload.routeurConfidence);
    payload.validationConfidence = this.toNumber(payload.validationConfidence);
    payload.estimateurConfidence = this.toNumber(payload.estimateurConfidence);

    this.feedbackForm = payload;
    return payload;
  }

  private prefillFromAgentResults(results: AgentResult[]): void {
    const routeur = results.find(r => r.agentName === 'AgentRouteur');
    const validateur = results.find(r => r.agentName === 'AgentValidateur');
    const estimateur = results.find(r => r.agentName === 'AgentEstimateur');

    const routeurJson = this.safeParseJson(routeur?.rawLlmResponse);
    const validateurJson = this.safeParseJson(validateur?.rawLlmResponse);
    const estimateurJson = this.safeParseJson(estimateur?.rawLlmResponse);

    const predictedType =
      routeurJson?.type ||
      this.extractTypeFromConclusion(routeur?.conclusion) ||
      '';

    const predictedDecision =
      validateurJson?.decision ||
      this.extractDecisionFromConclusion(validateur?.conclusion) ||
      '';

    const estimationFromConclusion = this.extractEstimationFromConclusion(estimateur?.conclusion);

    this.feedbackForm.predictedType = predictedType;
    this.feedbackForm.routeurConfidence = this.toNumber(
      routeurJson?.confidence ?? routeur?.confidenceScore ?? 0
    );
    this.feedbackForm.finalType = predictedType;

    this.feedbackForm.predictedDecision = predictedDecision;
    this.feedbackForm.validationConfidence = this.toNumber(
      validateurJson?.confidence ?? validateur?.confidenceScore ?? 0
    );
    this.feedbackForm.finalDecision = predictedDecision;

    this.feedbackForm.predictedEstimationMin = this.toNumber(
      estimateurJson?.estimationMin ?? estimationFromConclusion.min ?? 0
    );
    this.feedbackForm.predictedEstimationMoyenne = this.toNumber(
      estimateurJson?.estimationMoyenne ?? estimationFromConclusion.moyenne ?? 0
    );
    this.feedbackForm.predictedEstimationMax = this.toNumber(
      estimateurJson?.estimationMax ?? estimationFromConclusion.max ?? 0
    );
    this.feedbackForm.estimateurConfidence = this.toNumber(
      estimateurJson?.confidence ?? estimateur?.confidenceScore ?? 0
    );

    this.feedbackForm.finalEstimationMin = this.feedbackForm.predictedEstimationMin;
    this.feedbackForm.finalEstimationMoyenne = this.feedbackForm.predictedEstimationMoyenne;
    this.feedbackForm.finalEstimationMax = this.feedbackForm.predictedEstimationMax;

    this.feedbackForm.routeurCorrect = null;
    this.feedbackForm.validationCorrect = null;
    this.feedbackForm.estimateurCorrect = null;
  }

  private prefillFromExistingFeedback(feedback: any): void {
    this.feedbackForm = {
      claimId: feedback.claim?.id ?? feedback.claimId ?? this.selectedClaim?.id ?? 0,
      reviewedBy: feedback.reviewedBy ?? '',
      useForLearning: feedback.useForLearning ?? true,
      satisfactionScore: feedback.satisfactionScore ?? 5,
      globalComment: feedback.globalComment ?? '',

      predictedType: feedback.predictedType ?? '',
      routeurConfidence: this.toNumber(feedback.routeurConfidence ?? 0),
      routeurCorrect: feedback.routeurCorrect ?? null,
      finalType: feedback.finalType ?? '',
      routeurComment: feedback.routeurComment ?? '',

      predictedDecision: feedback.predictedDecision ?? '',
      validationConfidence: this.toNumber(feedback.validationConfidence ?? 0),
      validationCorrect: feedback.validationCorrect ?? null,
      finalDecision: feedback.finalDecision ?? '',
      validationComment: feedback.validationComment ?? '',

      predictedEstimationMin: this.toNumber(feedback.predictedEstimationMin ?? 0),
      predictedEstimationMoyenne: this.toNumber(feedback.predictedEstimationMoyenne ?? 0),
      predictedEstimationMax: this.toNumber(feedback.predictedEstimationMax ?? 0),
      estimateurConfidence: this.toNumber(feedback.estimateurConfidence ?? 0),
      estimateurCorrect: feedback.estimateurCorrect ?? null,
      estimateEvaluation: feedback.estimateEvaluation ?? 'CORRECTE',
      finalEstimationMin: this.toNumber(feedback.finalEstimationMin ?? 0),
      finalEstimationMoyenne: this.toNumber(feedback.finalEstimationMoyenne ?? 0),
      finalEstimationMax: this.toNumber(feedback.finalEstimationMax ?? 0),
      estimateurComment: feedback.estimateurComment ?? ''
    };

    this.gestionnairComment = feedback.globalComment ?? '';
  }

  private resetFeedbackForm(claimId: number): void {
    this.feedbackForm = this.createEmptyFeedbackForm(claimId);
  }

  private createEmptyFeedbackForm(claimId: number): ExpertFeedbackRequest {
    return {
      claimId,
      reviewedBy: '',
      useForLearning: true,
      satisfactionScore: 5,
      globalComment: '',

      predictedType: '',
      routeurConfidence: 0,
      routeurCorrect: null,
      finalType: '',
      routeurComment: '',

      predictedDecision: '',
      validationConfidence: 0,
      validationCorrect: null,
      finalDecision: '',
      validationComment: '',

      predictedEstimationMin: 0,
      predictedEstimationMoyenne: 0,
      predictedEstimationMax: 0,
      estimateurConfidence: 0,
      estimateurCorrect: null,
      estimateEvaluation: 'CORRECTE',
      finalEstimationMin: 0,
      finalEstimationMoyenne: 0,
      finalEstimationMax: 0,
      estimateurComment: ''
    };
  }

  private safeParseJson(value?: string): any {
    if (!value) return null;

    try {
      return JSON.parse(value);
    } catch {
      try {
        const cleaned = value
          .replace(/<think>[\s\S]*?<\/think>/g, '')
          .replace(/```json/g, '')
          .replace(/```/g, '')
          .trim();

        const start = cleaned.indexOf('{');
        const end = cleaned.lastIndexOf('}');

        if (start >= 0 && end > start) {
          return JSON.parse(cleaned.substring(start, end + 1));
        }

        return null;
      } catch {
        return null;
      }
    }
  }

  private extractTypeFromConclusion(conclusion?: string): string {
    const value = (conclusion || '').toUpperCase();

    if (value.includes('AUTO')) return 'AUTO';
    if (value.includes('HABITATION')) return 'HABITATION';
    if (value.includes('SANTE') || value.includes('SANTÉ')) return 'SANTE';
    if (value.includes('VOYAGE')) return 'VOYAGE';

    return '';
  }

  private extractDecisionFromConclusion(conclusion?: string): string {
    const value = (conclusion || '').toUpperCase();

    if (value.includes('COUVERT')) return 'COUVERT';
    if (value.includes('EXCLU')) return 'EXCLU';
    if (value.includes('INCONNU')) return 'INCONNU';

    return '';
  }

  private extractEstimationFromConclusion(conclusion?: string): {
    min: number | null;
    moyenne: number | null;
    max: number | null;
  } {
    const text = conclusion || '';

    const minMatch = text.match(/min\s*:\s*([0-9]+(?:[.,][0-9]+)?)/i);
    const moyenneMatch = text.match(/moyenne\s*:\s*([0-9]+(?:[.,][0-9]+)?)/i);
    const maxMatch = text.match(/max\s*:\s*([0-9]+(?:[.,][0-9]+)?)/i);

    return {
      min: minMatch ? this.toNumber(minMatch[1]) : null,
      moyenne: moyenneMatch ? this.toNumber(moyenneMatch[1]) : null,
      max: maxMatch ? this.toNumber(maxMatch[1]) : null
    };
  }

  private sameText(a?: string, b?: string): boolean {
    const left = (a || '').trim().toUpperCase();
    const right = (b || '').trim().toUpperCase();

    if (!left || !right) return false;

    return left === right;
  }

  private toNumber(value: unknown): number {
    if (value === null || value === undefined || value === '') {
      return 0;
    }

    const parsed = Number(String(value).replace(',', '.'));
    return Number.isFinite(parsed) ? parsed : 0;
  }

  closePanel(event: MouseEvent): void {
    const overlay = event.target as HTMLElement;

    if (overlay.classList.contains('detail-overlay')) {
      this.closeSelectedClaim();
    }
  }

  private closeSelectedClaim(): void {
    this.selectedClaim = null;
    this.claimDetails = null;
    this.reviewData = null;
    this.agentResults = [];
    this.gestionnairComment = '';
  }

  get imageDocuments(): ClaimDocument[] {
    return (this.claimDetails?.documents || []).filter(
      (doc) => !!doc.fileType && doc.fileType.startsWith('image/')
    );
  }

  get pdfDocuments(): ClaimDocument[] {
    return (this.claimDetails?.documents || []).filter(
      (doc) => doc.fileType === 'application/pdf'
    );
  }

  buildFileUrl(filePath: string): string {
    if (!filePath) return '';

    const normalized = filePath.replace(/\\/g, '/');
    const fileName = normalized.split('/').pop();

    return `http://localhost:8080/uploads/${fileName}`;
  }

  getSafePdfUrl(filePath: string): SafeResourceUrl {
    return this.sanitizer.bypassSecurityTrustResourceUrl(
      this.buildFileUrl(filePath)
    );
  }

  private removeClaim(id: number): void {
    this.pendingClaims = this.pendingClaims.filter(c => c.id !== id);
  }

  private showToast(message: string, type: 'success' | 'error'): void {
    this.toast = {
      visible: true,
      message,
      type
    };

    setTimeout(() => {
      this.toast.visible = false;
    }, 3500);
  }
}
