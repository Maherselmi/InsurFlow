import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Claim } from '../models/Claim/claim.model';

export interface ReviewData {
  id: number;
  description: string;
  incidentDate: string;
  createdAt: string;
  status: string;
  aiReport: string;
}

export interface DecisionResponse {
  message: string;
  claimId: number;
  status: string;
}

@Injectable({
  providedIn: 'root'
})
export class ClaimValidationService {
  private readonly BASE_URL = 'http://localhost:8080/api/claims';

  constructor(private http: HttpClient) {}

  getPendingClaims(): Observable<Claim[]> {
    return this.http.get<Claim[]>(`${this.BASE_URL}/pending-validation`);
  }

  getClaimReview(id: number): Observable<ReviewData> {
    return this.http.get<ReviewData>(`${this.BASE_URL}/${id}/review`);
  }

  approveClaim(id: number, comment: string): Observable<DecisionResponse> {
    return this.http.post<DecisionResponse>(`${this.BASE_URL}/${id}/approve`, { comment });
  }

  rejectClaim(id: number, comment: string): Observable<DecisionResponse> {
    return this.http.post<DecisionResponse>(`${this.BASE_URL}/${id}/reject`, { comment });
  }
}
