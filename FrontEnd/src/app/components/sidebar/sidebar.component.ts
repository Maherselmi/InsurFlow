import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterModule } from '@angular/router';

interface NavItem {
  label: string;
  icon: string;
  route?: string;
  active?: boolean;
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
    { label: 'Dossiers sinistres', icon: 'folder', route: '/dossiers', badgeClass: 'red' },
    { label: 'Clients', icon: 'users', route: '/clients' },
    { label: 'Polices', icon: 'grid', route: '/polices' }
  ];

  iaNavItems: NavItem[] = [
    { label: 'Résultats agents', icon: 'layout', route: '/agents' },
    { label: 'Validation humaine', icon: 'shield', route: '/Validation', badgeClass: 'red' }
  ];

  configNavItems: NavItem[] = [
    { label: 'Paramètres IA', icon: 'settings', route: '/parametres-ia' },
    { label: 'Déconnexion', icon: 'settings', route: '/logout' }
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

  getIconPath(iconName: string): string {
    const icons: Record<string, string> = {
      dashboard: '<rect x="3" y="3" width="7" height="7" rx="1"/><rect x="14" y="3" width="7" height="7" rx="1"/><rect x="3" y="14" width="7" height="7" rx="1"/><rect x="14" y="14" width="7" height="7" rx="1"/>',
      folder: '<path d="M14 2H6a2 2 0 00-2 2v16a2 2 0 002 2h12a2 2 0 002-2V8z"/><polyline points="14 2 14 8 20 8"/>',
      users: '<path d="M17 21v-2a4 4 0 00-4-4H5a4 4 0 00-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M23 21v-2a4 4 0 00-3-3.87M16 3.13a4 4 0 010 7.75"/>',
      grid: '<rect x="3" y="3" width="18" height="18" rx="2"/><path d="M3 9h18M9 21V9"/>',
      compass: '<circle cx="12" cy="12" r="3"/><path d="M12 1v4M12 19v4M4.22 4.22l2.83 2.83M16.95 16.95l2.83 2.83M1 12h4M19 12h4M4.22 19.78l2.83-2.83M16.95 7.05l2.83-2.83"/>',
      layout: '<path d="M9 3H5a2 2 0 00-2 2v4m6-6h10a2 2 0 012 2v4M9 3v18m0 0h10a2 2 0 002-2V9M9 21H5a2 2 0 01-2-2V9m0 0h18"/>',
      shield: '<path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/>',
      settings: '<circle cx="12" cy="12" r="3"/><path d="M19.07 4.93l-1.41 1.41M4.93 4.93l1.41 1.41M20 12h-2M4 12H2M17.66 17.66l-1.41-1.41M6.34 17.66l1.41-1.41"/>'
    };
    return icons[iconName] || '';
  }
}
