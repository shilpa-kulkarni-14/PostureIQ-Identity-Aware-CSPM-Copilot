import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

export interface DashboardStats {
  totalScans: number;
  totalFindings: number;
  complianceScore: number;
  severityDistribution: { HIGH: number; MEDIUM: number; LOW: number };
  findingsByResourceType: { [key: string]: number };
  scanHistory: ScanHistoryEntry[];
}

export interface ScanHistoryEntry {
  scanId: string;
  timestamp: string;
  totalFindings: number;
  highSeverity: number;
  mediumSeverity: number;
  lowSeverity: number;
}

@Injectable({
  providedIn: 'root'
})
export class DashboardService {
  private apiUrl = environment.apiUrl;

  constructor(private http: HttpClient) {}

  getStats(): Observable<DashboardStats> {
    return this.http.get<DashboardStats>(`${this.apiUrl}/dashboard/stats`);
  }
}
