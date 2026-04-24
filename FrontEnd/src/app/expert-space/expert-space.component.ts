import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterModule } from '@angular/router';
import {AuthService} from "../services/auth.service";

interface ExpertNavItem {
  label: string;
  route: string;
}

interface ExpertActionCard {
  title: string;
  text: string;
  button: string;
  route: string;
}

@Component({
  selector: 'app-expert-space',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './expert-space.component.html',
  styleUrls: ['./expert-space.component.css']
})
export class ExpertSpaceComponent implements OnInit {

  navItems: ExpertNavItem[] = [
    { label: 'Espace expert', route: '/Expert_Space' },
    { label: 'Validation humaine', route: '/expert-feedback/:claimId' },
    { label: 'Feedback expert', route: '/feedback-claims' },
    { label: 'Dossiers', route: '/AdminClaimsList' }
  ];

  quickActions: ExpertActionCard[] = [
    {
      title: 'Validation humaine',
      text: 'Traitez les dossiers en attente et prenez une décision finale.',
      button: 'Accéder à la validation',
      route: '/HumanValidationList'
    },
    {
      title: 'Feedback expert',
      text: 'Corrigez les résultats IA et alimentez la mémoire des agents.',
      button: 'Ouvrir les feedbacks',
      route: '/ExpertFeedbackList'
    },
    {
      title: 'Suivi des dossiers',
      text: 'Consultez les sinistres analysés et l’état global du traitement.',
      button: 'Voir les dossiers',
      route: '/AdminClaimsList'
    }
  ];

  expertName = 'Expert';
  expertEmail = '';
  role = '';

  constructor(
    private authService: AuthService,
    private router: Router
  ) {}

  ngOnInit(): void {
    if (!this.authService.isLoggedIn()) {
      this.router.navigate(['/login']);
      return;
    }

    const role = this.authService.getRole() || '';
    this.role = role;

    if (!this.isExpert(role)) {
      this.router.navigate(['/login']);
      return;
    }

    const email = localStorage.getItem('email') || '';
    this.expertEmail = email;
    this.expertName = this.buildExpertName(email);
  }

  isActiveNav(route: string): boolean {
    return this.router.url === route;
  }

  private isExpert(role: string): boolean {
    const normalized = role.trim().toUpperCase();
    return normalized === 'EXPERT' || normalized === 'ROLE_EXPERT';
  }

  private buildExpertName(email: string): string {
    if (!email) return 'Expert InSurFlow';

    const localPart = email.split('@')[0] || 'expert';
    const formatted = localPart
      .replace(/[._-]+/g, ' ')
      .trim()
      .split(' ')
      .filter(Boolean)
      .map(word => word.charAt(0).toUpperCase() + word.slice(1).toLowerCase())
      .join(' ');

    return formatted || 'Expert InSurFlow';
  }

  goTo(route: string): void {
    this.router.navigate([route]);
  }

  logout(): void {
    this.authService.logout();
    this.router.navigate(['/login']);
  }
}
