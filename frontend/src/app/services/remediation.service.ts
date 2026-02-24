import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { AutoRemediationRequest, AutoRemediationResponse, RemediationAudit } from '../models/finding.model';

@Injectable({ providedIn: 'root' })
export class RemediationService {
  private apiUrl = environment.apiUrl;

  constructor(private http: HttpClient) {}

  triggerAutoRemediation(request: AutoRemediationRequest): Observable<AutoRemediationResponse> {
    return this.http.post<AutoRemediationResponse>(`${this.apiUrl}/remediate/auto`, request);
  }

  getAuditTrail(findingId: string): Observable<RemediationAudit[]> {
    return this.http.get<RemediationAudit[]>(`${this.apiUrl}/remediate/audit/${findingId}`);
  }

  getSessionAudit(sessionId: string): Observable<RemediationAudit[]> {
    return this.http.get<RemediationAudit[]>(`${this.apiUrl}/remediate/audit/session/${sessionId}`);
  }
}
