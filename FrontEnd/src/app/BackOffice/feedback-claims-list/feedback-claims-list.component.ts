import { Component, OnInit } from '@angular/core';
import { CommonModule, DatePipe } from '@angular/common';
import { Router, RouterModule } from '@angular/router';
import { Claim } from '../../models/Claim/claim.model';
import { ClaimService } from '../../services/claim.service';

interface NavItem {
  label: string;
  route: string;
}

@Component({
  selector: 'app-feedback-claims-list',
  standalone: true,
  imports: [CommonModule, RouterModule, DatePipe],
  templateUrl: './feedback-claims-list.component.html',
  styleUrls: ['./feedback-claims-list.component.css']
})
export class FeedbackClaimsListComponent implements OnInit {
  claims: Claim[] = [];
  loading = true;
  errorMessage = '';

  navItems: NavItem[] = [
    { label: 'Espace expert', route: '/expert-space' },
    { label: 'Validation humaine', route: '/HumanValidationList' },
    { label: 'Feedback expert', route: '/ExpertFeedbackList' },
    { label: 'Dossiers', route: '/AdminClaimsList' }
  ];

  constructor(
    private claimService: ClaimService,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.loadClaims();
  }

  loadClaims(): void {
    this.loading = true;
    this.errorMessage = '';

    this.claimService.getAllClaims().subscribe({
      next: (data) => {
        this.claims = data || [];
        this.loading = false;
      },
      error: (err) => {
        console.error(err);
        this.errorMessage = 'Erreur lors du chargement des dossiers.';
        this.loading = false;
      }
    });
  }

  openFeedback(claimId: number): void {
    this.router.navigate(['/expert-feedback', claimId]);
  }

  getClientFullName(claim: Claim): string {
    const firstName = claim.policy?.client?.firstName || '';
    const lastName = claim.policy?.client?.lastName || '';
    return `${firstName} ${lastName}`.trim() || 'Client non renseigné';
  }

  isPendingValidation(claim: Claim): boolean {
    return claim.status === 'PENDING_VALIDATION';
  }

  isActiveNav(route: string): boolean {
    return this.router.url === route;
  }

  get totalClaims(): number {
    return this.claims.length;
  }

  get pendingClaims(): number {
    return this.claims.filter(claim => claim.status === 'PENDING_VALIDATION').length;
  }

  get processedClaims(): number {
    return this.claims.filter(claim => claim.status !== 'PENDING_VALIDATION').length;
  }

  get reviewLabel(): string {
    return this.pendingClaims > 0
      ? `${this.pendingClaims} dossier(s) à traiter`
      : 'Aucun dossier urgent';
  }

  getStatusLabel(status: string): string {
    const map: Record<string, string> = {
      PENDING_VALIDATION: 'En attente expert',
      APPROVED: 'Approuvé',
      REJECTED: 'Rejeté',
      CLOSED: 'Clôturé',
      IN_ANALYSIS: 'En analyse',
      SUBMITTED: 'Soumis'
    };

    return map[status] || status;
  }

  getStatusClass(status: string): string {
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
}
