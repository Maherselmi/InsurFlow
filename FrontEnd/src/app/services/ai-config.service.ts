import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface AiAgentConfig {
  id?: number;
  agentName: string;
  confidenceThreshold: number;
}

@Injectable({
  providedIn: 'root'
})
export class AiConfigService {
  private readonly apiUrl = 'http://localhost:8080/api/admin/ai-config';

  constructor(private http: HttpClient) {}

  getAllConfigs(): Observable<AiAgentConfig[]> {
    return this.http.get<AiAgentConfig[]>(this.apiUrl);
  }

  updateConfig(config: AiAgentConfig): Observable<AiAgentConfig> {
    return this.http.put<AiAgentConfig>(this.apiUrl, {
      agentName: config.agentName,
      confidenceThreshold: config.confidenceThreshold
    });
  }
}
