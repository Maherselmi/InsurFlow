import { Component, OnInit } from '@angular/core';
import { CommonModule, DatePipe } from '@angular/common';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { ClaimService } from '../../services/claim.service';
import { Claim } from '../../models/Claim/claim.model';

interface NavItem {
  label: string;
  route: string;
}

@Component({
  selector: 'app-claim-report-page',
  standalone: true,
  imports: [CommonModule, DatePipe, RouterModule],
  templateUrl: './claim-report-page.component.html',
  styleUrls: ['./claim-report-page.component.css']
})
export class ClaimReportPageComponent implements OnInit {
  navItems: NavItem[] = [
    { label: 'Tableau de bord', route: '/Client_Space' },
    { label: 'Mes contrats', route: '/contrats' },
    { label: 'Sinistres', route: '/Claim_Home' },
    { label: 'Mes dossiers', route: '/Consulter' }
  ];

  claim: Claim | null = null;
  loading = true;
  claimId = 0;

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private claimService: ClaimService
  ) {}

  ngOnInit(): void {
    this.claimId = Number(this.route.snapshot.paramMap.get('id'));

    if (!this.claimId) {
      this.loading = false;
      return;
    }

    this.claimService.getAllClaims().subscribe({
      next: (data) => {
        const claims = data || [];
        this.claim = claims.find(c => c.id === this.claimId) || null;
        this.loading = false;
      },
      error: (err) => {
        console.error('Erreur lors du chargement du rapport :', err);
        this.loading = false;
      }
    });
  }

  isActiveNav(route: string): boolean {
    return this.router.url === route;
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

  getClientFullName(): string {
    if (!this.claim?.policy?.client) {
      return 'Client non renseigné';
    }

    const firstName = this.claim.policy.client.firstName || '';
    const lastName = this.claim.policy.client.lastName || '';
    const fullName = `${firstName} ${lastName}`.trim();

    return fullName || 'Client non renseigné';
  }



  getPolicyNumber(): string {
    return this.claim?.policy?.policyNumber || '-';
  }

  goBack(): void {
    this.router.navigate(['/Consulter']);
  }

  goToHome(): void {
    this.router.navigate(['/']);
  }

  goToPolice(): void {
    this.router.navigate(['/PolicesList']);
  }

  goToClaimHome(): void {
    this.router.navigate(['/Claim_Home']);
  }

  logout(): void {
    this.router.navigate(['/login']);
  }
}
