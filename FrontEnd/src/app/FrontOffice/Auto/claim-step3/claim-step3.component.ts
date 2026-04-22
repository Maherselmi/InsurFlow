import { Component, OnInit } from '@angular/core';
import { Router, RouterModule } from '@angular/router';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-claim-step3',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './claim-step3.component.html',
  styleUrls: ['./claim-step3.component.css']
})
export class ClaimStep3Component implements OnInit {
  navItems = [
    { label: 'Tableau de bord', route: '/Client_Space' },
    { label: 'Mes contrats', route: '/contrats' },
    { label: 'Sinistres', route: '/Claim_Home' },
    { label: 'Documents', route: '/documents' }
  ];

  reference = '';

  constructor(private router: Router) {}

  ngOnInit(): void {
    this.reference = this.generateReference();
  }

  generateReference(): string {
    const storedClaimId = localStorage.getItem('claimId');

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
    this.router.navigate(['/sinistres-home']);
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
