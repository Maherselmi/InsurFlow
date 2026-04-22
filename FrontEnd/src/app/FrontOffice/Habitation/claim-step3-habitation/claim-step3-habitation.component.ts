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
  selector: 'app-claim-step3-habitation',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './claim-step3-habitation.component.html',
  styleUrls: ['./claim-step3-habitation.component.css'],
  host: { ngSkipHydration: 'true' }
})
export class ClaimStep3HabitationComponent implements OnInit {
  navItems: NavItem[] = [
    { label: 'Tableau de bord', route: '/Client_Space' },
    { label: 'Mes contrats', route: '/contrats' },
    { label: 'Sinistres', route: '/Claim_Home' },
    { label: 'Mes dossiers', route: '/Consulter' }
  ];

  nextSteps: NextStepItem[] = [
    {
      id: '1',
      title: 'Analyse des dégâts',
      text: 'Étude des informations et des pièces transmises pour qualifier le sinistre.'
    },
    {
      id: '2',
      title: 'Évaluation habitation',
      text: 'Estimation des réparations et du niveau de prise en charge.'
    },
    {
      id: '3',
      title: 'Suivi du dossier',
      text: 'Retrouvez l’avancement complet depuis votre espace InSurFlow.'
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
        currentUrl.startsWith('/Habitation') ||
        currentUrl.startsWith('/Sante')
      );
    }

    return currentUrl === route;
  }

  generateReference(): string {
    if (isPlatformBrowser(this.platformId)) {
      const storedClaimId = localStorage.getItem('claimId');
      if (storedClaimId) {
        return `HAB-${storedClaimId}-${new Date().getFullYear()}`;
      }
    }

    const rand = Math.floor(Math.random() * 10000);
    return `HAB-${new Date().getFullYear()}-${rand}`;
  }

  newClaim(): void {
    this.router.navigate(['/claim/habitation/step1']);
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
