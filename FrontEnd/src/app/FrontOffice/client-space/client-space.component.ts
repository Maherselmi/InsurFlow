import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterModule } from '@angular/router';
import { Client, ClientService } from '../../services/client.service';
import { Policy } from '../../claim.service';
import { AuthService } from '../../services/auth.service';
import { PolicyService } from '../../services/policy.service';

interface NavItem {
  label: string;
  route: string;
}

interface ClaimItem {
  status: string;
  statusClass: 'danger' | 'warning' | 'info';
  title: string;
  meta: string;
  message: string;
}

interface QuickAction {
  title: string;
  text: string;
  route: string;
  icon: 'contracts' | 'claims' | 'documents';
}

@Component({
  selector: 'app-client-space',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './client-space.component.html',
  styleUrls: ['./client-space.component.css']
})
export class ClientSpaceComponent implements OnInit {
  navItems: NavItem[] = [
    { label: 'Tableau de bord', route: '/Client_Space' },
    { label: 'Mes contrats', route: '/contrats' },
    { label: 'Sinistres', route: '/Claim_Home' },
    { label: 'Mes dossiers', route: '/Consulter' }
  ];

  quickActions: QuickAction[] = [
    {
      title: 'Mes contrats',
      text: 'Consultez vos polices et leurs échéances.',
      route: '/contrats',
      icon: 'contracts'
    },
    {
      title: 'Déclarer un sinistre',
      text: 'Lancez une nouvelle démarche en quelques étapes.',
      route: '/Claim_Home',
      icon: 'claims'
    },
    {
      title: 'Mes dossiers',
      text: 'Suivez vos décisions et rapports détaillés.',
      route: '/Consulter',
      icon: 'documents'
    }
  ];

  client: Client | null = null;
  policies: Policy[] = [];

  loadingProfile = true;
  loadingPolicies = true;

  profileError = '';
  policiesError = '';

  claims: ClaimItem[] = [
    {
      status: 'Action requise',
      statusClass: 'danger',
      title: 'Dégât des eaux — Cuisine',
      meta: 'Ouvert le 18 oct. 2024 · Habitation',
      message: 'Veuillez transmettre le devis de votre plombier pour finaliser l’évaluation.'
    },
    {
      status: 'Expertise programmée',
      statusClass: 'warning',
      title: 'Bris de glace — Pare-brise',
      meta: 'Ouvert le 02 oct. 2024 · Auto',
      message: 'Rendez-vous le 24 oct. à 09h30 chez le réparateur agréé.'
    }
  ];

  constructor(
      private authService: AuthService,
      private clientService: ClientService,
      private policyService: PolicyService,
      private router: Router
  ) {}

  ngOnInit(): void {
    if (!this.authService.isLoggedIn()) {
      this.router.navigate(['/login']);
      return;
    }

    const email = this.getStoredEmail();

    if (!email) {
      this.loadingProfile = false;
      this.loadingPolicies = false;
      this.profileError = 'Utilisateur non identifié.';
      this.policiesError = 'Impossible de charger les contrats.';
      return;
    }

    this.loadCurrentClient(email);
    this.loadPolicies(email);
  }

  loadCurrentClient(email: string): void {
    this.clientService.getAllClients().subscribe({
      next: (clients) => {
        const found = clients.find(
            (c) => c.email?.toLowerCase() === email.toLowerCase()
        );

        this.client = found ?? null;
        this.loadingProfile = false;

        if (!found) {
          this.profileError = 'Aucun profil client trouvé pour cet email.';
        }
      },
      error: () => {
        this.loadingProfile = false;
        this.profileError = 'Impossible de charger les données client.';
      }
    });
  }

  loadPolicies(email: string): void {
    this.policyService.getAllPolicies().subscribe({
      next: (policies) => {
        this.policies = policies.filter(
            (policy) => policy.client?.email?.toLowerCase() === email.toLowerCase()
        );
        this.loadingPolicies = false;
      },
      error: () => {
        this.loadingPolicies = false;
        this.policiesError = 'Impossible de charger les contrats.';
      }
    });
  }

  getStoredEmail(): string | null {
    return localStorage.getItem('email');
  }

  isActiveNav(route: string): boolean {
    return this.router.url === route;
  }

  get fullName(): string {
    if (!this.client) return 'Client InSurFlow';
    return `${this.client.firstName} ${this.client.lastName}`;
  }

  get initials(): string {
    if (!this.client) return 'CI';
    const first = this.client.firstName?.charAt(0) || '';
    const last = this.client.lastName?.charAt(0) || '';
    return `${first}${last}`.toUpperCase();
  }

  get activeContractsCount(): number {
    return this.policies.filter((policy) => this.getPolicyStatus(policy) === 'ACTIF').length;
  }

  get expiredContractsCount(): number {
    return this.policies.filter((policy) => this.getPolicyStatus(policy) === 'EXPIRÉ').length;
  }

  get activeClaimsCount(): number {
    return this.claims.length;
  }

  formatDate(date: string | undefined): string {
    if (!date) return '-';

    const d = new Date(date);
    if (isNaN(d.getTime())) return date;

    return d.toLocaleDateString('fr-FR', {
      day: '2-digit',
      month: 'short',
      year: 'numeric'
    });
  }

  getPolicyTitle(policy: Policy): string {
    const type = policy.type || 'Contrat';
    const formule = policy.formule ? ` ${policy.formule}` : '';
    return `${type}${formule}`;
  }

  getPolicySubtitle(policy: Policy): string {
    if (policy.coverageDetails && policy.coverageDetails.trim()) {
      return policy.coverageDetails;
    }

    if (policy.productCode && policy.productCode.trim()) {
      return `Code produit : ${policy.productCode}`;
    }

    return 'Contrat d’assurance enregistré';
  }

  getPolicyStatus(policy: Policy): string {
    const today = new Date();
    const end = new Date(policy.endDate);

    if (!isNaN(end.getTime()) && end < today) {
      return 'EXPIRÉ';
    }

    return 'ACTIF';
  }

  logout(): void {
    this.authService.logout();
    this.router.navigate(['/login']);
  }
}
