import { Component, OnInit, Inject, PLATFORM_ID } from '@angular/core';
import { CommonModule, isPlatformBrowser } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterModule } from '@angular/router';
import { ClaimData, ClaimService, Policy } from '../../../claim.service';

interface NavItem {
  label: string;
  route: string;
}

interface StepTip {
  title: string;
  text: string;
}

@Component({
  selector: 'app-claim-step1',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule],
  templateUrl: './claim-step1.component.html',
  styleUrls: ['./claim-step1.component.css'],
  host: { ngSkipHydration: 'true' }
})
export class ClaimStep1Component implements OnInit {
  navItems: NavItem[] = [
    { label: 'Tableau de bord', route: '/Client_Space' },
    { label: 'Mes contrats', route: '/contrats' },
    { label: 'Sinistres', route: '/Claim_Home' },
    { label: 'Mes dossiers', route: '/Consulter' }
  ];

  autoClaimTypes = [
    { value: 'ACCIDENT_RESPONSABLE', label: 'Accident responsable' },
    { value: 'ACCIDENT_NON_RESPONSABLE', label: 'Accident non responsable' },
    { value: 'BRIS_GLACE', label: 'Bris de glace' },
    { value: 'INCENDIE', label: 'Incendie / Explosion' },
    { value: 'VOL', label: 'Vol / Vandalisme' },
    { value: 'CATASTROPHE', label: 'Catastrophe naturelle' }
  ];

  stepTips: StepTip[] = [
    {
      title: 'Déclarez rapidement',
      text: 'Renseignez les informations essentielles de l’accident pour lancer le dossier.'
    },
    {
      title: 'Police vérifiée',
      text: 'Votre contrat auto est contrôlé avant de passer à l’étape suivante.'
    },
    {
      title: 'Parcours guidé',
      text: 'Chaque étape est pensée pour rendre la déclaration plus simple.'
    }
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

  loadingPolicies = false;
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

  isActiveNav(route: string): boolean {
    const currentUrl = this.router.url;

    if (route === '/Claim_Home') {
      return (
          currentUrl.startsWith('/Claim_Home') ||
          currentUrl.startsWith('/claim') ||
          currentUrl.startsWith('/Sante') ||
          currentUrl.startsWith('/Habitation')
      );
    }

    return currentUrl === route;
  }

  loadPolicies(): void {
    this.loadingPolicies = true;
    this.errorMessage = '';

    const email = isPlatformBrowser(this.platformId)
      ? localStorage.getItem('email')?.toLowerCase().trim()
      : null;

    if (!email) {
      this.loadingPolicies = false;
      this.errorMessage = 'Utilisateur non identifié. Veuillez vous reconnecter.';
      return;
    }

    this.claimService.getPolicies().subscribe({
      next: (res) => {
        const allPolicies = res || [];

        this.policies = allPolicies.filter((p) => {
          const policyType = this.normalizeType(p.type);
          const clientEmail = p.client?.email?.toLowerCase().trim();

          return (
            policyType === 'AUTO' &&
            clientEmail === email &&
            this.isPolicyActive(p)
          );
        });

        this.loadingPolicies = false;
      },
      error: (err) => {
        this.loadingPolicies = false;
        this.errorMessage = 'Erreur lors du chargement des polices auto.';
        console.error('Erreur chargement polices auto:', err);
      }
    });
  }

  onPolicyChange(): void {
    this.errorMessage = '';
    this.successMessage = '';

    this.selectedPolicy =
        this.policies.find((p) => p.id === Number(this.claim.policyId)) || null;

    if (!this.selectedPolicy) {
      this.claim.clientId = null;
      return;
    }

    if (this.selectedPolicy.client?.id) {
      this.claim.clientId = this.selectedPolicy.client.id;
    } else {
      this.claim.clientId = null;
      this.errorMessage = 'Client introuvable pour cette police.';
      return;
    }

    if (this.normalizeType(this.selectedPolicy.type) !== 'AUTO') {
      this.errorMessage = 'Veuillez sélectionner une police auto valide.';
      this.selectedPolicy = null;
      this.claim.policyId = null;
      this.claim.clientId = null;
      return;
    }

    if (!this.isPolicyActive(this.selectedPolicy)) {
      this.errorMessage = 'Cette police auto est expirée ou inactive.';
    }
  }

  validate(): boolean {
    this.errorMessage = '';

    if (!this.claim.policyId) {
      this.errorMessage = 'Veuillez sélectionner une police auto.';
      return false;
    }

    if (!this.selectedPolicy) {
      this.errorMessage = 'Veuillez sélectionner une police valide.';
      return false;
    }

    if (this.normalizeType(this.selectedPolicy.type) !== 'AUTO') {
      this.errorMessage = 'La police choisie doit être de type AUTO.';
      return false;
    }

    if (!this.isPolicyActive(this.selectedPolicy)) {
      this.errorMessage = 'La police sélectionnée n’est pas active.';
      return false;
    }

    if (!this.claim.clientId) {
      this.errorMessage = 'Client introuvable pour cette police.';
      return false;
    }

    if (!this.claim.incidentDate) {
      this.errorMessage = 'La date du sinistre est obligatoire.';
      return false;
    }

    const incidentDate = new Date(this.claim.incidentDate);
    const today = new Date();
    today.setHours(0, 0, 0, 0);

    if (incidentDate > today) {
      this.errorMessage = 'La date du sinistre ne peut pas être dans le futur.';
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

        this.successMessage = 'Dossier auto créé avec succès. Redirection en cours...';

        setTimeout(() => {
          this.router.navigate(['/claim/step2']);
        }, 900);
      },
      error: (err) => {
        this.loading = false;
        this.errorMessage =
            err?.message || 'Erreur serveur lors de la création du dossier.';
        console.error('Erreur création claim auto:', err);
      }
    });
  }

  getSelectedPolicyPeriod(): string {
    if (!this.selectedPolicy?.startDate || !this.selectedPolicy?.endDate) {
      return '-';
    }

    return `${this.formatDate(this.selectedPolicy.startDate)} → ${this.formatDate(this.selectedPolicy.endDate)}`;
  }

  getSelectedPolicyStatus(): string {
    if (!this.selectedPolicy) {
      return '-';
    }

    return this.isPolicyActive(this.selectedPolicy) ? 'Active' : 'Inactive';
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
