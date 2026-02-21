import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { RemediationRequest, RemediationResponse } from '../models/finding.model';
import { environment } from '../../environments/environment';

@Injectable({
  providedIn: 'root'
})
export class ClaudeService {
  private apiUrl = environment.apiUrl;

  constructor(private http: HttpClient) {}

  getRemediation(request: RemediationRequest): Observable<RemediationResponse> {
    return this.http.post<RemediationResponse>(`${this.apiUrl}/remediate`, request);
  }
}
