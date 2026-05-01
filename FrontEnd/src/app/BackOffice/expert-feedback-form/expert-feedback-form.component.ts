import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';
import { forkJoin, of, switchMap } from 'rxjs';
import { catchError } from 'rxjs/operators';

import { Claim, ClaimDocument } from '../../models/Claim/claim.model';
import { AgentResult } from '../../models/agent-result.model';
import { ExpertFeedbackRequest } from '../../models/expert-feedback.model';

import { AgentResultService } from '../../services/agent-result.service';
import { ExpertFeedbackService } from '../../services/expert-feedback.service';
import { ClaimService } from '../../services/claim.service';
import {ClaimValidationService, ReviewData} from "../../services/Claim Validation";

interface NavItem {
  label: string;
  route: string;
}

type HumanAction = 'approve' | 'reject';

@Component({
  selector: 'app-expert-feedback-form',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule],
  templateUrl: './expert-feedback-form.component.html',
  styleUrls: ['./expert-feedback-form.component.css']
})
export class ExpertFeedbackFormComponent implements OnInit {
  claimId!: number;
  claim: Claim | null = null;
  reviewData: ReviewData | null = null;
  agentResults: AgentResult[] = [];

  loading = true;
  saving = false;
  actionLoading = false;
  actionType: HumanAction | null = null;

  errorMessage = '';
  successMessage = '';
  decisionComment = '';

  navItems: NavItem[] = [
    { label: 'Espace expert', route: '/expert-space' },
    { label: 'Validation humaine', route: '/HumanValidationList' },
    { label: 'Feedback expert', route: '/feedback-claims' },
    { label: 'Dossiers', route: '/AdminClaimsList' }
  ];

  feedbackForm: ExpertFeedbackRequest = this.createEmptyFeedback();

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private claimService: ClaimService,
    private claimValidationService: ClaimValidationService,
    private agentResultService: AgentResultService,
    private expertFeedbackService: ExpertFeedbackService,
    private sanitizer: DomSanitizer
  ) {}

  ngOnInit(): void {
    this.claimId = Number(this.route.snapshot.paramMap.get('claimId'));
    this.feedbackForm.claimId = this.claimId;
    this.loadData();
  }

  get isPendingValidation(): boolean {
    return this.claim?.status === 'PENDING_VALIDATION';
  }

  get hasHumanReviewFlag(): boolean {
    return this.agentResults.some(agent => !!agent.needsHumanReview);
  }

  get shouldShowExpertDocuments(): boolean {
    return this.isPendingValidation || this.hasHumanReviewFlag;
  }

  get imageDocuments(): ClaimDocument[] {
    return (this.claim?.documents || []).filter(
      doc => !!doc.fileType && doc.fileType.startsWith('image/')
    );
  }

  get pdfDocuments(): ClaimDocument[] {
    return (this.claim?.documents || []).filter(
      doc => doc.fileType === 'application/pdf'
    );
  }

  get totalDocuments(): number {
    return this.claim?.documents?.length || 0;
  }

  get pendingLabel(): string {
    return this.isPendingValidation ? 'Validation humaine requise' : 'Feedback expert';
  }

  isActiveNav(route: string): boolean {
    return this.router.url.startsWith(route);
  }

  getStatusLabel(status?: string): string {
    const map: Record<string, string> = {
      PENDING_VALIDATION: 'En attente expert',
      APPROVED: 'Approuvé',
      REJECTED: 'Rejeté',
      CLOSED: 'Clôturé',
      IN_ANALYSIS: 'En analyse',
      SUBMITTED: 'Soumis'
    };
    return map[status || ''] || (status || '-');
  }

  getStatusClass(status?: string): string {
    switch (status) {
      case 'PENDING_VALIDATION': return 'pending';
      case 'APPROVED': return 'approved';
      case 'REJECTED': return 'rejected';
      case 'CLOSED': return 'closed';
      default: return 'default';
    }
  }

  loadData(): void {
    this.loading = true;
    this.errorMessage = '';
    this.successMessage = '';

    forkJoin({
      claim: this.claimService.getClaimById(this.claimId),
      agentResults: this.agentResultService.getResultsByClaimId(this.claimId),
      feedback: this.expertFeedbackService.getFeedbackByClaimId(this.claimId).pipe(
        catchError(() => of(null))
      ),
      review: this.claimValidationService.getClaimReview(this.claimId).pipe(
        catchError(() => of(null))
      )
    }).subscribe({
      next: ({ claim, agentResults, feedback, review }) => {
        this.claim = claim;
        this.agentResults = agentResults || [];
        this.reviewData = review;

        this.feedbackForm = this.createEmptyFeedback();
        this.feedbackForm.claimId = this.claimId;
        this.prefillExpertIdentity();
        this.prefillFromAgentResults(this.agentResults);

        if (feedback) {
          this.prefillFromExistingFeedback(feedback);
        }

        this.loading = false;
      },
      error: err => {
        console.error(err);
        this.errorMessage = 'Erreur chargement dossier.';
        this.loading = false;
      }
    });
  }

  submit(): void {
    this.saving = true;
    this.errorMessage = '';
    this.successMessage = '';

    const payload = this.preparePayload();

    this.expertFeedbackService.saveFeedback(payload).subscribe({
      next: res => {
        this.saving = false;
        const saved = res?.learningItemsSaved ?? 0;
        this.successMessage = `Feedback expert enregistré avec succès. ${saved} exemple(s) learning sauvegardé(s).`;
      },
      error: err => {
        console.error(err);
        this.saving = false;
        this.errorMessage = this.extractBackendError(err) || 'Erreur lors de l’enregistrement du feedback.';
      }
    });
  }

  approveClaim(): void {
    this.submitDecision('approve');
  }

  rejectClaim(): void {
    this.submitDecision('reject');
  }

  private submitDecision(action: HumanAction): void {
    if (!this.claim || this.actionLoading) return;

    this.actionLoading = true;
    this.actionType = action;
    this.errorMessage = '';
    this.successMessage = '';

    const payload = this.preparePayload(action);

    this.expertFeedbackService.saveFeedback(payload).pipe(
      switchMap(() => {
        const comment = this.decisionComment || payload.globalComment || '';
        return action === 'approve'
          ? this.claimValidationService.approveClaim(this.claimId, comment)
          : this.claimValidationService.rejectClaim(this.claimId, comment);
      })
    ).subscribe({
      next: res => {
        if (this.claim) {
          this.claim.status = res.status;
        }

        this.actionLoading = false;
        this.actionType = null;
        this.successMessage = action === 'approve'
          ? `Dossier #${res.claimId} approuvé avec feedback enregistré.`
          : `Dossier #${res.claimId} rejeté avec feedback enregistré.`;
      },
      error: err => {
        console.error(err);
        this.actionLoading = false;
        this.actionType = null;
        this.errorMessage = this.extractBackendError(err) || 'Erreur lors du traitement du dossier.';
      }
    });
  }

  back(): void {
    this.router.navigate(['/FeedbackClaimsList']);
  }

  buildFileUrl(filePath?: string): string {
    if (!filePath) return '';
    const normalized = filePath.replace(/\\/g, '/');
    const fileName = normalized.split('/').pop();
    return `http://localhost:8080/uploads/${fileName}`;
  }

  getSafePdfUrl(filePath?: string): SafeResourceUrl {
    return this.sanitizer.bypassSecurityTrustResourceUrl(this.buildFileUrl(filePath));
  }

  onRouteurFinalTypeChange(): void {
    this.feedbackForm.routeurCorrect = this.sameText(
      this.feedbackForm.predictedType,
      this.feedbackForm.finalType
    );
  }

  onValidationFinalDecisionChange(): void {
    this.feedbackForm.validationCorrect = this.sameText(
      this.feedbackForm.predictedDecision,
      this.feedbackForm.finalDecision
    );
  }

  onEstimateEvaluationChange(): void {
    if (this.feedbackForm.estimateEvaluation === 'CORRECTE') {
      this.feedbackForm.estimateurCorrect = true;
      this.feedbackForm.finalEstimationMin = this.feedbackForm.predictedEstimationMin;
      this.feedbackForm.finalEstimationMoyenne = this.feedbackForm.predictedEstimationMoyenne;
      this.feedbackForm.finalEstimationMax = this.feedbackForm.predictedEstimationMax;
      return;
    }

    this.feedbackForm.estimateurCorrect = false;
  }

  prefillFromAgentResults(results: AgentResult[]): void {
    const routeur = results.find(r => r.agentName === 'AgentRouteur');
    const validateur = results.find(r => r.agentName === 'AgentValidateur');
    const estimateur = results.find(r => r.agentName === 'AgentEstimateur');

    const routeurJson = this.safeParseJson(routeur?.rawLlmResponse);
    const validateurJson = this.safeParseJson(validateur?.rawLlmResponse);
    const estimateurJson = this.safeParseJson(estimateur?.rawLlmResponse);

    const predictedType = this.normalizeType(
      routeurJson?.type || this.extractTypeFromConclusion(routeur?.conclusion) || ''
    );
    this.feedbackForm.predictedType = predictedType;
    this.feedbackForm.finalType = predictedType;
    this.feedbackForm.routeurConfidence = this.toNumber(routeurJson?.confidence, routeur?.confidenceScore, 0);
    this.feedbackForm.routeurCorrect = predictedType ? true : null;

    const predictedDecision = this.normalizeDecision(
      validateurJson?.decision || validateur?.conclusion || ''
    );
    this.feedbackForm.predictedDecision = predictedDecision;
    this.feedbackForm.finalDecision = predictedDecision;
    this.feedbackForm.validationConfidence = this.toNumber(validateurJson?.confidence, validateur?.confidenceScore, 0);
    this.feedbackForm.validationCorrect = predictedDecision ? true : null;

    const parsedEstimate = this.extractEstimateFromConclusion(estimateur?.conclusion);
    this.feedbackForm.predictedEstimationMin = this.toNullableNumber(estimateurJson?.estimationMin, parsedEstimate.min);
    this.feedbackForm.predictedEstimationMoyenne = this.toNullableNumber(estimateurJson?.estimationMoyenne, parsedEstimate.moyenne);
    this.feedbackForm.predictedEstimationMax = this.toNullableNumber(estimateurJson?.estimationMax, parsedEstimate.max);
    this.feedbackForm.estimateurConfidence = this.toNumber(estimateurJson?.confidence, estimateur?.confidenceScore, 0);
    this.feedbackForm.estimateurCorrect = this.hasPredictedEstimate() ? true : null;
    this.feedbackForm.estimateEvaluation = 'CORRECTE';

    this.feedbackForm.finalEstimationMin = this.feedbackForm.predictedEstimationMin;
    this.feedbackForm.finalEstimationMoyenne = this.feedbackForm.predictedEstimationMoyenne;
    this.feedbackForm.finalEstimationMax = this.feedbackForm.predictedEstimationMax;
  }

  prefillFromExistingFeedback(feedback: any): void {
    this.feedbackForm = {
      claimId: feedback.claimId || feedback.claim?.id || this.claimId,
      reviewedBy: feedback.reviewedBy || this.feedbackForm.reviewedBy || '',
      useForLearning: feedback.useForLearning ?? true,
      satisfactionScore: this.toNullableNumber(feedback.satisfactionScore, 5),
      globalComment: feedback.globalComment || feedback.expertComment || '',

      predictedType: feedback.predictedType || this.feedbackForm.predictedType || '',
      routeurConfidence: this.toNullableNumber(feedback.routeurConfidence, this.feedbackForm.routeurConfidence),
      routeurCorrect: feedback.routeurCorrect ?? this.feedbackForm.routeurCorrect,
      finalType: feedback.finalType || this.feedbackForm.finalType || '',
      routeurComment: feedback.routeurComment || '',

      predictedDecision: feedback.predictedDecision || this.feedbackForm.predictedDecision || '',
      validationConfidence: this.toNullableNumber(feedback.validationConfidence, this.feedbackForm.validationConfidence),
      validationCorrect: feedback.validationCorrect ?? this.feedbackForm.validationCorrect,
      finalDecision: feedback.finalDecision || this.feedbackForm.finalDecision || '',
      validationComment: feedback.validationComment || '',

      predictedEstimationMin: this.toNullableNumber(feedback.predictedEstimationMin, this.feedbackForm.predictedEstimationMin),
      predictedEstimationMoyenne: this.toNullableNumber(feedback.predictedEstimationMoyenne, this.feedbackForm.predictedEstimationMoyenne),
      predictedEstimationMax: this.toNullableNumber(feedback.predictedEstimationMax, this.feedbackForm.predictedEstimationMax),
      estimateurConfidence: this.toNullableNumber(feedback.estimateurConfidence, this.feedbackForm.estimateurConfidence),
      estimateurCorrect: feedback.estimateurCorrect ?? this.feedbackForm.estimateurCorrect,
      estimateEvaluation: feedback.estimateEvaluation || this.feedbackForm.estimateEvaluation || 'CORRECTE',
      finalEstimationMin: this.toNullableNumber(feedback.finalEstimationMin, this.feedbackForm.finalEstimationMin),
      finalEstimationMoyenne: this.toNullableNumber(feedback.finalEstimationMoyenne, this.feedbackForm.finalEstimationMoyenne),
      finalEstimationMax: this.toNullableNumber(feedback.finalEstimationMax, this.feedbackForm.finalEstimationMax),
      estimateurComment: feedback.estimateurComment || ''
    };

    this.decisionComment = this.feedbackForm.globalComment || '';
  }

  private preparePayload(action?: HumanAction): ExpertFeedbackRequest {
    const payload: ExpertFeedbackRequest = {
      ...this.feedbackForm,
      claimId: this.claimId,
      reviewedBy: (this.feedbackForm.reviewedBy || '').trim(),
      globalComment: (this.feedbackForm.globalComment || this.decisionComment || '').trim(),
      satisfactionScore: this.clampSatisfaction(this.feedbackForm.satisfactionScore)
    };

    payload.finalType = this.normalizeType(payload.finalType || payload.predictedType);
    payload.finalDecision = this.normalizeDecision(payload.finalDecision || payload.predictedDecision);

    if (action === 'approve' && (!payload.finalDecision || payload.finalDecision === 'INCONNU')) {
      payload.finalDecision = 'COUVERT';
    }
    if (action === 'reject' && (!payload.finalDecision || payload.finalDecision === 'INCONNU')) {
      payload.finalDecision = 'EXCLU';
    }

    if (payload.routeurCorrect === null && payload.predictedType && payload.finalType) {
      payload.routeurCorrect = this.sameText(payload.predictedType, payload.finalType);
    }

    if (payload.validationCorrect === null && payload.predictedDecision && payload.finalDecision) {
      payload.validationCorrect = this.sameText(payload.predictedDecision, payload.finalDecision);
    }

    if (payload.estimateurCorrect === null) {
      payload.estimateurCorrect = this.resolveEstimateurCorrect(payload);
    }

    payload.routeurConfidence = this.normalizeConfidence(payload.routeurConfidence);
    payload.validationConfidence = this.normalizeConfidence(payload.validationConfidence);
    payload.estimateurConfidence = this.normalizeConfidence(payload.estimateurConfidence);

    return payload;
  }

  private createEmptyFeedback(): ExpertFeedbackRequest {
    return {
      claimId: this.claimId || 0,
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

      predictedEstimationMin: null,
      predictedEstimationMoyenne: null,
      predictedEstimationMax: null,
      estimateurConfidence: 0,
      estimateurCorrect: null,
      estimateEvaluation: 'CORRECTE',
      finalEstimationMin: null,
      finalEstimationMoyenne: null,
      finalEstimationMax: null,
      estimateurComment: ''
    };
  }

  private prefillExpertIdentity(): void {
    const stored = localStorage.getItem('username')
      || localStorage.getItem('userEmail')
      || localStorage.getItem('email')
      || '';
    this.feedbackForm.reviewedBy = stored;
  }

  private safeParseJson(value?: string): any {
    if (!value) return null;
    try {
      return JSON.parse(value);
    } catch {
      const block = this.extractJsonBlock(value);
      if (!block) return null;
      try {
        return JSON.parse(block);
      } catch {
        return null;
      }
    }
  }

  private extractJsonBlock(value: string): string | null {
    const cleaned = value
      .replace(/<think>[\s\S]*?<\/think>/g, '')
      .replace(/```json/g, '')
      .replace(/```/g, '')
      .trim();

    const start = cleaned.indexOf('{');
    const end = cleaned.lastIndexOf('}');
    if (start < 0 || end <= start) return null;
    return cleaned.substring(start, end + 1);
  }

  private extractTypeFromConclusion(conclusion?: string): string {
    const text = (conclusion || '').toUpperCase();
    if (text.includes('HABITATION')) return 'HABITATION';
    if (text.includes('SANTE')) return 'SANTE';
    if (text.includes('VOYAGE')) return 'VOYAGE';
    if (text.includes('AUTO')) return 'AUTO';
    return '';
  }

  private extractEstimateFromConclusion(conclusion?: string): { min: number | null; moyenne: number | null; max: number | null } {
    const text = conclusion || '';
    const min = this.matchMoney(text, /min\s*:\s*([0-9]+(?:[.,][0-9]+)?)/i);
    const moyenne = this.matchMoney(text, /moyenne\s*:\s*([0-9]+(?:[.,][0-9]+)?)/i);
    const max = this.matchMoney(text, /max\s*:\s*([0-9]+(?:[.,][0-9]+)?)/i);
    return { min, moyenne, max };
  }

  private matchMoney(text: string, regex: RegExp): number | null {
    const match = text.match(regex);
    if (!match?.[1]) return null;
    return this.toNullableNumber(match[1]);
  }

  private normalizeType(value?: string): string {
    const text = (value || '').toUpperCase();
    if (text.includes('HABITATION')) return 'HABITATION';
    if (text.includes('SANTE')) return 'SANTE';
    if (text.includes('VOYAGE')) return 'VOYAGE';
    if (text.includes('AUTO')) return 'AUTO';
    if (text.includes('INCONNU')) return 'INCONNU';
    return text || '';
  }

  private normalizeDecision(value?: string): string {
    const text = (value || '').toUpperCase();
    if (text.includes('COUVERT')) return 'COUVERT';
    if (text.includes('EXCLU')) return 'EXCLU';
    if (text.includes('INCONNU')) return 'INCONNU';
    return text || '';
  }

  private normalizeConfidence(value: number | null): number | null {
    if (value === null || value === undefined) return null;
    let n = Number(value);
    if (!Number.isFinite(n)) return null;
    if (n > 1 && n <= 100) n = n / 100;
    return Math.max(0, Math.min(1, n));
  }

  private clampSatisfaction(value: number | null): number | null {
    if (value === null || value === undefined) return null;
    const n = Number(value);
    if (!Number.isFinite(n)) return null;
    return Math.max(1, Math.min(5, Math.round(n)));
  }

  private resolveEstimateurCorrect(payload: ExpertFeedbackRequest): boolean | null {
    if (payload.estimateEvaluation === 'CORRECTE') return true;
    if (payload.estimateEvaluation === 'SOUS_ESTIME' || payload.estimateEvaluation === 'SUR_ESTIME') return false;
    if (!this.hasFinalEstimate(payload)) return null;
    return this.sameMoney(payload.predictedEstimationMin, payload.finalEstimationMin)
      && this.sameMoney(payload.predictedEstimationMoyenne, payload.finalEstimationMoyenne)
      && this.sameMoney(payload.predictedEstimationMax, payload.finalEstimationMax);
  }

  private hasPredictedEstimate(): boolean {
    return this.feedbackForm.predictedEstimationMin !== null
      && this.feedbackForm.predictedEstimationMoyenne !== null
      && this.feedbackForm.predictedEstimationMax !== null;
  }

  private hasFinalEstimate(payload: ExpertFeedbackRequest): boolean {
    return payload.finalEstimationMin !== null
      && payload.finalEstimationMoyenne !== null
      && payload.finalEstimationMax !== null;
  }

  private sameMoney(a: number | null, b: number | null): boolean {
    if (a === null || b === null) return false;
    return Math.abs(Number(a) - Number(b)) < 0.01;
  }

  private sameText(a?: string, b?: string): boolean {
    return (a || '').trim().toUpperCase() === (b || '').trim().toUpperCase();
  }

  private toNumber(...values: unknown[]): number {
    for (const value of values) {
      const n = this.toNullableNumber(value);
      if (n !== null) return n;
    }
    return 0;
  }

  private toNullableNumber(...values: unknown[]): number | null {
    for (const value of values) {
      if (value === null || value === undefined || value === '') continue;
      const n = Number(String(value).replace(',', '.'));
      if (Number.isFinite(n)) return n;
    }
    return null;
  }

  private extractBackendError(err: any): string {
    if (typeof err?.error === 'string') return err.error;
    if (typeof err?.error?.message === 'string') return err.error.message;
    if (typeof err?.message === 'string') return err.message;
    return '';
  }
}
