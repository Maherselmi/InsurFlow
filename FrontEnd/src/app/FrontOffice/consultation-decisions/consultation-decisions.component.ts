import { Component, OnInit } from '@angular/core';
import { CommonModule, DatePipe } from '@angular/common';
import { Router, RouterModule } from '@angular/router';
import { ClaimService } from '../../services/claim.service';
import { Claim } from '../../models/Claim/claim.model';

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
    { label: 'Mes contrats', route: '/contrats' },
    { label: 'Sinistres', route: '/Claim_Home' },
    { label: 'Mes dossiers', route: '/Consulter' }
  ];

  claims: Claim[] = [];
  loading = true;

  constructor(
    private claimService: ClaimService,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.claimService.getAllClaims().subscribe({
      next: (data) => {
        this.claims = data || [];
        this.loading = false;
      },
      error: (err) => {
        console.error('Erreur lors du chargement des dossiers :', err);
        this.loading = false;
      }
    });
  }

  isActiveNav(route: string): boolean {
    return this.router.url === route;
  }

  get totalClaims(): number {
    return this.claims.length;
  }

  get approvedClaims(): number {
    return this.claims.filter(claim => claim.status === 'APPROVED').length;
  }

  get pendingClaims(): number {
    return this.claims.filter(
      claim =>
        claim.status === 'PENDING_VALIDATION' ||
        claim.status === 'IN_ANALYSIS' ||
        claim.status === 'SUBMITTED'
    ).length;
  }

  get rejectedClaims(): number {
    return this.claims.filter(claim => claim.status === 'REJECTED').length;
  }

  get closedClaims(): number {
    return this.claims.filter(claim => claim.status === 'CLOSED').length;
  }

  get sortedClaims(): Claim[] {
    return [...this.claims].sort((a, b) => {
      const order = ['PENDING_VALIDATION', 'IN_ANALYSIS', 'SUBMITTED', 'APPROVED', 'REJECTED', 'CLOSED'];

      const aIndex = order.indexOf(a.status || '');
      const bIndex = order.indexOf(b.status || '');

      if (aIndex !== bIndex) {
        return aIndex - bIndex;
      }

      const aDate = new Date(a.createdAt || '').getTime();
      const bDate = new Date(b.createdAt || '').getTime();

      if (isNaN(aDate) && isNaN(bDate)) return 0;
      if (isNaN(aDate)) return 1;
      if (isNaN(bDate)) return -1;

      return bDate - aDate;
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
    this.router.navigate(['/login']);
  }
}
