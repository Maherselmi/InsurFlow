import { Component, Inject, OnInit, PLATFORM_ID } from '@angular/core';
import { CommonModule, isPlatformBrowser } from '@angular/common';
import { Router, RouterModule } from '@angular/router';

interface NavItem {
  label: string;
  route: string;
}

interface NextStepItem {
  id: string;
  title: string;
  text: string;
}

@Component({
  selector: 'app-claim-step3-sante',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './claim-step3-sante.component.html',
  styleUrls: ['./claim-step3-sante.component.css'],
  host: { ngSkipHydration: 'true' }
})
export class ClaimStep3SanteComponent implements OnInit {
  navItems: NavItem[] = [
    { label: 'Tableau de bord', route: '/Client_Space' },
    { label: 'Mes contrats', route: '/contrats' },
    { label: 'Sinistres', route: '/Claim_Home' },
    { label: 'Mes dossiers', route: '/Consulter' }
  ];

  nextSteps: NextStepItem[] = [
    {
      id: '1',
      title: 'Validation médicale',
      text: 'Analyse des informations et des justificatifs transmis pour qualifier le dossier.'
    },
    {
      id: '2',
      title: 'Étude de prise en charge',
      text: 'Vérification de la couverture santé et du niveau de remboursement possible.'
    },
    {
      id: '3',
      title: 'Suivi dans votre espace',
      text: 'Retrouvez l’avancement complet depuis vos dossiers InSurFlow.'
    }
  ];

  reference = '';

  constructor(
    private router: Router,
    @Inject(PLATFORM_ID) private platformId: Object
  ) {}

  ngOnInit(): void {
    this.reference = this.generateReference();
  }

  isActiveNav(route: string): boolean {
    const currentUrl = this.router.url;

    if (route === '/Claim_Home') {
      return (
        currentUrl.startsWith('/Claim_Home') ||
        currentUrl.startsWith('/claim') ||
        currentUrl.startsWith('/Sante') ||
        currentUrl.startsWith('/Habitation')
      );
    }

    return currentUrl === route;
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

  goToClaimsHome(): void {
    this.router.navigate(['/Claim_Home']);
  }

  goToMyFiles(): void {
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
