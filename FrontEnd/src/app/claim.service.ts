import { Injectable } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Observable, throwError } from 'rxjs';
import { catchError, tap } from 'rxjs/operators';
import {Claim} from "./models/Claim/claim.model";
import {PolicyClient} from "./services/policy.service";

export interface ClaimData {
  id?:          number;
  policyId:     number | null;
  clientId:     number | null;
  incidentDate: string;
  type:         string;
  description:  string;
}

export interface Policy {
  id: number;
  policyNumber: string;
  type: string;
  formule?: string;
  productCode?: string;
  startDate: string;
  endDate: string;
  coverageDetails?: string;
  client?: PolicyClient;
}

@Injectable({ providedIn: 'root' })
export class ClaimService {

  private apiUrl      = 'http://localhost:8080/api/claims';
  private policyUrl   = 'http://localhost:8080/api/policies';
  private documentUrl = 'http://localhost:8080/api/documents';

  constructor(private http: HttpClient) {}

  createClaim(claim: ClaimData): Observable<any> {
    const body = {
      description:  claim.description.trim(),
      incidentDate: claim.incidentDate,
      client: { id: Number(claim.clientId) },
      policy: { id: Number(claim.policyId) }
    };
    console.log('📤 Body envoyé:', JSON.stringify(body, null, 2));
    return this.http.post(this.apiUrl, body).pipe(
        tap(res => console.log('✅ Claim créé:', res)),
        catchError(this.handleError)
    );
  }

  getPolicies(): Observable<Policy[]> {
    return this.http.get<Policy[]>(this.policyUrl).pipe(
        tap(res => console.log('✅ Policies:', res)),
        catchError(this.handleError)
    );
  }
  processClaim(claimId: number): Observable<any> {
    return this.http.post(`${this.documentUrl}/process/${claimId}`, {}, {
      responseType: 'text'  // 🆕 important — le backend retourne du texte pas du JSON
    }).pipe(
        tap(res => console.log('✅ Orchestrateur déclenché:', res)),
        catchError(this.handleError)
    );
  }

  // ✅ URL corrigée : /api/documents/upload/{claimId}
  uploadDocument(claimId: number, file: File): Observable<any> {
    const formData = new FormData();
    formData.append('file', file);
    return this.http.post(`${this.documentUrl}/upload/${claimId}`, formData).pipe(
        tap(res  => console.log('✅ Document uploadé:', res)),
        catchError(this.handleError)
    );
  }

  private handleError(error: HttpErrorResponse) {
    console.error('❌ HTTP Error:', error.status, error.error);
    return throwError(() => ({
      status:  error.status,
      message: error.error?.message || 'Erreur serveur'
    }));
  }

}
