import { Component, OnInit } from '@angular/core';
import { ClaimService } from '../../services/claim.service';
import { Claim } from '../../models/Claim/claim.model';
import { CommonModule, DatePipe } from '@angular/common';
import { Router } from '@angular/router';

@Component({
  selector: 'app-consultation-decisions',
  standalone: true,
  imports: [
    CommonModule,
    DatePipe
  ],
  templateUrl: './consultation-decisions.component.html',
  styleUrl: './consultation-decisions.component.css'
})
export class ConsultationDecisionsComponent implements OnInit {

  claims: Claim[] = [];
  loading = true;

  selectedClaim: Claim | null = null;
  showReportModal = false;

  constructor(
    private claimService: ClaimService,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.claimService.getAllClaims().subscribe({
      next: (data) => {
        this.claims = data;
        this.loading = false;
      },
      error: (err) => {
        console.error(err);
        this.loading = false;
      }
    });
  }

  formatStatus(status: string): string {
    const map: any = {
      'SUBMITTED': 'Soumis',
      'IN_ANALYSIS': 'En analyse',
      'PENDING_VALIDATION': 'En attente',
      'APPROVED': 'Approuvé',
      'REJECTED': 'Rejeté',
      'CLOSED': 'Clôturé'
    };
    return map[status] || status;
  }

  openReport(claim: Claim): void {
    this.selectedClaim = claim;
    this.showReportModal = true;
  }

  closeReport(): void {
    this.showReportModal = false;
    this.selectedClaim = null;
  }

  goToHome(): void {
    console.log('🏠 Navigation vers la page d\'accueil');
    this.router.navigate(['/']);
  }

  goToDecisions(): void {
    console.log('📋 Navigation vers la page des décisions');
    this.router.navigate(['/Consulter']);
  }
  goToPolice(): void {
    this.router.navigate(['/PolicesList']);
  }
  logout(): void {
    this.router.navigate(['/login']);
  }
}
