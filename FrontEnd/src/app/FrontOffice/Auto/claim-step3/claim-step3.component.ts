import { Component, OnInit } from '@angular/core';
import { Router, RouterModule } from '@angular/router';
import { CommonModule } from '@angular/common';

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
  selector: 'app-claim-step3',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './claim-step3.component.html',
  styleUrls: ['./claim-step3.component.css']
})
export class ClaimStep3Component implements OnInit {
  navItems: NavItem[] = [
    { label: 'Tableau de bord', route: '/Client_Space' },
    { label: 'Mes contrats', route: '/contrats' },
    { label: 'Sinistres', route: '/Claim_Home' },
    { label: 'Mes dossiers', route: '/Consulter' }
  ];

  nextSteps: NextStepItem[] = [
    {
      id: '1',
      title: 'Analyse du dossier',
      text: 'Vérification des informations et des documents envoyés.'
    },
    {
      id: '2',
      title: 'Étude du sinistre',
      text: 'Évaluation du dossier avant décision de traitement.'
    },
    {
      id: '3',
      title: 'Suivi dans votre espace',
      text: 'Consultez l’avancement depuis vos dossiers InSurFlow.'
    }
  ];

  reference = '';

  constructor(private router: Router) {}

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
    const storedClaimId =
      typeof localStorage !== 'undefined' ? localStorage.getItem('claimId') : null;

    if (storedClaimId) {
      return `AUTO-${storedClaimId}-${new Date().getFullYear()}`;
    }

    const rand = Math.floor(Math.random() * 10000);
    return `SIN-AUTO-${new Date().getFullYear()}-${rand}`;
  }

  newClaim(): void {
    this.router.navigate(['/claim/step1']);
  }

  goToClaimsHome(): void {
    this.router.navigate(['/Claim_Home']);
  }

  goToMyFiles(): void {
    this.router.navigate(['/Consulter']);
  }

  goToHome(): void {
    this.router.navigate(['/']);
  }

  goToDecisions(): void {
    this.router.navigate(['/Consulter']);
  }

  goToPolice(): void {
    this.router.navigate(['/PolicesList']);
  }

  logout(): void {
    this.router.navigate(['/login']);
  }
}
