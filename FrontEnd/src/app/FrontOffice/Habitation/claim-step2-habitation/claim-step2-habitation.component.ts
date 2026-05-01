import { Component, Inject, OnInit, PLATFORM_ID } from '@angular/core';
import { CommonModule, isPlatformBrowser } from '@angular/common';
import { Router, RouterModule } from '@angular/router';
import { ClaimService } from '../../../claim.service';

interface NavItem {
  label: string;
  route: string;
}

interface UploadTip {
  title: string;
  text: string;
  icon: string;
}

@Component({
  selector: 'app-claim-step2-habitation',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './claim-step2-habitation.component.html',
  styleUrls: ['./claim-step2-habitation.component.css'],
  host: { ngSkipHydration: 'true' }
})
export class ClaimStep2HabitationComponent implements OnInit {
  navItems: NavItem[] = [
    { label: 'Tableau de bord', route: '/Client_Space' },
    { label: 'Mes contrats', route: '/PolicesList' },
    { label: 'Sinistres', route: '/Claim_Home' },
    { label: 'Mes dossiers', route: '/Consulter' }
  ];

  uploadTips: UploadTip[] = [
    {
      title: 'Vue générale',
      text: 'Ajoutez une photo complète de la pièce ou de la zone touchée.',
      icon: 'V'
    },
    {
      title: 'Dégâts détaillés',
      text: 'Ajoutez un gros plan des éléments endommagés.',
      icon: 'D'
    },
    {
      title: 'Origine visible',
      text: 'Montrez la fuite, fissure ou la source du sinistre si possible.',
      icon: 'O'
    },
    {
      title: 'Pièces utiles',
      text: 'Ajoutez devis, factures, constats ou autres justificatifs.',
      icon: 'P'
    }
  ];

  files: File[] = [];
  claimId = 0;
  loading = false;
  errorMessage = '';
  successMessage = '';
  uploadProgress = 0;

  constructor(
    private claimService: ClaimService,
    private router: Router,
    @Inject(PLATFORM_ID) private platformId: Object
  ) {}

  ngOnInit(): void {
    if (isPlatformBrowser(this.platformId)) {
      const stored = localStorage.getItem('claimId');

      if (stored) {
        this.claimId = Number(stored);
      } else {
        this.errorMessage = 'Aucun dossier trouvé. Retournez à l’étape 1.';
      }
    }
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

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const selectedFiles: File[] = Array.from(input.files || []);
    this.addFiles(selectedFiles);
  }

  onDragOver(event: DragEvent): void {
    event.preventDefault();
  }

  onDrop(event: DragEvent): void {
    event.preventDefault();
    const droppedFiles: File[] = Array.from(event.dataTransfer?.files || []);
    this.addFiles(droppedFiles);
  }

  private addFiles(newFiles: File[]): void {
    const allowed = ['image/png', 'image/jpeg', 'application/pdf'];
    const maxSize = 10 * 1024 * 1024;

    this.errorMessage = '';
    this.successMessage = '';

    newFiles.forEach((file) => {
      if (!allowed.includes(file.type)) {
        this.errorMessage = 'Seuls les fichiers JPG, PNG et PDF sont autorisés.';
        return;
      }

      if (file.size > maxSize) {
        this.errorMessage = 'Chaque fichier doit faire moins de 10 Mo.';
        return;
      }

      const alreadyExists = this.files.some(
        (f) =>
          f.name === file.name &&
          f.size === file.size &&
          f.lastModified === file.lastModified
      );

      if (!alreadyExists) {
        this.files.push(file);
      }
    });
  }

  removeFile(index: number): void {
    this.files.splice(index, 1);
  }

  clearFiles(input?: HTMLInputElement): void {
    this.files = [];
    this.errorMessage = '';
    this.successMessage = '';
    this.uploadProgress = 0;

    if (input) {
      input.value = '';
    }
  }

  isPdf(file: File): boolean {
    return file.type === 'application/pdf';
  }

  isImage(file: File): boolean {
    return file.type.startsWith('image/');
  }

  formatSize(bytes: number): string {
    if (bytes < 1024 * 1024) {
      return (bytes / 1024).toFixed(1) + ' Ko';
    }

    return (bytes / (1024 * 1024)).toFixed(1) + ' Mo';
  }

  get totalFiles(): number {
    return this.files.length;
  }

  get totalSize(): string {
    const total = this.files.reduce((sum, file) => sum + file.size, 0);
    return this.formatSize(total);
  }

  get canUpload(): boolean {
    return !this.loading && this.files.length > 0 && !!this.claimId;
  }

  back(): void {
    this.router.navigate(['/claim/habitation/step1']);
  }

  upload(): void {
    if (!this.claimId) {
      this.errorMessage = 'ClaimId introuvable. Retournez à l’étape 1.';
      return;
    }

    if (this.files.length === 0) {
      this.errorMessage = 'Ajoutez au moins un document habitation.';
      return;
    }

    this.loading = true;
    this.errorMessage = '';
    this.successMessage = '';
    this.uploadProgress = 0;

    let completed = 0;

    this.files.forEach((file) => {
      this.claimService.uploadDocument(this.claimId, file).subscribe({
        next: () => {
          completed++;
          this.uploadProgress = Math.round((completed / this.files.length) * 100);

          if (completed === this.files.length) {
            this.claimService.processClaim(this.claimId).subscribe({
              next: () => {
                this.loading = false;
                this.successMessage = 'Documents habitation envoyés avec succès. Redirection...';

                setTimeout(() => {
                  this.router.navigate(['/Habitation/step3']);
                }, 1000);
              },
              error: (err) => {
                this.loading = false;
                this.errorMessage =
                  `Erreur traitement : ${err?.message || 'Erreur inconnue.'}`;
              }
            });
          }
        },
        error: (err) => {
          this.loading = false;
          this.errorMessage =
            `Erreur upload : ${err?.message || 'Erreur inconnue.'}`;
        }
      });
    });
  }

  goToClaimsHome(): void {
    this.router.navigate(['/Claim_Home']);
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
