import { Component } from '@angular/core';
import { Router } from '@angular/router';
import {TopbarComponent} from "../../components/topbar/topbar.component";

@Component({
  selector: 'app-home',
  standalone: true,
  imports: [TopbarComponent],
  templateUrl: './home.component.html',
  styleUrls: ['./home.component.css']
})
export class HomeComponent {

  constructor(private router: Router) {}

  goToClaim(type: string) {
    this.router.navigate(['/claim/step1'], {
      queryParams: { type }
    });
  }
  goToClaimSante(type: string) {
    this.router.navigate(['/Sante/step1'], {
      queryParams: { type }
    });
  }
  goToClaimHabitation(type: string) {
    this.router.navigate(['/Habitation/step1'], {
      queryParams: { type }
    });
  }

  goToClaims() {
    this.router.navigate(['/dossiers']);
  }

  goToProfile() {
    this.router.navigate(['/profile']);
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
  logout(): void {
    this.router.navigate(['/login']);
  }
  goToPolice(): void {
    this.router.navigate(['/PolicesList']);
  }

}
