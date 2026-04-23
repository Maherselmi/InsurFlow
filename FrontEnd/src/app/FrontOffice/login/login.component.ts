import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterModule } from '@angular/router';
import { AuthService } from '../../services/auth.service';

interface LoginTrustItem {
  value: string;
  label: string;
}

interface LoginFeatureItem {
  title: string;
  text: string;
  icon: 'shield' | 'folder' | 'clock';
}

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule],
  templateUrl: './login.component.html',
  styleUrls: ['./login.component.css']
})
export class LoginComponent {
  email = '';
  password = '';
  loading = false;
  errorMessage = '';
  showPassword = false;

  trustItems: LoginTrustItem[] = [
    { value: '24/7', label: 'Accès continu à votre espace' },
    { value: '100%', label: 'Données centralisées et sécurisées' },
    { value: '1', label: 'Plateforme unique pour tous vos suivis' }
  ];

  featureItems: LoginFeatureItem[] = [
    {
      title: 'Connexion sécurisée',
      text: 'Accès protégé à vos informations personnelles et à vos contrats.',
      icon: 'shield'
    },
    {
      title: 'Documents organisés',
      text: 'Retrouvez vos pièces, justificatifs et dossiers au même endroit.',
      icon: 'folder'
    },
    {
      title: 'Suivi instantané',
      text: 'Consultez l’état de vos démarches et sinistres en temps réel.',
      icon: 'clock'
    }
  ];

  constructor(
    private authService: AuthService,
    private router: Router
  ) {}

  onLogin(): void {
    this.errorMessage = '';

    if (!this.email || !this.password) {
      this.errorMessage = 'Veuillez remplir tous les champs.';
      return;
    }

    this.loading = true;

    this.authService.login({
      email: this.email,
      password: this.password
    }).subscribe({
      next: (response) => {
        this.loading = false;

        switch (response.role) {
          case 'ROLE_ADMIN':
            this.router.navigate(['/dashboard']);
            break;
          case 'ROLE_EXPERT':
            this.router.navigate(['/Validation']);
            break;
          case 'ROLE_CLIENT':
            this.router.navigate(['/Client_Space']);
            break;
          default:
            this.router.navigate(['/']);
            break;
        }
      },
      error: (err) => {
        this.loading = false;
        console.error('Erreur login:', err);
        this.errorMessage = 'Email ou mot de passe incorrect.';
      }
    });
  }

  togglePassword(): void {
    this.showPassword = !this.showPassword;
  }
}
