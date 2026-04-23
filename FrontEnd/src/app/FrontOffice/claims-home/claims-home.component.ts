import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterModule } from '@angular/router';
import { AuthService } from '../../services/auth.service';
import { Client, ClientService } from '../../services/client.service';

interface NavItem {
  label: string;
  route: string;
}

interface ClaimTypeCard {
  code: 'AUTO' | 'SANTE' | 'HABITATION';
  title: string;
  subtitle: string;
  description: string;
  image: string;
  icon: string;
  action: string;
}

interface BenefitItem {
  title: string;
  text: string;
}

interface ProcessStep {
  id: string;
  title: string;
  text: string;
  icon: 'edit' | 'upload' | 'follow';
}

@Component({
  selector: 'app-claims-home',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './claims-home.component.html',
  styleUrls: ['./claims-home.component.css']
})
export class ClaimsHomeComponent implements OnInit {
  navItems: NavItem[] = [
    { label: 'Tableau de bord', route: '/Client_Space' },
    { label: 'Mes contrats', route: '/contrats' },
    { label: 'Sinistres', route: '/Claim_Home' },
    { label: 'Mes dossiers', route: '/Consulter' }
  ];

  claimTypes: ClaimTypeCard[] = [
    {
      code: 'AUTO',
      title: 'Sinistre Auto',
      subtitle: 'Déclaration véhicule',
      description: 'Accidents, collisions, bris de glace et dommages véhicule avec un parcours rapide et guidé.',
      image: 'assets/images/auto-insurance.jpg',
      icon: 'A',
      action: 'Déclarer un sinistre auto'
    },
    {
      code: 'SANTE',
      title: 'Sinistre Santé',
      subtitle: 'Prise en charge médicale',
      description: 'Hospitalisation, soins, remboursement et documents médicaux dans un parcours clair.',
      image: 'assets/images/health-insurance.jpg',
      icon: 'S',
      action: 'Déclarer un sinistre santé'
    },
    {
      code: 'HABITATION',
      title: 'Sinistre Habitation',
      subtitle: 'Dommages logement',
      description: 'Incendie, dégâts des eaux, vol, vandalisme et autres dommages sur votre habitation.',
      image: 'assets/images/home-insurance.jpg',
      icon: 'H',
      action: 'Déclarer un sinistre habitation'
    }
  ];

  benefits: BenefitItem[] = [
    {
      title: 'Déclaration simplifiée',
      text: 'Un parcours fluide pour démarrer rapidement sans complexité inutile.'
    },
    {
      title: 'Suivi centralisé',
      text: 'Toutes les étapes, documents et informations restent visibles dans le même espace.'
    },
    {
      title: 'Vision plus claire',
      text: 'Chaque type de sinistre est présenté de façon lisible et professionnelle.'
    }
  ];

  processSteps: ProcessStep[] = [
    {
      id: '01',
      title: 'Choisissez votre type de sinistre',
      text: 'Sélectionnez auto, santé ou habitation pour démarrer le bon parcours de déclaration.',
      icon: 'edit'
    },
    {
      id: '02',
      title: 'Ajoutez vos pièces et justificatifs',
      text: 'Déposez vos documents, photos et informations utiles dans le même espace.',
      icon: 'upload'
    },
    {
      id: '03',
      title: 'Suivez l’avancement du dossier',
      text: 'Consultez les décisions, rapports et évolutions de traitement à chaque étape.',
      icon: 'follow'
    }
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

  isActiveNav(route: string): boolean {
    return this.router.url === route;
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

  handleClaim(type: 'AUTO' | 'SANTE' | 'HABITATION'): void {
    if (type === 'AUTO') {
      this.goToClaim('AUTO');
      return;
    }

    if (type === 'SANTE') {
      this.goToClaimSante('SANTE');
      return;
    }

    this.goToClaimHabitation('HABITATION');
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
