import { Component, OnInit, Inject, PLATFORM_ID } from '@angular/core';
import { CommonModule, isPlatformBrowser } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { ClaimService, ClaimData, Policy } from '../../../claim.service';

@Component({
  selector: 'app-claim-step1-habitation',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './claim-step1-habitation.component.html',
  styleUrls: ['./claim-step1-habitation.component.css'],
  host: { ngSkipHydration: 'true' }
})
export class ClaimStep1HabitationComponent implements OnInit {

  claim: ClaimData = {
    policyId:     null,
    clientId:     null,
    incidentDate: '',
    type:         '',
    description:  ''
  };

  policies:       Policy[]      = [];
  selectedPolicy: Policy | null = null;
  loading        = false;
  errorMessage   = '';
  successMessage = '';

  constructor(
    private claimService: ClaimService,
    private router: Router,
    @Inject(PLATFORM_ID) private platformId: Object
  ) {}

  ngOnInit(): void {
    this.loadPolicies();
  }

  loadPolicies(): void {
    this.loading = true;
    this.claimService.getPolicies().subscribe({
      next: (res) => {
        this.policies = res || [];
        this.loading  = false;
        console.log('✅ Policies chargées:', this.policies);
      },
      error: () => {
        this.loading      = false;
        this.errorMessage = 'Erreur lors du chargement des polices.';
      }
    });
  }

  onPolicyChange(): void {
    this.selectedPolicy = this.policies.find(
      p => p.id === Number(this.claim.policyId)
    ) || null;

    if (this.selectedPolicy?.client?.id) {
      this.claim.clientId = this.selectedPolicy.client.id;
      console.log('✅ ClientId:', this.claim.clientId);
    }

    this.errorMessage = '';
  }

  validate(): boolean {
    if (!this.claim.policyId) {
      this.errorMessage = 'Veuillez sélectionner une police habitation.';
      return false;
    }
    if (!this.claim.clientId) {
      this.errorMessage = 'Client introuvable.';
      return false;
    }
    if (!this.claim.incidentDate) {
      this.errorMessage = 'La date du sinistre est obligatoire.';
      return false;
    }
    const date  = new Date(this.claim.incidentDate);
    const today = new Date();
    today.setHours(0, 0, 0, 0);
    if (date > today) {
      this.errorMessage = 'La date ne peut pas être dans le futur.';
      return false;
    }
    if (!this.claim.type) {
      this.errorMessage = 'Le type de sinistre est obligatoire.';
      return false;
    }
    if (!this.claim.description || this.claim.description.trim().length < 10) {
      this.errorMessage = 'La description doit contenir au moins 10 caractères.';
      return false;
    }
    return true;
  }

  next(): void {
    console.log('🚀 next() appelé');
    console.log('📋 claim:', this.claim);

    this.errorMessage   = '';
    this.successMessage = '';

    if (!this.validate()) {
      console.log('❌ Validation échouée:', this.errorMessage);
      return;
    }

    this.loading = true;

    this.claimService.createClaim(this.claim).subscribe({
      next: (res) => {
        this.loading = false;
        console.log('✅ Claim créé:', res);

        if (isPlatformBrowser(this.platformId) && res?.id) {
          localStorage.setItem('claimId', res.id.toString());
        }

        this.successMessage = 'Dossier habitation créé avec succès ! Redirection...';
        setTimeout(() => {
          this.router.navigate(['/claim/habitation/step2']);
        }, 1000);
      },
      error: (err) => {
        this.loading      = false;
        this.errorMessage = err.message || 'Erreur serveur.';
        console.error('❌ Erreur:', err);
      }
    });
  }
  goToHome(): void {
    console.log('🏠 Navigation vers la page d\'accueil');
    this.router.navigate(['/']);
  }

  /**
   * Navigation vers la page de consultation des décisions
   */
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
