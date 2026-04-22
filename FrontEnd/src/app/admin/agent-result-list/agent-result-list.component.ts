import { Component, OnInit } from '@angular/core';
import { CommonModule, DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { SidebarComponent } from '../../components/sidebar/sidebar.component';
import { TopbarComponent } from '../../components/topbar/topbar.component';
import { AgentResultService, AgentResult } from '../../services/agent-result.service';

interface ClaimGroup {
  claimId: number;
  clientInitials: string;
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
  filtered: AgentResult[] = [];
  claimGroups: ClaimGroup[] = [];
  filteredGroups: ClaimGroup[] = [];
  loading = true;
  searchTerm = '';
  selectedGroup: ClaimGroup | null = null;

  constructor(private agentResultService: AgentResultService) {}

  ngOnInit(): void {
    this.agentResultService.getAll().subscribe({
      next: (data) => {
        this.results = data;
        this.filtered = data;
        this.claimGroups = this.groupByClaim(data);
        this.filteredGroups = this.claimGroups;
        this.loading = false;
      },
      error: (err) => {
        console.error('Erreur', err);
        this.loading = false;
      }
    });
  }

  groupByClaim(results: AgentResult[]): ClaimGroup[] {
    const map = new Map<number, AgentResult[]>();
    results.forEach(r => {
      const id = r.claim?.id ?? 0;
      if (!map.has(id)) map.set(id, []);
      map.get(id)!.push(r);
    });
    return Array.from(map.entries()).map(([claimId, agents]) => ({
      claimId,
      clientInitials: this.getInitials(agents[0]),
      agents
    }));
  }

  onSearch(): void {
    const term = this.searchTerm.toLowerCase().trim();
    if (!term) { this.filteredGroups = this.claimGroups; return; }
    this.filteredGroups = this.claimGroups.filter(g =>
      `${g.claimId}`.includes(term) ||
      g.agents.some(r =>
        r.agentName?.toLowerCase().includes(term) ||
        r.conclusion?.toLowerCase().includes(term)
      )
    );
  }

  selectGroup(g: ClaimGroup): void {
    this.selectedGroup = this.selectedGroup?.claimId === g.claimId ? null : g;
  }
  groupNeedsHuman(group: ClaimGroup): boolean {
    return group.agents.some(a => a.needsHumanReview);
  }

  getAgentFromGroup(group: ClaimGroup, agentName: string): AgentResult | undefined {
    return group.agents.find(a => a.agentName === agentName);
  }

  getCountByAgent(name: string): number {
    return this.results.filter(r => r.agentName === name).length;
  }

  getCountNeedsHuman(): number {
    return this.results.filter(r => r.needsHumanReview).length;
  }

  getInitials(r: AgentResult): string {
    const f = r.claim?.policy?.client?.firstName || '';
    const l = r.claim?.policy?.client?.lastName || '';
    return ((f[0] || '') + (l[0] || '')).toUpperCase() || '?';
  }

  getAgentBadgeClass(name: string): string {
    const map: Record<string, string> = {
      'AgentRouteur':    'badge-routeur',
      'AgentValidateur': 'badge-validateur',
      'AgentEstimateur': 'badge-estimateur'
    };
    return map[name] || 'badge-progress';
  }

  getConclusionClass(conclusion: string): string {
    if (!conclusion) return 'badge-progress';
    const c = conclusion.toUpperCase();
    if (c.includes('COUVERT') || c.includes('SANTE') || c.includes('AUTO') || c.includes('HABITATION')) return 'badge-ok';
    if (c.includes('EXCLU') || c.includes('INCONNU')) return 'badge-reject';
    if (c.includes('ESTIMATION') || c.includes('PARTIEL')) return 'badge-pending';
    return 'badge-progress';
  }

  formatConfidence(score: number): string {
    if (score == null) return '0%';
    return score > 1 ? `${score.toFixed(0)}%` : `${(score * 100).toFixed(0)}%`;
  }

  getConfidenceWidth(score: number): string {
    if (score == null) return '0%';
    const val = score > 1 ? score : score * 100;
    return `${Math.min(val, 100)}%`;
  }

  getConfidenceColor(score: number): string {
    if (score == null) return '#dc2626';
    const val = score > 1 ? score : score * 100;
    if (val >= 75) return '#059669';
    if (val >= 50) return '#f0a500';
    return '#dc2626';
  }
}
