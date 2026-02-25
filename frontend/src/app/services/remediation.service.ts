import { Injectable, NgZone } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import {
  ApprovalRequest,
  AutoRemediationRequest,
  AutoRemediationResponse,
  RemediationAudit,
  RemediationProgressEvent
} from '../models/finding.model';

@Injectable({ providedIn: 'root' })
export class RemediationService {
  private apiUrl = environment.apiUrl;

  constructor(private http: HttpClient, private ngZone: NgZone) {}

  triggerAutoRemediation(request: AutoRemediationRequest): Observable<AutoRemediationResponse> {
    return this.http.post<AutoRemediationResponse>(`${this.apiUrl}/remediate/auto`, request);
  }

  streamProgress(sessionId: string): Observable<RemediationProgressEvent> {
    return new Observable(observer => {
      const url = `${this.apiUrl}/remediate/stream/${sessionId}`;
      const eventSource = new EventSource(url);

      eventSource.addEventListener('progress', (event: MessageEvent) => {
        this.ngZone.run(() => {
          try {
            const data: RemediationProgressEvent = JSON.parse(event.data);
            observer.next(data);
            if (data.type === 'COMPLETED' || data.type === 'ERROR') {
              eventSource.close();
              observer.complete();
            }
          } catch (e) {
            console.error('Failed to parse SSE event:', e);
          }
        });
      });

      eventSource.onerror = () => {
        this.ngZone.run(() => {
          eventSource.close();
          observer.complete();
        });
      };

      return () => {
        eventSource.close();
      };
    });
  }

  approveRemediation(approval: ApprovalRequest): Observable<AutoRemediationResponse> {
    return this.http.post<AutoRemediationResponse>(`${this.apiUrl}/remediate/approve`, approval);
  }

  getAuditTrail(findingId: string): Observable<RemediationAudit[]> {
    return this.http.get<RemediationAudit[]>(`${this.apiUrl}/remediate/audit/${findingId}`);
  }

  getSessionAudit(sessionId: string): Observable<RemediationAudit[]> {
    return this.http.get<RemediationAudit[]>(`${this.apiUrl}/remediate/audit/session/${sessionId}`);
  }
}
