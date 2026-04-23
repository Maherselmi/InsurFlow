import { Component, OnInit } from '@angular/core';
import { CommonModule, DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';

import { AgentResultService, AgentResult } from '../../services/agent-result.service';
import { SidebarComponent } from "../../BackOffice/sidebar/sidebar.component";
import { TopbarComponent } from "../../BackOffice/topbar/topbar.component";

interface ClaimGroup {
  claimId: number;
  clientInitials: string;
  clientName: string;
  claimDescription: string;
  agents: AgentResult[];
}

@Component({
  selector: 'app-agent-result-list',
  standalone: true,
  imports: [CommonModule, FormsModule, DatePipe, SidebarComponent, TopbarComponent],
  templateUrl: './agent-result-list.component.html',
  styleUrls: ['./agent-result-list.component.css']
})
export class AgentResultListComponent implements OnInit {

  results: AgentResult[] = [];
  claimGroups: ClaimGroup[] = [];
  filteredGroups: ClaimGroup[] = [];
  loading = true;
  searchTerm = '';
  selectedGroup: ClaimGroup | null = null;

  constructor(
    private agentResultService: AgentResultService,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.loadResults();
  }

  loadResults(): void {
    this.loading = true;

    this.agentResultService.getAll().subscribe({
      next: (data) => {
        this.results = data || [];
        this.claimGroups = this.groupByClaim(this.results);
        this.filteredGroups = [...this.claimGroups];
        this.loading = false;
      },
      error: (err) => {
        console.error('Erreur chargement résultats agents', err);
        this.loading = false;
      }
    });
  }

  groupByClaim(results: AgentResult[]): ClaimGroup[] {
    const map = new Map<number, AgentResult[]>();

    results.forEach(result => {
      const claimId = result.claim?.id ?? 0;
      if (!map.has(claimId)) {
        map.set(claimId, []);
      }
      map.get(claimId)!.push(result);
    });

    return Array.from(map.entries()).map(([claimId, agents]) => ({
      claimId,
      clientInitials: this.getInitials(agents[0]),
      clientName: this.getClientName(agents[0]),
      claimDescription: agents[0]?.claim?.description || 'Aucune description',
      agents: agents.sort((a, b) =>
        new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()
      )
    }));
  }

  onSearch(): void {
    const term = this.searchTerm.toLowerCase().trim();

    if (!term) {
      this.filteredGroups = [...this.claimGroups];
      return;
    }

    this.filteredGroups = this.claimGroups.filter(group =>
      `${group.claimId}`.includes(term) ||
      group.clientName.toLowerCase().includes(term) ||
      group.claimDescription.toLowerCase().includes(term) ||
      group.agents.some(agent =>
        (agent.agentName ?? '').toLowerCase().includes(term) ||
        (agent.conclusion ?? '').toLowerCase().includes(term)
      )
    );
  }

  selectGroup(group: ClaimGroup): void {
    this.selectedGroup = this.selectedGroup?.claimId === group.claimId ? null : group;
  }

  groupNeedsHuman(group: ClaimGroup): boolean {
    return group.agents.some(agent => agent.needsHumanReview);
  }

  getAgentFromGroup(group: ClaimGroup, agentName: string): AgentResult | undefined {
    return group.agents.find(agent => agent.agentName === agentName);
  }

  getCountByAgent(name: string): number {
    return this.results.filter(result => result.agentName === name).length;
  }

  getCountNeedsHuman(): number {
    return this.results.filter(result => result.needsHumanReview).length;
  }

  getInitials(result: AgentResult): string {
    const firstName = result.claim?.policy?.client?.firstName || '';
    const lastName = result.claim?.policy?.client?.lastName || '';
    return ((firstName[0] || '') + (lastName[0] || '')).toUpperCase() || '?';
  }

  getClientName(result: AgentResult): string {
    const firstName = result.claim?.policy?.client?.firstName || '';
    const lastName = result.claim?.policy?.client?.lastName || '';
    return `${firstName} ${lastName}`.trim() || 'Client non renseigné';
  }

  getAgentBadgeClass(name: string): string {
    const map: Record<string, string> = {
      AgentRouteur: 'badge-routeur',
      AgentValidateur: 'badge-validateur',
      AgentEstimateur: 'badge-estimateur'
    };
    return map[name] || 'badge-progress';
  }

  getConclusionClass(conclusion: string): string {
    if (!conclusion) return 'badge-progress';

    const value = conclusion.toUpperCase();

    if (
      value.includes('COUVERT') ||
      value.includes('AUTO') ||
      value.includes('SANTE') ||
      value.includes('HABITATION')
    ) {
      return 'badge-ok';
    }

    if (value.includes('EXCLU') || value.includes('INCONNU')) {
      return 'badge-reject';
    }

    if (value.includes('ESTIMATION') || value.includes('PARTIEL')) {
      return 'badge-pending';
    }

    return 'badge-progress';
  }

  formatConfidence(score: number): string {
    if (score == null) return '0%';
    return score > 1 ? `${score.toFixed(0)}%` : `${(score * 100).toFixed(0)}%`;
  }

  getConfidenceWidth(score: number): string {
    if (score == null) return '0%';
    const value = score > 1 ? score : score * 100;
    return `${Math.min(value, 100)}%`;
  }

  getConfidenceColor(score: number): string {
    if (score == null) return '#dc2626';
    const value = score > 1 ? score : score * 100;

    if (value >= 75) return '#059669';
    if (value >= 50) return '#f0a500';
    return '#dc2626';
  }

  getLatestCreatedAt(group: ClaimGroup): string | undefined {
    return group.agents[0]?.createdAt;
  }

  onNewClaim(): void {
    this.router.navigate(['/dossiers']);
  }
}
