import { Component, OnInit } from '@angular/core';
import { CommonModule, DatePipe } from '@angular/common';
import { Router, RouterModule } from '@angular/router';
import { ClaimService } from '../../services/claim.service';
import { Claim } from '../../models/Claim/claim.model';
import { AuthService } from '../../services/auth.service';

interface NavItem {
  label: string;
  route: string;
}

@Component({
  selector: 'app-consultation-decisions',
  standalone: true,
  imports: [CommonModule, DatePipe, RouterModule],
  templateUrl: './consultation-decisions.component.html',
  styleUrls: ['./consultation-decisions.component.css']
})
export class ConsultationDecisionsComponent implements OnInit {
  navItems: NavItem[] = [
    { label: 'Tableau de bord', route: '/Client_Space' },
    { label: 'Mes contrats', route: '/PolicesList' },
    { label: 'Sinistres', route: '/Claim_Home' },
    { label: 'Mes dossiers', route: '/Consulter' }
  ];

  allClaims: Claim[] = [];
  claims: Claim[] = [];

  loading = true;
  errorMessage = '';

  constructor(
    private claimService: ClaimService,
    private authService: AuthService,
    private router: Router
  ) {}

  ngOnInit(): void {
    if (!this.authService.isLoggedIn()) {
      this.router.navigate(['/login']);
      return;
    }

    const email = this.getStoredEmail();

    if (!email) {
      this.loading = false;
      this.errorMessage = 'Utilisateur non identifié. Veuillez vous reconnecter.';
      return;
    }

    this.loadClaims(email);
  }

  loadClaims(email: string): void {
    this.loading = true;
    this.errorMessage = '';

    this.claimService.getAllClaims().subscribe({
      next: (data) => {
        this.allClaims = data || [];

        const connectedEmail = email.toLowerCase().trim();

        this.claims = this.allClaims.filter((claim) => {
          const policyClientEmail = claim.policy?.client?.email
            ?.toLowerCase()
            .trim();

          const directClientEmail = (claim as any).client?.email
            ?.toLowerCase()
            .trim();

          return (
            policyClientEmail === connectedEmail ||
            directClientEmail === connectedEmail
          );
        });

        this.loading = false;
      },
      error: (err) => {
        console.error('Erreur lors du chargement des dossiers :', err);
        this.errorMessage = 'Impossible de charger vos dossiers.';
        this.loading = false;
      }
    });
  }

  getStoredEmail(): string | null {
    return localStorage.getItem('email');
  }

  isActiveNav(route: string): boolean {
    return this.router.url === route;
  }

  get totalClaims(): number {
    return this.claims.length;
  }

  get approvedClaims(): number {
    return this.claims.filter((claim) => claim.status === 'APPROVED').length;
  }

  get pendingClaims(): number {
    return this.claims.filter(
      (claim) =>
        claim.status === 'PENDING_VALIDATION' ||
        claim.status === 'IN_ANALYSIS' ||
        claim.status === 'SUBMITTED'
    ).length;
  }

  get rejectedClaims(): number {
    return this.claims.filter((claim) => claim.status === 'REJECTED').length;
  }

  get closedClaims(): number {
    return this.claims.filter((claim) => claim.status === 'CLOSED').length;
  }

  get sortedClaims(): Claim[] {
    return [...this.claims].sort((a, b) => {
      return (b.id || 0) - (a.id || 0);
    });
  }

  formatStatus(status: string): string {
    const map: Record<string, string> = {
      SUBMITTED: 'Soumis',
      IN_ANALYSIS: 'En analyse',
      PENDING_VALIDATION: 'En attente',
      APPROVED: 'Approuvé',
      REJECTED: 'Rejeté',
      CLOSED: 'Clôturé'
    };

    return map[status] || status;
  }

  getStatusClass(status: string): string {
    switch (status) {
      case 'APPROVED':
        return 'approved';
      case 'REJECTED':
        return 'rejected';
      case 'PENDING_VALIDATION':
      case 'IN_ANALYSIS':
        return 'pending';
      case 'CLOSED':
        return 'closed';
      default:
        return 'submitted';
    }
  }

  getClientFullName(claim: Claim): string {
    const firstName = claim.policy?.client?.firstName || '';
    const lastName = claim.policy?.client?.lastName || '';
    const fullName = `${firstName} ${lastName}`.trim();

    return fullName || 'Client non renseigné';
  }

  getClaimTone(claim: Claim): string {
    const type = (claim.policy?.type || '').toUpperCase();

    switch (type) {
      case 'AUTO':
        return 'tone-auto';
      case 'SANTE':
        return 'tone-health';
      case 'HABITATION':
        return 'tone-home';
      default:
        return 'tone-default';
    }
  }

  goToReport(claim: Claim): void {
    this.router.navigate(['/claim-report', claim.id]);
  }

  goToHome(): void {
    this.router.navigate(['/']);
  }

  goToDecisions(): void {
    this.router.navigate(['/Consulter']);
  }

  goToPolice(): void {
    this.router.navigate(['/PolicesList']);
  }

  goToClaimsHome(): void {
    this.router.navigate(['/Claim_Home']);
  }

  logout(): void {
    this.authService.logout();
    this.router.navigate(['/login']);
  }
}
