import { Component, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatIconModule } from '@angular/material/icon';
import { MatChipsModule } from '@angular/material/chips';
import { ScannerService } from '../../services/scanner.service';
import { ScanResult } from '../../models/finding.model';
import { FindingCardComponent } from '../finding-card/finding-card.component';

@Component({
  selector: 'app-scanner',
  standalone: true,
  imports: [
    CommonModule,
    MatButtonModule,
    MatCardModule,
    MatProgressSpinnerModule,
    MatSnackBarModule,
    MatIconModule,
    MatChipsModule,
    FindingCardComponent
  ],
  templateUrl: './scanner.component.html',
  styleUrl: './scanner.component.scss'
})
export class ScannerComponent {
  isScanning = signal(false);
  scanResult = signal<ScanResult | null>(null);
  hasScanned = signal(false);
  isExportingPdf = signal(false);
  isExportingJson = signal(false);

  constructor(
    private scannerService: ScannerService,
    private snackBar: MatSnackBar
  ) {}

  runScan(): void {
    this.isScanning.set(true);
    this.scanResult.set(null);

    this.scannerService.triggerScan().subscribe({
      next: (result) => {
        this.scanResult.set(result);
        this.hasScanned.set(true);
        this.isScanning.set(false);
        this.snackBar.open(
          `Scan completed: ${result.totalFindings} findings detected`,
          'Dismiss',
          { duration: 3000 }
        );
      },
      error: (error) => {
        this.isScanning.set(false);
        this.snackBar.open(
          'Error running scan. Please ensure the backend is running.',
          'Dismiss',
          { duration: 5000 }
        );
        console.error('Scan error:', error);
      }
    });
  }

  exportPdf(): void {
    const result = this.scanResult();
    if (!result) return;

    this.isExportingPdf.set(true);
    this.scannerService.downloadPdfReport(result.scanId).subscribe({
      next: (blob) => {
        this.downloadFile(blob, `cspm-report-${result.scanId}.pdf`);
        this.isExportingPdf.set(false);
        this.snackBar.open('PDF report downloaded', 'Dismiss', { duration: 3000 });
      },
      error: (error) => {
        this.isExportingPdf.set(false);
        this.snackBar.open('Error generating PDF report', 'Dismiss', { duration: 5000 });
        console.error('PDF export error:', error);
      }
    });
  }

  exportJson(): void {
    const result = this.scanResult();
    if (!result) return;

    this.isExportingJson.set(true);
    this.scannerService.exportScanJson(result.scanId).subscribe({
      next: (blob) => {
        this.downloadFile(blob, `cspm-scan-${result.scanId}.json`);
        this.isExportingJson.set(false);
        this.snackBar.open('JSON export downloaded', 'Dismiss', { duration: 3000 });
      },
      error: (error) => {
        this.isExportingJson.set(false);
        this.snackBar.open('Error exporting JSON', 'Dismiss', { duration: 5000 });
        console.error('JSON export error:', error);
      }
    });
  }

  private downloadFile(blob: Blob, filename: string): void {
    const url = window.URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = filename;
    a.click();
    window.URL.revokeObjectURL(url);
  }

  getSeverityClass(severity: string): string {
    return severity.toLowerCase();
  }
}
