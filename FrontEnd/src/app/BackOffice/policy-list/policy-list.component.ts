import { Component, OnInit } from '@angular/core';
import { CommonModule, DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';

import { PolicyService, Policy } from '../../services/policy.service';
import { SidebarComponent } from "../../BackOffice/sidebar/sidebar.component";
import { TopbarComponent } from "../../BackOffice/topbar/topbar.component";

@Component({
  selector: 'app-policy-list',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    DatePipe,
    SidebarComponent,
    TopbarComponent
  ],
  templateUrl: './policy-list.component.html',
  styleUrls: ['./policy-list.component.css']
})
export class PolicyListComponent implements OnInit {

  policies: Policy[] = [];
  filtered: Policy[] = [];
  loading = true;
  searchTerm = '';
  selectedPolicy: Policy | null = null;

  constructor(
    private policyService: PolicyService,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.loadPolicies();
  }

  loadPolicies(): void {
    this.loading = true;

    this.policyService.getAllPolicies().subscribe({
      next: (data) => {
        this.policies = data ?? [];
        this.filtered = [...this.policies];
        this.loading = false;
      },
      error: (err) => {
        console.error('Erreur chargement polices', err);
        this.policies = [];
        this.filtered = [];
        this.loading = false;
      }
    });
  }

  onSearch(): void {
    const term = this.searchTerm.toLowerCase().trim();

    if (!term) {
      this.filtered = [...this.policies];
      return;
    }

    this.filtered = this.policies.filter((p) =>
      (p.policyNumber ?? '').toLowerCase().includes(term) ||
      (p.type ?? '').toLowerCase().includes(term) ||
      (p.formule ?? '').toLowerCase().includes(term) ||
      (p.productCode ?? '').toLowerCase().includes(term) ||
      (p.coverageDetails ?? '').toLowerCase().includes(term) ||
      (p.client?.firstName ?? '').toLowerCase().includes(term) ||
      (p.client?.lastName ?? '').toLowerCase().includes(term) ||
      (p.client?.email ?? '').toLowerCase().includes(term)
    );
  }

  getCountByType(type: string): number {
    return this.policies.filter(
      (p) => (p.type ?? '').toUpperCase() === type.toUpperCase()
    ).length;
  }

  getInitials(p: Policy): string {
    const first = p.client?.firstName?.charAt(0) ?? '';
    const last = p.client?.lastName?.charAt(0) ?? '';
    const initials = `${first}${last}`.toUpperCase();
    return initials || '?';
  }

  getClientFullName(p: Policy): string {
    const first = p.client?.firstName ?? '';
    const last = p.client?.lastName ?? '';
    return `${first} ${last}`.trim() || 'Client non renseigné';
  }

  selectPolicy(p: Policy): void {
    this.selectedPolicy = this.selectedPolicy?.id === p.id ? null : p;
  }

  getTypeBadgeClass(type: string | undefined): string {
    const normalized = (type ?? '').toUpperCase();

    const map: Record<string, string> = {
      AUTO: 'type-auto',
      SANTE: 'type-sante',
      HABITATION: 'type-habitation',
      VIE: 'type-vie'
    };

    return map[normalized] || 'type-default';
  }

  isExpired(endDate: string | undefined): boolean {
    if (!endDate) {
      return false;
    }

    const end = new Date(endDate);
    const today = new Date();

    today.setHours(0, 0, 0, 0);
    end.setHours(0, 0, 0, 0);

    return end < today;
  }

  get activePoliciesCount(): number {
    return this.policies.filter(p => !this.isExpired(p.endDate)).length;
  }

  get expiredPoliciesCount(): number {
    return this.policies.filter(p => this.isExpired(p.endDate)).length;
  }

  getCoveragePreview(details: string | undefined): string {
    if (!details) {
      return 'Aucun détail de couverture renseigné.';
    }

    return details.length > 120 ? `${details.slice(0, 120)}...` : details;
  }

  onNewClaim(): void {
    this.router.navigate(['/dossiers']);
  }
}
