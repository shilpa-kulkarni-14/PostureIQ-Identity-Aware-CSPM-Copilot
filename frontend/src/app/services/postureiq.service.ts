import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ScanResult, AiFindingDetails, HighRiskIdentity } from '../models/finding.model';
import { environment } from '../../environments/environment';

@Injectable({
  providedIn: 'root'
})
export class PostureIqService {
  private apiUrl = environment.apiUrl;

  constructor(private http: HttpClient) {}

  runIamScan(): Observable<ScanResult> {
    return this.http.post<ScanResult>(`${this.apiUrl}/scan/iam`, {});
  }

  correlate(): Observable<ScanResult> {
    return this.http.post<ScanResult>(`${this.apiUrl}/scan/correlate`, {});
  }

  enrichScan(scanId: string): Observable<AiFindingDetails[]> {
    return this.http.post<AiFindingDetails[]>(`${this.apiUrl}/scan/${scanId}/enrich`, {});
  }

  getHighRiskIdentities(): Observable<HighRiskIdentity[]> {
    return this.http.get<HighRiskIdentity[]>(`${this.apiUrl}/identities/high-risk`);
  }
}
