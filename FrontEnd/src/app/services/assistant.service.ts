import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface AssistantRequest {
  message: string;
}

export interface AssistantResponse {
  answer: string;
}

@Injectable({
  providedIn: 'root'
})
export class AssistantService {
  private apiUrl = 'http://localhost:8080/api/assistant/chat';

  constructor(private http: HttpClient) {}

  sendMessage(message: string): Observable<AssistantResponse> {
    return this.http.post<AssistantResponse>(this.apiUrl, { message });
  }
}
