import { Component, OnInit } from '@angular/core';
import { CommonModule, DatePipe } from '@angular/common';
import { Router, RouterModule } from '@angular/router';
import { Claim } from '../models/Claim/claim.model';
import { ClaimService } from '../services/claim.service';

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
}
