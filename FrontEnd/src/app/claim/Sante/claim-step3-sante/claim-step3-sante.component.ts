import { Component, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { CommonModule } from '@angular/common';
import {ClaimService} from "../../../claim.service";

@Component({
  selector: 'app-claim-step3-sante',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './claim-step3-sante.component.html',
  styleUrls: ['./claim-step3-sante.component.css']
})
export class ClaimStep3SanteComponent implements OnInit {

  reference: string = '';

  constructor(
    private router: Router,
    private claimService: ClaimService
  ) {}

  ngOnInit(): void {
    this.reference = this.generateReference();
  }

  generateReference(): string {
    const rand = Math.floor(Math.random() * 10000);
    return `SANTE-2026-${rand}`;
  }

  newClaim() {
    this.router.navigate(['/claim/sante/step1']);
  }
  goToSinistre(): void {
    this.router.navigate(['/sinistre']);
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
