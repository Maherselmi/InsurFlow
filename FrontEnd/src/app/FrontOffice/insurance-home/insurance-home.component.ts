import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';

interface NavItem {
  label: string;
  href: string;
}

interface StatItem {
  value: string;
  label: string;
}

interface OfferItem {
  id: string;
  title: string;
  description: string;
  cta: string;
  image: string;
}

interface ClaimStep {
  id: string;
  title: string;
  text: string;
  icon: 'edit' | 'user' | 'check';
}

interface AdvantageItem {
  title: string;
  text: string;
  icon: 'user' | 'clock' | 'pin' | 'shield';
}

interface ContactMethod {
  title: string;
  text: string;
  action: string;
  href: string;
  icon: 'phone' | 'mail' | 'chat';
}

interface HomeSolution {
  title: string;
  text: string;
  icon: 'pilot' | 'claim' | 'safe';
}

interface ProductCard {
  title: string;
  text: string;
}

@Component({
  selector: 'app-insurance-home',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './insurance-home.component.html',
  styleUrls: ['./insurance-home.component.css']
})
export class InsuranceHomeComponent {
  navItems: NavItem[] = [
    { label: 'Nos offres', href: '#offres' },
    { label: 'Plateforme', href: '#plateforme' },
    { label: 'Sinistres', href: '#sinistres' },
    { label: 'Pourquoi InSurFlow', href: '#pourquoi' },
    { label: 'Contact', href: '#contact' }
  ];

  stats: StatItem[] = [
    { value: '98%', label: 'Sinistres traités sous 7 jours' },
    { value: '24/7', label: "Assistance d'urgence" },
    { value: '180k', label: 'Assurés en France' },
    { value: '1962', label: 'Année de fondation' }
  ];

  offers: OfferItem[] = [
    {
      id: '01',
      title: 'Auto',
      description: 'Tous risques, intermédiaire ou tiers. Assistance 24/7 et véhicule de remplacement.',
      cta: 'Découvrir l’offre',
      image: 'assets/images/auto-insurance.jpg'
    },
    {
      id: '02',
      title: 'Habitation',
      description: 'Résidence principale, secondaire ou locative. Couverture multirisque sur mesure.',
      cta: 'Découvrir l’offre',
      image: 'assets/images/home-insurance.jpg'
    },
    {
      id: '03',
      title: 'Santé',
      description: 'Complémentaires individuelles et familiales. Tiers payant généralisé.',
      cta: 'Découvrir l’offre',
      image: 'assets/images/health-insurance.jpg'
    },
    {
      id: '04',
      title: 'Vie & Prévoyance',
      description: 'Épargne, transmission, garantie décès. Conseillers patrimoniaux dédiés.',
      cta: 'Découvrir l’offre',
      image: 'assets/images/Vie.png'
    }
  ];

  solutions: HomeSolution[] = [
    {
      title: 'Expérience assurée centralisée',
      text: 'Une seule interface pour les contrats, les documents, les échanges et le suivi global.',
      icon: 'pilot'
    },
    {
      title: 'Gestion des sinistres plus fluide',
      text: 'Déclaration, dépôt de justificatifs et suivi du dossier dans un parcours beaucoup plus clair.',
      icon: 'claim'
    },
    {
      title: 'Environnement sécurisé',
      text: 'Protection des données, accès contrôlé et documents sensibles accessibles dans un espace fiable.',
      icon: 'safe'
    }
  ];

  productCards: ProductCard[] = [
    {
      title: 'Client Space',
      text: 'Un espace moderne pour consulter les contrats, suivre les dossiers et retrouver tous les documents.'
    },
    {
      title: 'ClaimFlow',
      text: 'Un parcours de sinistre plus lisible et plus rassurant, pensé pour réduire la friction.'
    },
    {
      title: 'DocFlow',
      text: 'Une organisation claire des justificatifs, rapports et pièces importantes du dossier.'
    }
  ];

  claimSteps: ClaimStep[] = [
    {
      id: '01',
      title: 'Déclarez',
      text: 'En ligne ou par téléphone, 24/7.',
      icon: 'edit'
    },
    {
      id: '02',
      title: 'Expertise',
      text: 'Un expert indépendant sous 48h.',
      icon: 'user'
    },
    {
      id: '03',
      title: 'Indemnisation',
      text: 'Versement sous 7 jours en moyenne.',
      icon: 'check'
    }
  ];

  advantages: AdvantageItem[] = [
    {
      title: 'Conseiller dédié',
      text: 'Un interlocuteur unique pour tous vos contrats.',
      icon: 'user'
    },
    {
      title: 'Réponse sous 24h',
      text: 'Engagement contractuel sur tous nos canaux.',
      icon: 'clock'
    },
    {
      title: 'Expertise sur place',
      text: 'Experts mandatés et indépendants partout en France.',
      icon: 'pin'
    },
    {
      title: 'Documents sécurisés',
      text: 'Coffre-fort numérique chiffré, accessible à vie.',
      icon: 'shield'
    }
  ];

  contactMethods: ContactMethod[] = [
    {
      title: 'Par téléphone',
      text: 'Parlez à un conseiller pour un besoin immédiat ou une situation urgente.',
      action: 'Nous appeler',
      href: 'tel:+33100000000',
      icon: 'phone'
    },
    {
      title: 'Par email',
      text: 'Recevez une réponse claire et centralisée sur vos contrats et vos démarches.',
      action: 'Nous écrire',
      href: 'mailto:contact@insurflow.com',
      icon: 'mail'
    },
    {
      title: 'Depuis votre espace',
      text: 'Retrouvez vos documents, votre suivi et vos échanges au même endroit.',
      action: 'Accéder à mon espace',
      href: '/login',
      icon: 'chat'
    }
  ];
}
