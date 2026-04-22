import { Component, Inject, OnInit, PLATFORM_ID } from '@angular/core';
import { CommonModule, isPlatformBrowser } from '@angular/common';
import { Router } from '@angular/router';
import { ClaimService } from '../../claim.service';

@Component({
  selector: 'app-claim-step2',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './claim-step2.component.html',
  styleUrls: ['./claim-step2.component.css'],
  host: { ngSkipHydration: 'true' }
})
export class ClaimStep2Component implements OnInit {

  files:          File[]  = [];
  claimId:        number  = 0;
  loading:        boolean = false;
  errorMessage:   string  = '';
  successMessage: string  = '';
  uploadProgress: number  = 0;

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
        console.log('✅ ClaimId récupéré:', this.claimId);
      } else {
        this.errorMessage = 'Aucun dossier trouvé. Retournez à l\'étape 1.';
        console.error('❌ claimId introuvable dans localStorage');
      }
    }
  }

  onFileSelected(event: any): void {
    const selectedFiles: File[] = Array.from(event.target.files);
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
    const allowed   = ['image/png', 'image/jpeg', 'application/pdf'];
    const maxSize   = 10 * 1024 * 1024;
    newFiles.forEach(file => {
      if (!allowed.includes(file.type)) return;
      if (file.size > maxSize)          return;
      this.files.push(file);
    });
  }

  removeFile(index: number): void {
    this.files.splice(index, 1);
  }

  isPdf(file: File):   boolean { return file.type === 'application/pdf'; }
  isImage(file: File): boolean { return file.type.startsWith('image/'); }

  formatSize(bytes: number): string {
    if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' Ko';
    return (bytes / (1024 * 1024)).toFixed(1) + ' Mo';
  }

  back(): void {
    this.router.navigate(['/claim/step1']);
  }

  upload(): void {
    if (!this.claimId) {
      this.errorMessage = 'ClaimId introuvable. Retournez à l\'étape 1.';
      return;
    }
    if (this.files.length === 0) {
      this.errorMessage = 'Ajoutez au moins un fichier.';
      return;
    }

    this.loading        = true;
    this.errorMessage   = '';
    this.successMessage = '';
    this.uploadProgress = 0;

    let completed = 0;

    this.files.forEach(file => {
      this.claimService.uploadDocument(this.claimId, file).subscribe({
        next: (res) => {
          completed++;
          this.uploadProgress = Math.round((completed / this.files.length) * 100);
          console.log(`✅ Fichier ${completed}/${this.files.length} uploadé:`, res);

          if (completed === this.files.length) {
            // 🆕 Déclencher l'orchestrateur UNE SEULE FOIS après tous les uploads
            this.claimService.processClaim(this.claimId).subscribe({
              next: () => {
                this.loading        = false;
                this.successMessage = 'Documents envoyés avec succès ! Redirection...';
                console.log('✅ Orchestrateur déclenché !');
                setTimeout(() => {
                  this.router.navigate(['/claim/step3']);
                }, 1000);
              },
              error: (err) => {
                this.loading      = false;
                this.errorMessage = `Erreur traitement : ${err.message}`;
                console.error('❌ Erreur orchestrateur:', err);
              }
            });
          }
        },
        error: (err) => {
          this.loading      = false;
          this.errorMessage = `Erreur upload : ${err.message}`;
          console.error('❌ Upload error:', err);
        }
      });
    });
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
