import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';

import { BaseChartDirective } from 'ng2-charts';
import {
  ChartConfiguration,
  ChartOptions
} from 'chart.js';

import { SidebarComponent } from '../sidebar/sidebar.component';
import { TopbarComponent } from '../topbar/topbar.component';
import { AgentResult, AgentResultService } from '../../services/agent-result.service';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, SidebarComponent, TopbarComponent, BaseChartDirective],
  templateUrl: './dashboard.component.html',
  styleUrls: ['./dashboard.component.css']
})
export class DashboardComponent implements OnInit {
  constructor(
    private agentResultService: AgentResultService,
    private router: Router
  ) {}

  threshold = {
    value: 75,
    message: 'En dessous → Human-in-the-Loop activé'
  };

  public lineChartType: 'line' = 'line';

  public lineChartData: ChartConfiguration<'line', number[], string>['data'] = {
    labels: [],
    datasets: [
      {
        label: 'Agent Routeur',
        data: [],
        tension: 0.35
      },
      {
        label: 'Agent Validateur',
        data: [],
        tension: 0.35
      },
      {
        label: 'Agent Estimateur',
        data: [],
        tension: 0.35
      }
    ]
  };

  public lineChartOptions: ChartOptions<'line'> = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: {
      legend: {
        display: true,
        position: 'top'
      }
    },
    scales: {
      y: {
        min: 0,
        max: 100
      }
    }
  };

  public barChartType: 'bar' = 'bar';

  public barChartData: ChartConfiguration<'bar', number[], string>['data'] = {
    labels: ['Routeur', 'Validateur', 'Estimateur'],
    datasets: [
      {
        label: 'Score moyen (%)',
        data: [0, 0, 0],
        borderRadius: 10
      }
    ]
  };

  public barChartOptions: ChartOptions<'bar'> = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: {
      legend: {
        display: false
      }
    },
    scales: {
      y: {
        min: 0,
        max: 100
      }
    }
  };

  public doughnutChartType: 'doughnut' = 'doughnut';

  public doughnutChartData: ChartConfiguration<'doughnut', number[], string>['data'] = {
    labels: ['Validés', 'Human Review', 'Refusés'],
    datasets: [
      {
        data: [0, 0, 0]
      }
    ]
  };

  public doughnutChartOptions: ChartOptions<'doughnut'> = {
    responsive: true,
    maintainAspectRatio: false,
    cutout: '68%',
    plugins: {
      legend: {
        display: true,
        position: 'left'
      }
    }
  };

  ngOnInit(): void {
    this.loadAgentResults();
  }

  get totalValidated(): number {
    const data: number[] = this.doughnutChartData.datasets[0]?.data ?? [];
    return Number(data[0] ?? 0);
  }

  get totalHumanReview(): number {
    const data: number[] = this.doughnutChartData.datasets[0]?.data ?? [];
    return Number(data[1] ?? 0);
  }

  get totalRejected(): number {
    const data: number[] = this.doughnutChartData.datasets[0]?.data ?? [];
    return Number(data[2] ?? 0);
  }

  get averageGlobalScore(): number {
    const values: number[] = this.barChartData.datasets[0]?.data ?? [];

    if (values.length === 0) {
      return 0;
    }

    const total = values.reduce<number>((sum, value) => {
      return sum + Number(value ?? 0);
    }, 0);

    return Math.round(total / values.length);
  }

  loadAgentResults(): void {
    this.agentResultService.getAll().subscribe({
      next: (results) => {
        this.buildLineChart(results);
        this.buildBarChart(results);
        this.buildDoughnutChart(results);
      },
      error: (error) => {
        console.error('Erreur chargement agent results', error);
      }
    });
  }

  buildLineChart(results: AgentResult[]): void {
    const sortedResults = [...results].sort(
      (a, b) => new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime()
    );

    const labels: string[] = [
      ...new Set(
        sortedResults.map(r => new Date(r.createdAt).toLocaleDateString('fr-FR'))
      )
    ];

    this.lineChartData = {
      labels,
      datasets: [
        {
          label: 'Agent Routeur',
          data: this.getAverageScoresByAgent(labels, sortedResults, 'routeur'),
          tension: 0.35
        },
        {
          label: 'Agent Validateur',
          data: this.getAverageScoresByAgent(labels, sortedResults, 'validateur'),
          tension: 0.35
        },
        {
          label: 'Agent Estimateur',
          data: this.getAverageScoresByAgent(labels, sortedResults, 'estimateur'),
          tension: 0.35
        }
      ]
    };
  }

  buildBarChart(results: AgentResult[]): void {
    const routeur = this.getGlobalAverage(results, 'routeur');
    const validateur = this.getGlobalAverage(results, 'validateur');
    const estimateur = this.getGlobalAverage(results, 'estimateur');

    this.barChartData = {
      labels: ['Routeur', 'Validateur', 'Estimateur'],
      datasets: [
        {
          label: 'Score moyen (%)',
          data: [routeur, validateur, estimateur],
          borderRadius: 10
        }
      ]
    };
  }

  buildDoughnutChart(results: AgentResult[]): void {
    const validated = results.filter(r =>
      !r.needsHumanReview &&
      this.normalizeScore(r.confidenceScore) >= this.threshold.value &&
      this.isCovered(r.conclusion)
    ).length;

    const humanReview = results.filter(r =>
      r.needsHumanReview || this.isUnknown(r.conclusion)
    ).length;

    const rejected = results.filter(r =>
      this.isRejected(r.conclusion)
    ).length;

    this.doughnutChartData = {
      labels: ['Validés', 'Human Review', 'Refusés'],
      datasets: [
        {
          data: [validated, humanReview, rejected]
        }
      ]
    };
  }

  private getAverageScoresByAgent(
    labels: string[],
    results: AgentResult[],
    keyword: string
  ): number[] {
    return labels.map((label: string) => {
      const filtered = results.filter(r =>
        (r.agentName ?? '').toLowerCase().includes(keyword) &&
        new Date(r.createdAt).toLocaleDateString('fr-FR') === label
      );

      if (filtered.length === 0) {
        return 0;
      }

      const total = filtered.reduce<number>((sum, item) => {
        return sum + this.normalizeScore(item.confidenceScore);
      }, 0);

      return Math.round(total / filtered.length);
    });
  }

  private getGlobalAverage(results: AgentResult[], keyword: string): number {
    const filtered = results.filter(r =>
      (r.agentName ?? '').toLowerCase().includes(keyword)
    );

    if (filtered.length === 0) {
      return 0;
    }

    const total = filtered.reduce<number>((sum, item) => {
      return sum + this.normalizeScore(item.confidenceScore);
    }, 0);

    return Math.round(total / filtered.length);
  }

  private normalizeScore(score: number | undefined | null): number {
    const value = Number(score ?? 0);

    if (value >= 0 && value <= 1) {
      return Math.round(value * 100);
    }

    return Math.round(value);
  }

  private isRejected(conclusion: string | undefined | null): boolean {
    const value = (conclusion ?? '').toLowerCase();
    return value.includes('rejet') || value.includes('exclu') || value === 'exclu';
  }

  private isCovered(conclusion: string | undefined | null): boolean {
    const value = (conclusion ?? '').toLowerCase();
    return value.includes('couvert') || value.includes('approuv');
  }

  private isUnknown(conclusion: string | undefined | null): boolean {
    const value = (conclusion ?? '').toLowerCase();
    return value.includes('inconnu');
  }

  onNewClaim(): void {
    this.router.navigate(['/dossiers']);
  }
}
