import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface PolicyClient {
  id: number;
  firstName: string;
  lastName: string;
  email?: string;
  phone?: string;
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

@Injectable({
  providedIn: 'root'
})
export class PolicyService {
  private readonly apiUrl = 'http://localhost:8080/api/policies';

  constructor(private http: HttpClient) {}

  getAllPolicies(): Observable<Policy[]> {
    return this.http.get<Policy[]>(this.apiUrl);
  }

  getPolicyById(id: number): Observable<Policy> {
    return this.http.get<Policy>(`${this.apiUrl}/${id}`);
  }
}
