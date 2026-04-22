import { Component, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { CommonModule } from '@angular/common';
import { ClaimService } from '../../claim.service';

@Component({
  selector: 'app-claim-step3',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './claim-step3.component.html',
  styleUrls: ['./claim-step3.component.css']
})
export class ClaimStep3Component implements OnInit {

  reference: string = '';

  constructor(
    private router: Router,
    private claimService: ClaimService
  ) {}

  ngOnInit(): void {
    // simulation ou backend
    this.reference = this.generateReference();
  }

  generateReference(): string {
    const rand = Math.floor(Math.random() * 10000);
    return `SIN-2026-${rand}`;
  }

  newClaim() {
    this.router.navigate(['/claim/step1']);
  }
  goToHome(): void {
    console.log('🏠 Navigation vers la page d\'accueil');
    this.router.navigate(['/']);
  }

  /**
   * Navigation vers la page de consultation des décisions
   */
  goToDecisions(): void {
    console.log('📋 Navigation vers la page des décisions');
    this.router.navigate(['/Consulter']);
  }
  goToPolice(): void {
    this.router.navigate(['/PolicesList']);
  }
  logout(): void {
    this.router.navigate(['/login']);
  }
}
