import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ExpertFeedbackRequest } from '../models/expert-feedback.model';

@Injectable({
  providedIn: 'root'
})
export class ExpertFeedbackService {
  private apiUrl = 'http://localhost:8080/api/admin/expert-feedback';

  constructor(private http: HttpClient) {}

  getFeedbackByClaimId(claimId: number): Observable<any> {
    return this.http.get(`${this.apiUrl}/claim/${claimId}`);
  }

  saveFeedback(payload: ExpertFeedbackRequest): Observable<any> {
    return this.http.post(this.apiUrl, payload);
  }
}
