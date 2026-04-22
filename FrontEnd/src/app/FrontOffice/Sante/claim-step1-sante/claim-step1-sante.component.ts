import { Component, OnInit, Inject, PLATFORM_ID } from '@angular/core';
import { CommonModule, isPlatformBrowser } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterModule } from '@angular/router';
import { ClaimData, ClaimService, Policy } from '../../../claim.service';

@Component({
  selector: 'app-claim-step1-sante',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule],
  templateUrl: './claim-step1-sante.component.html',
  styleUrls: ['./claim-step1-sante.component.css'],
  host: { ngSkipHydration: 'true' }
})
export class ClaimStep1SanteComponent implements OnInit {
  navItems = [
    { label: 'Tableau de bord', route: '/Client_Space' },
    { label: 'Mes contrats', route: '/contrats' },
    { label: 'Sinistres', route: '/Claim_Home' },
    { label: 'Documents', route: '/documents' }
  ];

  healthClaimTypes = [
    { value: 'CONSULTATION', label: 'Consultation médicale' },
    { value: 'HOSPITALISATION', label: 'Hospitalisation' },
    { value: 'DENTAIRE', label: 'Soins dentaires' },
    { value: 'OPTIQUE', label: 'Optique / Lunettes' },
    { value: 'MEDICAMENTS', label: 'Médicaments' },
    { value: 'URGENCE', label: 'Urgence médicale' },
    { value: 'MATERNITE', label: 'Maternité' }
  ];

  claim: ClaimData = {
    policyId: null,
    clientId: null,
    incidentDate: '',
    type: '',
    description: ''
  };

  policies: Policy[] = [];
  selectedPolicy: Policy | null = null;
  loading = false;
  errorMessage = '';
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
    this.errorMessage = '';

    this.claimService.getPolicies().subscribe({
      next: (res) => {
        const allPolicies = res || [];

        this.policies = allPolicies.filter(
          (p) => this.normalizeType(p.type) === 'SANTE'
        );

        this.loading = false;
      },
      error: (err) => {
        this.loading = false;
        this.errorMessage = 'Erreur lors du chargement des polices santé.';
        console.error('Erreur chargement polices santé:', err);
      }
    });
  }

  onPolicyChange(): void {
    this.errorMessage = '';
    this.successMessage = '';

    this.selectedPolicy =
      this.policies.find(p => p.id === Number(this.claim.policyId)) || null;

    if (!this.selectedPolicy) {
      this.claim.clientId = null;
      return;
    }

    if (this.selectedPolicy?.client?.id) {
      this.claim.clientId = this.selectedPolicy.client.id;
    } else {
      this.claim.clientId = null;
      this.errorMessage = 'Client introuvable pour cette police.';
      return;
    }

    if (this.normalizeType(this.selectedPolicy.type) !== 'SANTE') {
      this.errorMessage = 'Veuillez sélectionner une police santé valide.';
      this.selectedPolicy = null;
      this.claim.policyId = null;
      this.claim.clientId = null;
      return;
    }

    if (!this.isPolicyActive(this.selectedPolicy)) {
      this.errorMessage = 'Cette police santé est expirée ou inactive.';
      return;
    }
  }

  validate(): boolean {
    this.errorMessage = '';

    if (!this.claim.policyId) {
      this.errorMessage = 'Veuillez sélectionner une mutuelle.';
      return false;
    }

    if (!this.selectedPolicy) {
      this.errorMessage = 'Veuillez sélectionner une police valide.';
      return false;
    }

    if (this.normalizeType(this.selectedPolicy.type) !== 'SANTE') {
      this.errorMessage = 'La police choisie doit être de type SANTÉ.';
      return false;
    }

    if (!this.isPolicyActive(this.selectedPolicy)) {
      this.errorMessage = 'La police sélectionnée n’est pas active.';
      return false;
    }

    if (!this.claim.clientId) {
      this.errorMessage = 'Client introuvable.';
      return false;
    }

    if (!this.claim.incidentDate) {
      this.errorMessage = 'La date des soins est obligatoire.';
      return false;
    }

    const incidentDate = new Date(this.claim.incidentDate);
    const today = new Date();
    today.setHours(0, 0, 0, 0);

    if (incidentDate > today) {
      this.errorMessage = 'La date ne peut pas être dans le futur.';
      return false;
    }

    if (
      this.selectedPolicy.startDate &&
      incidentDate < new Date(this.selectedPolicy.startDate)
    ) {
      this.errorMessage =
        'La date du sinistre est antérieure au début de validité de la police.';
      return false;
    }

    if (
      this.selectedPolicy.endDate &&
      incidentDate > new Date(this.selectedPolicy.endDate)
    ) {
      this.errorMessage =
        'La date du sinistre dépasse la fin de validité de la police.';
      return false;
    }

    if (!this.claim.type || !this.claim.type.trim()) {
      this.errorMessage = 'Le type de soins est obligatoire.';
      return false;
    }

    if (!this.claim.description || this.claim.description.trim().length < 10) {
      this.errorMessage = 'La description doit contenir au moins 10 caractères.';
      return false;
    }

    return true;
  }

  next(): void {
    this.errorMessage = '';
    this.successMessage = '';

    if (!this.validate()) {
      return;
    }

    this.loading = true;

    const payload: ClaimData = {
      policyId: this.claim.policyId ? Number(this.claim.policyId) : null,
      clientId: this.claim.clientId ? Number(this.claim.clientId) : null,
      incidentDate: this.claim.incidentDate,
      type: this.claim.type.trim().toUpperCase(),
      description: this.claim.description.trim()
    };

    this.claimService.createClaim(payload).subscribe({
      next: (res) => {
        this.loading = false;

        if (isPlatformBrowser(this.platformId) && res?.id) {
          localStorage.setItem('claimId', res.id.toString());
        }

        this.successMessage = 'Dossier santé créé avec succès. Redirection en cours...';

        setTimeout(() => {
          this.router.navigate(['/Sante/step2']);
        }, 900);
      },
      error: (err) => {
        this.loading = false;
        this.errorMessage =
          err?.message || 'Erreur serveur lors de la création du dossier.';
        console.error('Erreur création claim santé:', err);
      }
    });
  }

  getSelectedPolicyPeriod(): string {
    if (!this.selectedPolicy?.startDate || !this.selectedPolicy?.endDate) {
      return '-';
    }

    return `${this.formatDate(this.selectedPolicy.startDate)} → ${this.formatDate(this.selectedPolicy.endDate)}`;
  }

  formatDate(date: string): string {
    const d = new Date(date);
    if (isNaN(d.getTime())) return date;

    return d.toLocaleDateString('fr-FR', {
      day: '2-digit',
      month: 'short',
      year: 'numeric'
    });
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

  logout(): void {
    this.router.navigate(['/login']);
  }

  private normalizeType(type: string | undefined | null): string {
    return (type || '').trim().toUpperCase();
  }

  private isPolicyActive(policy: Policy): boolean {
    if (!policy?.startDate || !policy?.endDate) {
      return false;
    }

    const today = new Date();
    today.setHours(0, 0, 0, 0);

    const start = new Date(policy.startDate);
    const end = new Date(policy.endDate);

    start.setHours(0, 0, 0, 0);
    end.setHours(0, 0, 0, 0);

    return today >= start && today <= end;
  }
}
