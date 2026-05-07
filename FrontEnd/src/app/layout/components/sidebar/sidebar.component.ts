import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';

type NavItem = {
  label: string;
  route: string;
  icon: 'dashboard' | 'clients' | 'policies' | 'claims' | 'agents' | 'ai' | 'reports' | 'settings';
  badge?: string;
  badgeClass?: 'red';
};

type NavGroup = {
  title: string;
  items: NavItem[];
};

@Component({
  selector: 'app-sidebar',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './sidebar.component.html',
  styleUrls: ['./sidebar.component.css']
})
export class SidebarComponent {
  navGroups: NavGroup[] = [
    {
      title: 'Principal',
      items: [
        { label: 'Dashboard', route: 'dashboard', icon: 'dashboard' },
        { label: 'Clients', route: 'clients', icon: 'clients' },
        { label: 'Polices', route: 'polices', icon: 'policies' },
        { label: 'Sinistres', route: 'dossiers', icon: 'claims', badge: '12', badgeClass: 'red' },
        { label: 'Rapports', route: '/admin/reports', icon: 'reports' }
      ]
    },
    {
      title: 'IA & Agents',
      items: [
        { label: 'Agents IA', route: 'agents', icon: 'agents' },
        { label: 'Analyse IA', route: '/admin/ai-analysis', icon: 'ai' }
      ]
    },
    {
      title: 'Configuration',
      items: [
        { label: 'Paramètres', route: '/admin/settings', icon: 'settings' }
      ]
    }
  ];

  constructor(private router: Router) {}

  navigate(route: string): void {
    if (!route) return;
    this.router.navigateByUrl(route);
  }

  isActive(route: string): boolean {
    return !!route && this.router.url.startsWith(route);
  }

  getAdminInitials(): string {
    return 'AD';
  }

  getAdminName(): string {
    return 'Admin InSurFlow';
  }

  getAdminRole(): string {
    return 'Administrateur';
  }
}
