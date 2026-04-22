import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterModule } from '@angular/router';

interface NavItem {
  label: string;
  icon: string;
  route?: string;
  badge?: string | number;
  badgeClass?: string;
}

@Component({
  selector: 'app-sidebar',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './sidebar.component.html',
  styleUrls: ['./sidebar.component.css']
})
export class SidebarComponent {
  constructor(private router: Router) {}

  mainNavItems: NavItem[] = [
    { label: 'Tableau de bord', icon: 'dashboard', route: '/dashboard' },
    { label: 'Dossiers sinistres', icon: 'folder', route: '/dossiers', badge: 'Live', badgeClass: 'red' },
    { label: 'Clients', icon: 'users', route: '/clients' },
    { label: 'Polices', icon: 'grid', route: '/polices' }
  ];

  iaNavItems: NavItem[] = [
    { label: 'Résultats agents', icon: 'layout', route: '/agents' },
    { label: 'Validation humaine', icon: 'shield', route: '/Validation', badge: 'IA', badgeClass: 'red' }
  ];

  configNavItems: NavItem[] = [
    { label: 'Paramètres IA', icon: 'settings', route: '/parametres-ia' },
    { label: 'Déconnexion', icon: 'logout', route: '/logout' }
  ];

  isActive(route: string): boolean {
    return this.router.url === route;
  }

  navigate(route?: string): void {
    if (route === '/logout') {
      this.logout();
      return;
    }

    if (route) {
      this.router.navigate([route]);
    }
  }

  logout(): void {
    localStorage.removeItem('token');
    localStorage.removeItem('userEmail');
    localStorage.removeItem('userRole');
    sessionStorage.clear();
    this.router.navigate(['/login']);
  }

  getAdminInitials(): string {
    return 'AD';
  }

  getAdminName(): string {
    return 'Admin InSurFlow';
  }

  getAdminRole(): string {
    return 'Administration centrale';
  }

  getIconPath(iconName: string): string {
    const icons: Record<string, string> = {
      dashboard:
        '<rect x="3" y="3" width="7" height="7" rx="1"/><rect x="14" y="3" width="7" height="7" rx="1"/><rect x="3" y="14" width="7" height="7" rx="1"/><rect x="14" y="14" width="7" height="7" rx="1"/>',
      folder:
        '<path d="M3 6a2 2 0 0 1 2-2h4l2 2h8a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V6z"/>',
      users:
        '<path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M23 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/>',
      grid:
        '<rect x="3" y="3" width="18" height="18" rx="2"/><path d="M3 9h18M9 21V9M15 21V9"/>',
      layout:
        '<rect x="3" y="4" width="18" height="16" rx="2"/><path d="M9 4v16"/><path d="M9 10h12"/>',
      shield:
        '<path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/>',
      settings:
        '<circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 1 1-4 0v-.09a1.65 1.65 0 0 0-1-1.51 1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 1 1 0-4h.09a1.65 1.65 0 0 0 1.51-1 1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06a1.65 1.65 0 0 0 1.82.33h.01a1.65 1.65 0 0 0 1-1.51V3a2 2 0 1 1 4 0v.09a1.65 1.65 0 0 0 1 1.51h.01a1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82v.01a1.65 1.65 0 0 0 1.51 1H21a2 2 0 1 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z"/>',
      logout:
        '<path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"/><polyline points="16 17 21 12 16 7"/><line x1="21" y1="12" x2="9" y2="12"/>'
    };

    return icons[iconName] || '';
  }
}
