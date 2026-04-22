import { Component, OnInit } from '@angular/core';
import { CommonModule, DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ClaimService } from '../../services/claim.service';
import { Claim } from '../../models/Claim/claim.model';
import {SidebarComponent} from "../../components/sidebar/sidebar.component";
import {TopbarComponent} from "../../components/topbar/topbar.component";


@Component({
  selector: 'app-dossier-sinistre',
  standalone: true,
  imports: [CommonModule, FormsModule, DatePipe, SidebarComponent, TopbarComponent],
  templateUrl: './dossier-sinistre.component.html',
  styleUrls: ['./dossier-sinistre.component.css']
})
export class DossierSinistreComponent implements OnInit {

  claims:         Claim[] = [];
  filteredClaims: Claim[] = [];
  loading    = true;
  searchTerm = '';

  constructor(private claimService: ClaimService) {}

  ngOnInit(): void {
    this.loadClaims();
  }

  loadClaims(): void {
    this.claimService.getAllClaims().subscribe({
      next: (data) => {
        this.claims         = data;
        this.filteredClaims = data;
        this.loading        = false;
      },
      error: (err) => {
        console.error('Erreur', err);
        this.loading = false;
      }
    });
  }

  onSearch(): void {
    const term = this.searchTerm.toLowerCase().trim();
    if (!term) { this.filteredClaims = this.claims; return; }
    this.filteredClaims = this.claims.filter(c =>
      c.description?.toLowerCase().includes(term) ||
      c.policy?.client?.firstName?.toLowerCase().includes(term) ||
      c.policy?.client?.lastName?.toLowerCase().includes(term) ||
      c.policy?.policyNumber?.toLowerCase().includes(term)
    );
  }

  getCountByStatus(status: string): number {
    return this.claims.filter(c => c.status === status).length;
  }

  getInitials(claim: Claim): string {
    const f = claim.policy?.client?.firstName || '';
    const l = claim.policy?.client?.lastName  || '';
    return ((f[0] || '') + (l[0] || '')).toUpperCase() || '?';
  }

  formatStatus(status: string): string {
    const map: Record<string, string> = {
      'SUBMITTED':          'Soumis',
      'IN_ANALYSIS':        'En analyse',
      'PENDING_VALIDATION': 'En attente',
      'APPROVED':           'Approuvé',
      'REJECTED':           'Rejeté',
      'CLOSED':             'Clôturé'
    };
    return map[status] || status;
  }
}
