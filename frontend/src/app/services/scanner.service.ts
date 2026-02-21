import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ScanResult } from '../models/finding.model';

@Injectable({
  providedIn: 'root'
})
export class ScannerService {
  private apiUrl = 'http://localhost:8080/api';

  constructor(private http: HttpClient) {}

  triggerScan(): Observable<ScanResult> {
    return this.http.post<ScanResult>(`${this.apiUrl}/scan`, {});
  }

  getScanResult(scanId: string): Observable<ScanResult> {
    return this.http.get<ScanResult>(`${this.apiUrl}/scan/${scanId}`);
  }

  downloadPdfReport(scanId: string): Observable<Blob> {
    return this.http.get(`${this.apiUrl}/scan/${scanId}/report`, {
      responseType: 'blob'
    });
  }

  exportScanJson(scanId: string): Observable<Blob> {
    return this.http.get(`${this.apiUrl}/scan/${scanId}/export`, {
      responseType: 'blob'
    });
  }
}
