import { Component, OnInit } from '@angular/core';
import { CommonModule, DatePipe, SlicePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';

import { Claim, ClaimDocument } from '../../models/Claim/claim.model';
import { ClaimValidationService, ReviewData } from '../../services/Claim Validation';
import { ClaimService } from '../../services/claim.service';

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
  styleUrl: './claim-validation.component.css'
})
export class ClaimValidationComponent implements OnInit {

  pendingClaims: Claim[] = [];
  selectedClaim: Claim | null = null;
  reviewData: ReviewData | null = null;
  claimDetails: Claim | null = null;

  loading = false;
  reviewLoading = false;
  actionLoading = false;
  actionType: 'approve' | 'reject' | null = null;

  gestionnairComment = '';

  toast = {
    visible: false,
    message: '',
    type: 'success' as 'success' | 'error'
  };

  constructor(
    private claimValidationService: ClaimValidationService,
    private claimService: ClaimService,
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
    this.gestionnairComment = '';
    this.reviewLoading = true;

    let reviewDone = false;
    let detailsDone = false;

    const finish = () => {
      if (reviewDone && detailsDone) {
        this.reviewLoading = false;
        console.log('reviewData =', this.reviewData);
        console.log('claimDetails =', this.claimDetails);
        console.log('documents =', this.claimDetails?.documents);
      }
    };

    this.claimValidationService.getClaimReview(claim.id).subscribe({
      next: (data) => {
        this.reviewData = data;
        reviewDone = true;
        finish();
      },
      error: () => {
        reviewDone = true;
        finish();
        this.showToast('Impossible de charger le rapport', 'error');
      }
    });

    this.claimService.getClaimById(claim.id).subscribe({
      next: (data) => {
        this.claimDetails = data;
        detailsDone = true;
        finish();
      },
      error: (err) => {
        console.error('Erreur getClaimById:', err);
        detailsDone = true;
        finish();
        this.showToast('Impossible de charger les documents du dossier', 'error');
      }
    });
  }

  approveClaim(): void {
    if (!this.selectedClaim || this.actionLoading) return;
    this.actionLoading = true;
    this.actionType = 'approve';

    this.claimValidationService.approveClaim(this.selectedClaim.id, this.gestionnairComment).subscribe({
      next: (res) => {
        this.showToast(`Dossier #${res.claimId} approuvé avec succès`, 'success');
        this.removeClaim(this.selectedClaim!.id);
        this.selectedClaim = null;
        this.claimDetails = null;
        this.actionLoading = false;
        this.actionType = null;
      },
      error: () => {
        this.showToast("Erreur lors de l'approbation", 'error');
        this.actionLoading = false;
        this.actionType = null;
      }
    });
  }

  rejectClaim(): void {
    if (!this.selectedClaim || this.actionLoading) return;
    this.actionLoading = true;
    this.actionType = 'reject';

    this.claimValidationService.rejectClaim(this.selectedClaim.id, this.gestionnairComment).subscribe({
      next: (res) => {
        this.showToast(`Dossier #${res.claimId} rejeté`, 'success');
        this.removeClaim(this.selectedClaim!.id);
        this.selectedClaim = null;
        this.claimDetails = null;
        this.actionLoading = false;
        this.actionType = null;
      },
      error: () => {
        this.showToast('Erreur lors du rejet', 'error');
        this.actionLoading = false;
        this.actionType = null;
      }
    });
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
