import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ExpertFeedbackRequest, ExpertFeedbackResponse } from '../models/expert-feedback.model';

@Injectable({
  providedIn: 'root'
})
export class ExpertFeedbackService {
  /**
   * Backend corrigé : @RequestMapping("/api/expert-feedback")
   * Si tu utilises environment.apiUrl dans ton projet, remplace localhost par environment.apiUrl.
   */
  private readonly apiUrl = 'http://localhost:8080/api/expert-feedback';

  constructor(private http: HttpClient) {}

  /**
   * Optionnel : garde cette méthode seulement si tu ajoutes aussi le GET côté backend.
   * Le composant ignore l'erreur si ce endpoint n'existe pas encore.
   */
  getFeedbackByClaimId(claimId: number): Observable<ExpertFeedbackRequest> {
    return this.http.get<ExpertFeedbackRequest>(`${this.apiUrl}/claim/${claimId}`);
  }

  saveFeedback(payload: ExpertFeedbackRequest): Observable<ExpertFeedbackResponse> {
    return this.http.post<ExpertFeedbackResponse>(this.apiUrl, payload);
  }
}
