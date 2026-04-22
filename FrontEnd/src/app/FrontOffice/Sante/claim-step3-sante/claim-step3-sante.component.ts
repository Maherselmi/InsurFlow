import { Component, Inject, OnInit, PLATFORM_ID } from '@angular/core';
import { CommonModule, isPlatformBrowser } from '@angular/common';
import { Router, RouterModule } from '@angular/router';

@Component({
  selector: 'app-claim-step3-sante',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './claim-step3-sante.component.html',
  styleUrls: ['./claim-step3-sante.component.css'],
  host: { ngSkipHydration: 'true' }
})
export class ClaimStep3SanteComponent implements OnInit {
  navItems = [
    { label: 'Tableau de bord', route: '/Client_Space' },
    { label: 'Mes contrats', route: '/contrats' },
    { label: 'Sinistres', route: '/Claim_Home' },
    { label: 'Documents', route: '/documents' }
  ];

  reference = '';

  constructor(
    private router: Router,
    @Inject(PLATFORM_ID) private platformId: Object
  ) {}

  ngOnInit(): void {
    this.reference = this.generateReference();
  }

  generateReference(): string {
    if (isPlatformBrowser(this.platformId)) {
      const storedClaimId = localStorage.getItem('claimId');
      if (storedClaimId) {
        return `SANTE-${storedClaimId}-${new Date().getFullYear()}`;
      }
    }

    const rand = Math.floor(Math.random() * 10000);
    return `SANTE-${new Date().getFullYear()}-${rand}`;
  }

  newClaim(): void {
    this.router.navigate(['/Sante/step1']);
  }

  goToSinistre(): void {
    this.router.navigate(['/sinistres-home']);
  }

  goToDecisions(): void {
    this.router.navigate(['/Consulter']);
  }

  goToPolice(): void {
    this.router.navigate(['/PolicesList']);
  }

  goToHome(): void {
    this.router.navigate(['/']);
  }

  logout(): void {
    this.router.navigate(['/login']);
  }
}
