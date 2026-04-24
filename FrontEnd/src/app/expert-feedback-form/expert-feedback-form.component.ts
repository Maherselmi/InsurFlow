import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';
import { forkJoin, of, switchMap } from 'rxjs';
import { catchError } from 'rxjs/operators';

import { Claim, ClaimDocument } from '../models/Claim/claim.model';
import { AgentResult } from '../models/agent-result.model';
import { ExpertFeedbackRequest } from '../models/expert-feedback.model';

import { AgentResultService } from '../services/agent-result.service';
import { ExpertFeedbackService } from '../services/expert-feedback.service';
import { ClaimService } from '../services/claim.service';
import { ClaimValidationService, ReviewData } from '../services/Claim Validation';

interface NavItem {
  label: string;
  route: string;
}

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
  actionType: 'approve' | 'reject' | null = null;

  errorMessage = '';
  successMessage = '';

  decisionComment = '';

  navItems: NavItem[] = [
    { label: 'Espace expert', route: '/Expert_Space' },
    { label: 'Validation humaine', route: '/HumanValidationList' },
    { label: 'Feedback expert', route: '/FeedbackClaimsList' },
    { label: 'Dossiers', route: '/AdminClaimsList' }
  ];

  feedbackForm: ExpertFeedbackRequest = {
    claimId: 0,
    reviewedBy: '',
    useForLearning: true,
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
    estimateEvaluation: 'CORRECTE',
    finalEstimationMin: 0,
    finalEstimationMoyenne: 0,
    finalEstimationMax: 0,
    estimateurComment: ''
  };

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
      (doc) => !!doc.fileType && doc.fileType.startsWith('image/')
    );
  }

  get pdfDocuments(): ClaimDocument[] {
    return (this.claim?.documents || []).filter(
      (doc) => doc.fileType === 'application/pdf'
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
      case 'PENDING_VALIDATION':
        return 'pending';
      case 'APPROVED':
        return 'approved';
      case 'REJECTED':
        return 'rejected';
      case 'CLOSED':
        return 'closed';
      default:
        return 'default';
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

        this.prefillFromAgentResults(this.agentResults);

        if (feedback) {
          this.prefillFromExistingFeedback(feedback);
        }

        this.loading = false;
      },
      error: (err) => {
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

    this.feedbackForm.claimId = this.claimId;
    this.feedbackForm.globalComment = this.feedbackForm.globalComment || this.decisionComment;

    this.expertFeedbackService.saveFeedback(this.feedbackForm).subscribe({
      next: () => {
        this.saving = false;
        this.successMessage = 'Feedback expert enregistré avec succès.';
      },
      error: (err) => {
        console.error(err);
        this.saving = false;
        this.errorMessage = 'Erreur lors de l’enregistrement du feedback.';
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
    if (!this.claim || this.actionLoading) return;

    this.actionLoading = true;
    this.actionType = action;
    this.errorMessage = '';
    this.successMessage = '';

    this.feedbackForm.claimId = this.claimId;
    this.feedbackForm.globalComment = this.feedbackForm.globalComment || this.decisionComment;

    this.expertFeedbackService.saveFeedback(this.feedbackForm).pipe(
      switchMap(() => {
        if (action === 'approve') {
          return this.claimValidationService.approveClaim(this.claimId, this.decisionComment);
        }
        return this.claimValidationService.rejectClaim(this.claimId, this.decisionComment);
      })
    ).subscribe({
      next: (res) => {
        if (this.claim) {
          this.claim.status = res.status;
        }

        this.actionLoading = false;
        this.actionType = null;
        this.successMessage =
          action === 'approve'
            ? `Dossier #${res.claimId} approuvé avec feedback enregistré.`
            : `Dossier #${res.claimId} rejeté avec feedback enregistré.`;
      },
      error: (err) => {
        console.error(err);
        this.actionLoading = false;
        this.actionType = null;
        this.errorMessage = 'Erreur lors du traitement du dossier.';
      }
    });
  }

  back(): void {
    this.router.navigate(['/FeedbackClaimsList']);
  }

  buildFileUrl(filePath: string): string {
    if (!filePath) return '';
    const normalized = filePath.replace(/\\/g, '/');
    const fileName = normalized.split('/').pop();
    return `http://localhost:8080/uploads/${fileName}`;
  }

  getSafePdfUrl(filePath: string): SafeResourceUrl {
    return this.sanitizer.bypassSecurityTrustResourceUrl(this.buildFileUrl(filePath));
  }

  prefillFromAgentResults(results: AgentResult[]): void {
    const routeur = results.find(r => r.agentName === 'AgentRouteur');
    const validateur = results.find(r => r.agentName === 'AgentValidateur');
    const estimateur = results.find(r => r.agentName === 'AgentEstimateur');

    const routeurJson = this.safeParseJson(routeur?.rawLlmResponse);
    const validateurJson = this.safeParseJson(validateur?.rawLlmResponse);
    const estimateurJson = this.safeParseJson(estimateur?.rawLlmResponse);

    this.feedbackForm.predictedType = routeurJson?.type || '';
    this.feedbackForm.routeurConfidence = routeurJson?.confidence || routeur?.confidenceScore || 0;
    this.feedbackForm.finalType = routeurJson?.type || '';

    this.feedbackForm.predictedDecision = validateurJson?.decision || validateur?.conclusion || '';
    this.feedbackForm.validationConfidence = validateurJson?.confidence || validateur?.confidenceScore || 0;
    this.feedbackForm.finalDecision = validateurJson?.decision || validateur?.conclusion || '';

    this.feedbackForm.predictedEstimationMin = estimateurJson?.estimationMin || 0;
    this.feedbackForm.predictedEstimationMoyenne = estimateurJson?.estimationMoyenne || 0;
    this.feedbackForm.predictedEstimationMax = estimateurJson?.estimationMax || 0;
    this.feedbackForm.estimateurConfidence = estimateurJson?.confidence || estimateur?.confidenceScore || 0;

    this.feedbackForm.finalEstimationMin = this.feedbackForm.predictedEstimationMin;
    this.feedbackForm.finalEstimationMoyenne = this.feedbackForm.predictedEstimationMoyenne;
    this.feedbackForm.finalEstimationMax = this.feedbackForm.predictedEstimationMax;
  }

  prefillFromExistingFeedback(feedback: any): void {
    this.feedbackForm = {
      claimId: feedback.claim?.id || this.claimId,
      reviewedBy: feedback.reviewedBy || '',
      useForLearning: feedback.useForLearning ?? true,
      globalComment: feedback.globalComment || '',

      predictedType: feedback.predictedType || '',
      routeurConfidence: feedback.routeurConfidence || 0,
      routeurCorrect: feedback.routeurCorrect,
      finalType: feedback.finalType || '',
      routeurComment: feedback.routeurComment || '',

      predictedDecision: feedback.predictedDecision || '',
      validationConfidence: feedback.validationConfidence || 0,
      validationCorrect: feedback.validationCorrect,
      finalDecision: feedback.finalDecision || '',
      validationComment: feedback.validationComment || '',

      predictedEstimationMin: feedback.predictedEstimationMin || 0,
      predictedEstimationMoyenne: feedback.predictedEstimationMoyenne || 0,
      predictedEstimationMax: feedback.predictedEstimationMax || 0,
      estimateurConfidence: feedback.estimateurConfidence || 0,
      estimateEvaluation: feedback.estimateEvaluation || 'CORRECTE',
      finalEstimationMin: feedback.finalEstimationMin || 0,
      finalEstimationMoyenne: feedback.finalEstimationMoyenne || 0,
      finalEstimationMax: feedback.finalEstimationMax || 0,
      estimateurComment: feedback.estimateurComment || ''
    };

    this.decisionComment = feedback.globalComment || '';
  }

  private safeParseJson(value?: string): any {
    if (!value) return null;

    try {
      return JSON.parse(value);
    } catch {
      return null;
    }
  }
}
