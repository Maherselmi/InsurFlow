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
import {ClaimValidationService, ReviewData} from "../../services/Claim Validation";
import {ExpertFeedbackRequest} from "../../models/expert-feedback.model";

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
        this.pendingClaims = data;
        this.loading = false;
      },
      error: () => {
        this.loading = false;
        this.showToast('Erreur lors du chargement des dossiers', 'error');
      }
    });
  }

  selectClaim(claim: Claim): void {
    this.selectedClaim = claim;
    this.reviewData = null;
    this.claimDetails = null;
    this.agentResults = [];
    this.gestionnairComment = '';
    this.reviewLoading = true;

    this.resetFeedbackForm(claim.id);

    forkJoin({
      review: this.claimValidationService.getClaimReview(claim.id),
      details: this.claimService.getClaimById(claim.id),
      agentResults: this.agentResultService.getResultsByClaimId(claim.id),
      existingFeedback: this.expertFeedbackService.getFeedbackByClaimId(claim.id).pipe(
        catchError(() => of(null))
      )
    }).subscribe({
      next: ({ review, details, agentResults, existingFeedback }) => {
        this.reviewData = review;
        this.claimDetails = details;
        this.agentResults = agentResults || [];

        this.prefillFromAgentResults(agentResults || []);

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

    this.feedbackForm.claimId = this.selectedClaim.id;
    this.feedbackForm.globalComment = this.gestionnairComment;

    this.expertFeedbackService.saveFeedback(this.feedbackForm).pipe(
      switchMap(() => {
        if (action === 'approve') {
          return this.claimValidationService.approveClaim(this.selectedClaim!.id, this.gestionnairComment);
        }
        return this.claimValidationService.rejectClaim(this.selectedClaim!.id, this.gestionnairComment);
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
        this.selectedClaim = null;
        this.claimDetails = null;
        this.reviewData = null;
        this.agentResults = [];
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

  private prefillFromAgentResults(results: AgentResult[]): void {
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

  private prefillFromExistingFeedback(feedback: any): void {
    this.feedbackForm.reviewedBy = feedback.reviewedBy || '';
    this.feedbackForm.useForLearning = feedback.useForLearning ?? true;
    this.feedbackForm.globalComment = feedback.globalComment || '';

    this.feedbackForm.predictedType = feedback.predictedType || '';
    this.feedbackForm.routeurConfidence = feedback.routeurConfidence || 0;
    this.feedbackForm.routeurCorrect = feedback.routeurCorrect;
    this.feedbackForm.finalType = feedback.finalType || '';
    this.feedbackForm.routeurComment = feedback.routeurComment || '';

    this.feedbackForm.predictedDecision = feedback.predictedDecision || '';
    this.feedbackForm.validationConfidence = feedback.validationConfidence || 0;
    this.feedbackForm.validationCorrect = feedback.validationCorrect;
    this.feedbackForm.finalDecision = feedback.finalDecision || '';
    this.feedbackForm.validationComment = feedback.validationComment || '';

    this.feedbackForm.predictedEstimationMin = feedback.predictedEstimationMin || 0;
    this.feedbackForm.predictedEstimationMoyenne = feedback.predictedEstimationMoyenne || 0;
    this.feedbackForm.predictedEstimationMax = feedback.predictedEstimationMax || 0;
    this.feedbackForm.estimateurConfidence = feedback.estimateurConfidence || 0;
    this.feedbackForm.estimateEvaluation = feedback.estimateEvaluation || 'CORRECTE';
    this.feedbackForm.finalEstimationMin = feedback.finalEstimationMin || 0;
    this.feedbackForm.finalEstimationMoyenne = feedback.finalEstimationMoyenne || 0;
    this.feedbackForm.finalEstimationMax = feedback.finalEstimationMax || 0;
    this.feedbackForm.estimateurComment = feedback.estimateurComment || '';
  }

  private resetFeedbackForm(claimId: number): void {
    this.feedbackForm = {
      claimId,
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
  }

  private safeParseJson(value?: string): any {
    if (!value) return null;
    try {
      return JSON.parse(value);
    } catch {
      return null;
    }
  }

  closePanel(event: MouseEvent): void {
    const overlay = event.target as HTMLElement;
    if (overlay.classList.contains('detail-overlay')) {
      this.selectedClaim = null;
      this.claimDetails = null;
    }
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
    return this.sanitizer.bypassSecurityTrustResourceUrl(this.buildFileUrl(filePath));
  }

  private removeClaim(id: number): void {
    this.pendingClaims = this.pendingClaims.filter(c => c.id !== id);
  }

  private showToast(message: string, type: 'success' | 'error'): void {
    this.toast = { visible: true, message, type };
    setTimeout(() => this.toast.visible = false, 3500);
  }
}
