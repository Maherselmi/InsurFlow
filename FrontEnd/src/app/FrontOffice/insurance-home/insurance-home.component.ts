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
    { label: 'Sinistres', href: '#sinistres' },
    { label: 'Pourquoi InSurFlow', href: '#pourquoi' },
    { label: 'Contact', href: '#contact' }
  ];

  stats: StatItem[] = [
    { value: '98%', label: 'SINISTRES TRAITÉS SOUS 7 JOURS' },
    { value: '24/7', label: "ASSISTANCE D'URGENCE" },
    { value: '180k', label: 'ASSURÉS EN FRANCE' },
    { value: '1962', label: 'ANNÉE DE FONDATION' }
  ];

  offers: OfferItem[] = [
    {
      id: '01',
      title: 'Auto',
      description: 'Tous risques, intermédiaire ou tiers. Assistance 24/7 et véhicule de remplacement.',
      cta: 'Demander un devis',
      image: 'assets/images/auto-insurance.jpg'
    },
    {
      id: '02',
      title: 'Habitation',
      description: 'Résidence principale, secondaire ou locative. Couverture multirisque sur mesure.',
      cta: 'Demander un devis',
      image: 'assets/images/home-insurance.jpg'
    },
    {
      id: '03',
      title: 'Santé',
      description: 'Complémentaires individuelles et familiales. Tiers payant généralisé.',
      cta: 'Demander un devis',
      image: 'assets/images/health-insurance.jpg'
    },
    {
      id: '04',
      title: 'Vie & Prévoyance',
      description: 'Épargne, transmission, garantie décès. Conseillers patrimoniaux dédiés.',
      cta: 'Demander un devis',
      image: 'assets/images/Vie.png'
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
}
