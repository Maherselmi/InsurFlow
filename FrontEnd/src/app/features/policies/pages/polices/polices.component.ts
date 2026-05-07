import { Component, Inject, OnInit, PLATFORM_ID } from '@angular/core';
import { CommonModule, DatePipe, isPlatformBrowser } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterModule } from '@angular/router';
import { Policy } from '../../claim.service';
import { PolicyService } from '../../services/policy.service';
import { AuthService } from '../../services/auth.service';

interface NavItem {
  label: string;
  route: string;
}

@Component({
  selector: 'app-polices',
  standalone: true,
  imports: [CommonModule, FormsModule, DatePipe, RouterModule],
  templateUrl: './polices.component.html',
  styleUrls: ['./polices.component.css']
})
export class PolicesComponent implements OnInit {
  allPolicies: Policy[] = [];
  policies: Policy[] = [];
  filteredPolicies: Policy[] = [];

  loading = true;
  errorMessage = '';
  searchTerm = '';
  selectedType = 'TOUS';
  expandedPolicyId: number | null = null;

  currentClientEmail: string | null = null;

  navItems: NavItem[] = [
    { label: 'Tableau de bord', route: '/Client_Space' },
    { label: 'Mes contrats', route: '/PolicesList' },
    { label: 'Sinistres', route: '/Claim_Home' },
    { label: 'Mes dossiers', route: '/Consulter' }
  ];

  constructor(
    private policyService: PolicyService,
    private authService: AuthService,
    private router: Router,
    @Inject(PLATFORM_ID) private platformId: Object
  ) {}

  ngOnInit(): void {
    if (!this.authService.isLoggedIn()) {
      this.router.navigate(['/login']);
      return;
    }

    this.loadCurrentClientEmail();

    if (!this.currentClientEmail) {
      this.loading = false;
      this.errorMessage = 'Utilisateur non identifié. Veuillez vous reconnecter.';
      return;
    }

    this.loadPolicies();
  }

  isActiveNav(route: string): boolean {
    return this.router.url === route;
  }

  loadCurrentClientEmail(): void {
    if (!isPlatformBrowser(this.platformId)) {
      return;
    }

    this.currentClientEmail = localStorage.getItem('email');
  }

  loadPolicies(): void {
    this.loading = true;
    this.errorMessage = '';

    this.policyService.getAllPolicies().subscribe({
      next: (data) => {
        this.allPolicies = data ?? [];

        const email = this.currentClientEmail?.toLowerCase().trim();

        this.policies = this.allPolicies.filter((policy) => {
          const policyClientEmail = policy.client?.email?.toLowerCase().trim();
          return policyClientEmail === email;
        });

        this.applyFilters();
        this.loading = false;
      },
      error: (err) => {
        console.error('Erreur chargement polices', err);
        this.errorMessage = 'Impossible de charger vos polices.';
        this.loading = false;
      }
    });
  }

  applyFilters(): void {
    const term = this.searchTerm.trim().toLowerCase();

    this.filteredPolicies = this.policies.filter((policy) => {
      const matchesSearch =
        !term ||
        (policy.policyNumber ?? '').toLowerCase().includes(term) ||
        (policy.type ?? '').toLowerCase().includes(term) ||
        (policy.formule ?? '').toLowerCase().includes(term) ||
        (policy.productCode ?? '').toLowerCase().includes(term) ||
        (policy.coverageDetails ?? '').toLowerCase().includes(term);

      const matchesType =
        this.selectedType === 'TOUS' ||
        (policy.type ?? '').toUpperCase() === this.selectedType;

      return matchesSearch && matchesType;
    });
  }

  onSearchChange(): void {
    this.applyFilters();
  }

  onTypeChange(type: string): void {
    this.selectedType = type;
    this.applyFilters();
  }

  toggleDetails(policyId: number): void {
    this.expandedPolicyId = this.expandedPolicyId === policyId ? null : policyId;
  }

  isExpanded(policyId: number): boolean {
    return this.expandedPolicyId === policyId;
  }

  isActive(policy: Policy): boolean {
    if (!policy.startDate || !policy.endDate) {
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

  getRemainingDays(policy: Policy): number {
    if (!policy.endDate) {
      return 0;
    }

    const today = new Date();
    const end = new Date(policy.endDate);

    today.setHours(0, 0, 0, 0);
    end.setHours(0, 0, 0, 0);

    const diff = end.getTime() - today.getTime();
    return Math.ceil(diff / (1000 * 60 * 60 * 24));
  }

  getTypeClass(type: string | undefined): string {
    const normalized = (type ?? '').toUpperCase();

    if (normalized === 'AUTO') return 'type-auto';
    if (normalized === 'SANTE') return 'type-sante';
    if (normalized === 'HABITATION') return 'type-habitation';

    return 'type-default';
  }

  getActiveCount(): number {
    return this.policies.filter((policy) => this.isActive(policy)).length;
  }

  getExpiredCount(): number {
    return this.policies.filter((policy) => !this.isActive(policy)).length;
  }

  getPolicyHolder(policy: Policy): string {
    const firstName = policy.client?.firstName ?? '';
    const lastName = policy.client?.lastName ?? '';

    return `${firstName} ${lastName}`.trim() || 'Client';
  }

  getTypeLabel(type: string | undefined): string {
    return (type ?? 'NON DÉFINI').toUpperCase();
  }

  goToHome(): void {
    this.router.navigate(['/']);
  }

  goToDecisions(): void {
    this.router.navigate(['/Consulter']);
  }

  logout(): void {
    this.authService.logout();
    this.router.navigate(['/login']);
  }

  goToPolice(): void {
    this.router.navigate(['/PolicesList']);
  }
}
