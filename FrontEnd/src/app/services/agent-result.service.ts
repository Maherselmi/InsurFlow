import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface AgentResult {
  id: number;
  agentName: string;
  conclusion: string;
  confidenceScore: number;
  needsHumanReview: boolean;
  rawLlmResponse: string;
  createdAt: string;
  claim?: {
    id: number;
    description: string;
    status: string;
    policy?: {
      type: string;
      policyNumber: string;
      client?: {
        firstName: string;
        lastName: string;
      };
    };
  };
}

@Injectable({ providedIn: 'root' })
export class AgentResultService {
  private apiUrl = 'http://localhost:8080/api/agent-results';

  constructor(private http: HttpClient) {}

  getAll(): Observable<AgentResult[]> {
    return this.http.get<AgentResult[]>(this.apiUrl);
  }

  getByClaim(claimId: number): Observable<AgentResult[]> {
    return this.http.get<AgentResult[]>(`${this.apiUrl}/claim/${claimId}`);
  }
}
