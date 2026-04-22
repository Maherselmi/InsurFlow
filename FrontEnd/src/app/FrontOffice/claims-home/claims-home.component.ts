import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterModule } from '@angular/router';
import {AuthService} from "../../services/auth.service";
import {Client, ClientService} from "../../services/client.service";


@Component({
  selector: 'app-claims-home',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './claims-home.component.html',
  styleUrls: ['./claims-home.component.css']
})
export class ClaimsHomeComponent implements OnInit {
  navItems = [
    { label: 'Tableau de bord', route: '/Client_Space' },
    { label: 'Mes contrats', route: '/contrats' },
    { label: 'Sinistres', route: '/Claim_Home' },
    { label: 'Documents', route: '/documents' }
  ];

  client: Client | null = null;
  loadingProfile = true;

  constructor(
    private router: Router,
    private authService: AuthService,
    private clientService: ClientService
  ) {}

  ngOnInit(): void {
    if (!this.authService.isLoggedIn()) {
      this.router.navigate(['/login']);
      return;
    }

    this.loadCurrentClient();
  }

  loadCurrentClient(): void {
    const email = localStorage.getItem('email');

    if (!email) {
      this.loadingProfile = false;
      return;
    }

    this.clientService.getAllClients().subscribe({
      next: (clients) => {
        const found = clients.find(
          (c) => c.email?.toLowerCase() === email.toLowerCase()
        );

        this.client = found ?? null;
        this.loadingProfile = false;
      },
      error: () => {
        this.loadingProfile = false;
      }
    });
  }

  get fullName(): string {
    if (!this.client) return 'Client InSurFlow';
    return `${this.client.firstName} ${this.client.lastName}`;
  }

  get initials(): string {
    if (!this.client) return 'CI';
    const first = this.client.firstName?.charAt(0) || '';
    const last = this.client.lastName?.charAt(0) || '';
    return `${first}${last}`.toUpperCase();
  }

  goToClaim(type: string): void {
    this.router.navigate(['/claim/step1'], {
      queryParams: { type }
    });
  }

  goToClaimSante(type: string): void {
    this.router.navigate(['/Sante/step1'], {
      queryParams: { type }
    });
  }

  goToClaimHabitation(type: string): void {
    this.router.navigate(['/Habitation/step1'], {
      queryParams: { type }
    });
  }

  goToClaims(): void {
    this.router.navigate(['/dossiers']);
  }

  logout(): void {
    this.authService.logout();
    this.router.navigate(['/login']);
  }
}
