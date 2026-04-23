import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AiAgentConfig, AiConfigService } from '../../services/ai-config.service';
import { SidebarComponent } from '../sidebar/sidebar.component';
import { TopbarComponent } from '../topbar/topbar.component';

interface AgentUiItem extends AiAgentConfig {
  title: string;
  description: string;
  icon: string;
  saving?: boolean;
}

@Component({
  selector: 'app-parametres-ia',
  standalone: true,
  imports: [CommonModule, FormsModule, SidebarComponent, TopbarComponent],
  templateUrl: './parametres-ia.component.html',
  styleUrls: ['./parametres-ia.component.css']
})
export class ParametresIaComponent implements OnInit {
  loading = false;
  successMessage = '';
  errorMessage = '';

  configs: AgentUiItem[] = [];

  private readonly agentMeta: Record<string, { title: string; description: string; icon: string }> = {
    AGENT_ROUTEUR: {
      title: 'Agent Routeur',
      description: 'Détermine le type de sinistre et oriente le dossier vers le bon circuit.',
      icon: 'R'
    },
    AGENT_VALIDATION: {
      title: 'Agent Validation',
      description: 'Vérifie la conformité du dossier avec les règles métier et la police.',
      icon: 'V'
    },
    AGENT_ESTIMATEUR: {
      title: 'Agent Estimateur',
      description: 'Estime le coût potentiel du sinistre à partir des données disponibles.',
      icon: 'E'
    }
  };

  constructor(private aiConfigService: AiConfigService) {}

  ngOnInit(): void {
    this.loadConfigs();
  }

  loadConfigs(): void {
    this.loading = true;
    this.errorMessage = '';
    this.successMessage = '';

    this.aiConfigService.getAllConfigs().subscribe({
      next: (data) => {
        const existing = data || [];

        const normalized: AgentUiItem[] = Object.keys(this.agentMeta).map((key) => {
          const found = existing.find(item => item.agentName === key);

          return {
            id: found?.id,
            agentName: key,
            confidenceThreshold: found?.confidenceThreshold ?? this.getDefaultThreshold(key),
            title: this.agentMeta[key].title,
            description: this.agentMeta[key].description,
            icon: this.agentMeta[key].icon,
            saving: false
          };
        });

        this.configs = normalized;
        this.loading = false;
      },
      error: (err) => {
        console.error('Erreur chargement config IA:', err);
        this.errorMessage = 'Erreur lors du chargement des paramètres IA.';
        this.loading = false;
      }
    });
  }

  saveConfig(config: AgentUiItem): void {
    this.successMessage = '';
    this.errorMessage = '';

    if (config.confidenceThreshold < 0 || config.confidenceThreshold > 1) {
      this.errorMessage = `Le seuil de ${config.title} doit être entre 0 et 1.`;
      return;
    }

    config.saving = true;

    this.aiConfigService.updateConfig(config).subscribe({
      next: (updated) => {
        config.id = updated.id;
        config.confidenceThreshold = updated.confidenceThreshold;
        config.saving = false;
        this.successMessage = `Seuil mis à jour avec succès pour ${config.title}.`;
      },
      error: (err) => {
        console.error('Erreur sauvegarde config IA:', err);
        config.saving = false;
        this.errorMessage = `Erreur lors de l’enregistrement de ${config.title}.`;
      }
    });
  }

  formatPercent(value: number): string {
    return `${Math.round(value * 100)}%`;
  }

  onSliderChange(config: AgentUiItem, event: Event): void {
    const input = event.target as HTMLInputElement;
    config.confidenceThreshold = Number(input.value);
  }

  private getDefaultThreshold(agentName: string): number {
    switch (agentName) {
      case 'AGENT_ROUTEUR':
        return 0.70;
      case 'AGENT_VALIDATION':
        return 0.60;
      case 'AGENT_ESTIMATEUR':
        return 0.70;
      default:
        return 0.70;
    }
  }
}
