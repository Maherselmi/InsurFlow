import { Component, OnInit } from '@angular/core';
import { CommonModule, DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';

import { ClaimService } from '../../services/claim.service';
import { Claim } from '../../models/Claim/claim.model';
import { SidebarComponent } from '../../BackOffice/sidebar/sidebar.component';
import { TopbarComponent } from '../../BackOffice/topbar/topbar.component';

@Component({
  selector: 'app-dossier-sinistre',
  standalone: true,
  imports: [CommonModule, FormsModule, DatePipe, SidebarComponent, TopbarComponent],
  templateUrl: './dossier-sinistre.component.html',
  styleUrls: ['./dossier-sinistre.component.css']
})
export class DossierSinistreComponent implements OnInit {

  claims: Claim[] = [];
  filteredClaims: Claim[] = [];
  loading = true;
  searchTerm = '';

  constructor(
    private claimService: ClaimService,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.loadClaims();
  }

  loadClaims(): void {
    this.loading = true;

    this.claimService.getAllClaims().subscribe({
      next: (data) => {
        this.claims = data || [];
        this.filteredClaims = [...this.claims];
        this.loading = false;
      },
      error: (err) => {
        console.error('Erreur chargement dossiers', err);
        this.loading = false;
      }
    });
  }

  onSearch(): void {
    const term = this.searchTerm.toLowerCase().trim();

    if (!term) {
      this.filteredClaims = [...this.claims];
      return;
    }

    this.filteredClaims = this.claims.filter((claim) =>
      claim.description?.toLowerCase().includes(term) ||
      claim.policy?.client?.firstName?.toLowerCase().includes(term) ||
      claim.policy?.client?.lastName?.toLowerCase().includes(term) ||
      claim.policy?.policyNumber?.toLowerCase().includes(term) ||
      claim.policy?.type?.toLowerCase().includes(term) ||
      claim.status?.toLowerCase().includes(term)
    );
  }

  getCountByStatus(status: string): number {
    return this.claims.filter(claim => claim.status === status).length;
  }

  getInitials(claim: Claim): string {
    const firstName = claim.policy?.client?.firstName || '';
    const lastName = claim.policy?.client?.lastName || '';
    return ((firstName[0] || '') + (lastName[0] || '')).toUpperCase() || '?';
  }

  getClientFullName(claim: Claim): string {
    const firstName = claim.policy?.client?.firstName || '';
    const lastName = claim.policy?.client?.lastName || '';
    return `${firstName} ${lastName}`.trim() || 'Client non renseigné';
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
        return 'status-approved';
      case 'REJECTED':
        return 'status-rejected';
      case 'PENDING_VALIDATION':
        return 'status-pending';
      case 'IN_ANALYSIS':
      case 'SUBMITTED':
        return 'status-progress';
      case 'CLOSED':
        return 'status-closed';
      default:
        return 'status-progress';
    }
  }

  get pendingCount(): number {
    return this.getCountByStatus('PENDING_VALIDATION');
  }

  get approvedCount(): number {
    return this.getCountByStatus('APPROVED');
  }

  get rejectedCount(): number {
    return this.getCountByStatus('REJECTED');
  }

  get inAnalysisCount(): number {
    return this.getCountByStatus('IN_ANALYSIS');
  }

  onNewClaim(): void {
    this.router.navigate(['/dossiers']);
  }
}
