import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { RemediationRequest, RemediationResponse } from '../models/finding.model';

@Injectable({
  providedIn: 'root'
})
export class ClaudeService {
  private apiUrl = 'http://localhost:8080/api';

  constructor(private http: HttpClient) {}

  getRemediation(request: RemediationRequest): Observable<RemediationResponse> {
    return this.http.post<RemediationResponse>(`${this.apiUrl}/remediate`, request);
  }
}
