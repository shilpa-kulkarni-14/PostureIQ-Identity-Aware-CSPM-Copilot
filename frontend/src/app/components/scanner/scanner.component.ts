import { Component, signal, computed, OnDestroy, AfterViewInit, ViewChild, ElementRef } from '@angular/core';
import { CommonModule, DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatIconModule } from '@angular/material/icon';
import { MatChipsModule } from '@angular/material/chips';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatTableModule } from '@angular/material/table';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { RouterLink } from '@angular/router';
import { Subscription } from 'rxjs';
import { ScannerService } from '../../services/scanner.service';
import { ScanResult, Finding, AutoRemediationResponse } from '../../models/finding.model';
import { RemediationCompleteEvent } from '../finding-card/finding-card.component';
import { ClaudeService } from '../../services/claude.service';
import { RemediationDialogComponent } from '../remediation-dialog/remediation-dialog.component';
import { AutoRemediationDialogComponent } from '../auto-remediation-dialog/auto-remediation-dialog.component';

@Component({
  selector: 'app-scanner',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatButtonModule,
    MatCardModule,
    MatProgressSpinnerModule,
    MatProgressBarModule,
    MatSnackBarModule,
    MatIconModule,
    MatChipsModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonToggleModule,
    MatTableModule,
    MatDialogModule,
    DatePipe,
    RouterLink
  ],
  templateUrl: './scanner.component.html',
  styleUrl: './scanner.component.scss'
})
export class ScannerComponent implements OnDestroy, AfterViewInit {
  @ViewChild('pageHeading') pageHeading!: ElementRef;

  displayedColumns = ['severity', 'title', 'resourceType', 'resourceId', 'status', 'actions'];
  expandedFindingId = signal<string | null>(null);
  loadingRemediation = signal<Set<string>>(new Set());
  loadingAutoRemediation = signal<Set<string>>(new Set());

  isScanning = signal(false);
  scanResult = signal<ScanResult | null>(null);
  hasScanned = signal(false);
  isExportingPdf = signal(false);
  isExportingJson = signal(false);
  scanElapsedSeconds = signal(0);

  readonly scanMessages = [
    'Connecting to AWS environment...',
    'Enumerating S3 bucket policies...',
    'Auditing IAM roles and permission boundaries...',
    'Checking EC2 security group configurations...',
    'Inspecting EBS volume encryption status...',
    'Cross-referencing CIS Benchmark controls...',
    'Compiling findings and risk scores...',
  ];
  currentScanMessage = computed(() => {
    const idx = Math.floor(this.scanElapsedSeconds() / 2) % this.scanMessages.length;
    return this.scanMessages[idx];
  });

  // Filter state
  severityFilter = signal<string[]>([]);
  resourceTypeFilter = signal<string>('');
  searchQuery = signal('');
  sortBy = signal<'severity' | 'resourceType' | 'title'>('severity');
  showRemediated = signal(true);

  // Track remediation status per finding
  remediationStatuses = signal<Map<string, Finding['remediationStatus']>>(new Map());

  private scanSubscription: Subscription | null = null;
  private timerInterval: ReturnType<typeof setInterval> | null = null;

  filteredFindings = computed(() => {
    const result = this.scanResult();
    if (!result) return [];

    const statuses = this.remediationStatuses();
    let findings = result.findings.map(f => ({
      ...f,
      remediationStatus: statuses.get(f.id) || f.remediationStatus
    }));

    // Filter by remediation status
    if (!this.showRemediated()) {
      findings = findings.filter(f => !f.remediationStatus || f.remediationStatus === 'OPEN');
    }

    // Filter by severity
    const severities = this.severityFilter();
    if (severities.length > 0) {
      findings = findings.filter(f => severities.includes(f.severity));
    }

    // Filter by resource type
    const resType = this.resourceTypeFilter();
    if (resType) {
      findings = findings.filter(f => f.resourceType === resType);
    }

    // Filter by search query
    const query = this.searchQuery().toLowerCase();
    if (query) {
      findings = findings.filter(f =>
        f.title.toLowerCase().includes(query) ||
        f.description.toLowerCase().includes(query) ||
        f.resourceId.toLowerCase().includes(query)
      );
    }

    // Sort
    const sort = this.sortBy();
    findings.sort((a, b) => {
      if (sort === 'severity') {
        const order: Record<string, number> = { HIGH: 0, MEDIUM: 1, LOW: 2 };
        return (order[a.severity] ?? 3) - (order[b.severity] ?? 3);
      }
      if (sort === 'resourceType') return a.resourceType.localeCompare(b.resourceType);
      return a.title.localeCompare(b.title);
    });

    return findings;
  });

  remediatedCount = computed(() => {
    const statuses = this.remediationStatuses();
    let count = 0;
    statuses.forEach(status => {
      if (status === 'REMEDIATED' || status === 'FAILED' || status === 'PARTIAL') {
        count++;
      }
    });
    return count;
  });

  resourceTypes = computed(() => {
    const result = this.scanResult();
    if (!result) return [];
    return [...new Set(result.findings.map(f => f.resourceType))].sort();
  });

  constructor(
    private scannerService: ScannerService,
    private snackBar: MatSnackBar,
    private claudeService: ClaudeService,
    private dialog: MatDialog
  ) {}

  ngAfterViewInit(): void {
    this.pageHeading?.nativeElement?.focus();
  }

  getCountBySeverity(severity: string): number {
    return this.scanResult()?.findings.filter(f => f.severity === severity).length ?? 0;
  }

  ngOnDestroy(): void {
    this.cancelScan();
  }

  onRemediationComplete(event: RemediationCompleteEvent): void {
    this.remediationStatuses.update(map => {
      const updated = new Map(map);
      updated.set(event.findingId, event.status);
      return updated;
    });
  }

  toggleShowRemediated(): void {
    this.showRemediated.update(v => !v);
  }

  runScan(): void {
    this.isScanning.set(true);
    this.scanResult.set(null);
    this.scanElapsedSeconds.set(0);
    this.remediationStatuses.set(new Map());
    this.clearFilters();

    this.timerInterval = setInterval(() => {
      this.scanElapsedSeconds.update(s => s + 1);
    }, 1000);

    this.scanSubscription = this.scannerService.triggerScan().subscribe({
      next: (result) => {
        this.scanResult.set(result);
        this.hasScanned.set(true);
        this.isScanning.set(false);
        this.stopTimer();
        this.snackBar.open(
          `Scan completed: ${result.totalFindings} findings detected`,
          'Dismiss',
          { duration: 3000 }
        );
      },
      error: (error) => {
        this.isScanning.set(false);
        this.stopTimer();
        this.snackBar.open(
          'Error running scan. Please ensure the backend is running.',
          'Dismiss',
          { duration: 5000 }
        );
        console.error('Scan error:', error);
      }
    });
  }

  cancelScan(): void {
    if (this.scanSubscription) {
      this.scanSubscription.unsubscribe();
      this.scanSubscription = null;
    }
    this.isScanning.set(false);
    this.stopTimer();
  }

  toggleSeverityFilter(severity: string): void {
    this.severityFilter.update(current => {
      if (current.includes(severity)) {
        return current.filter(s => s !== severity);
      }
      return [...current, severity];
    });
  }

  clearFilters(): void {
    this.severityFilter.set([]);
    this.resourceTypeFilter.set('');
    this.searchQuery.set('');
    this.sortBy.set('severity');
    this.showRemediated.set(true);
  }

  formatElapsed(seconds: number): string {
    const mins = Math.floor(seconds / 60);
    const secs = seconds % 60;
    return `${mins}:${secs.toString().padStart(2, '0')}`;
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

  toggleExpanded(findingId: string): void {
    this.expandedFindingId.update(current => current === findingId ? null : findingId);
  }

  getSeverityIcon(severity: string): string {
    switch (severity) {
      case 'HIGH': return 'error';
      case 'MEDIUM': return 'warning';
      case 'LOW': return 'info';
      default: return 'help';
    }
  }

  getResourceIcon(resourceType: string): string {
    switch (resourceType) {
      case 'S3': return 'folder';
      case 'IAM': return 'admin_panel_settings';
      case 'EC2': return 'dns';
      case 'EBS': return 'storage';
      default: return 'cloud';
    }
  }

  isRemediated(finding: Finding): boolean {
    return finding.remediationStatus === 'REMEDIATED';
  }

  hasRemediationStatus(finding: Finding): boolean {
    return !!finding.remediationStatus && finding.remediationStatus !== 'OPEN';
  }

  getRemediation(finding: Finding): void {
    this.loadingRemediation.update(set => {
      const updated = new Set(set);
      updated.add(finding.id);
      return updated;
    });

    this.claudeService.getRemediation({
      findingId: finding.id,
      resourceType: finding.resourceType,
      resourceId: finding.resourceId,
      title: finding.title,
      description: finding.description
    }).subscribe({
      next: (response) => {
        this.loadingRemediation.update(set => {
          const updated = new Set(set);
          updated.delete(finding.id);
          return updated;
        });
        this.dialog.open(RemediationDialogComponent, {
          width: '800px',
          maxWidth: '95vw',
          maxHeight: '90vh',
          data: {
            finding: finding,
            remediation: response.remediation
          }
        });
      },
      error: (error) => {
        this.loadingRemediation.update(set => {
          const updated = new Set(set);
          updated.delete(finding.id);
          return updated;
        });
        this.snackBar.open(
          'Error getting remediation. Please try again.',
          'Dismiss',
          { duration: 5000 }
        );
        console.error('Remediation error:', error);
      }
    });
  }

  autoRemediate(finding: Finding): void {
    const sessionId = crypto.randomUUID();

    const dialogRef = this.dialog.open(AutoRemediationDialogComponent, {
      width: '900px',
      maxWidth: '95vw',
      maxHeight: '90vh',
      data: {
        finding: finding,
        sessionId: sessionId
      }
    });

    dialogRef.afterClosed().subscribe((response: AutoRemediationResponse | null) => {
      if (!response) return;

      let status: RemediationCompleteEvent['status'];
      switch (response.status) {
        case 'COMPLETED':
          status = 'REMEDIATED';
          break;
        case 'FAILED':
          status = 'FAILED';
          break;
        default:
          status = 'PARTIAL';
          break;
      }

      this.onRemediationComplete({
        findingId: finding.id,
        status
      });
    });
  }

  private stopTimer(): void {
    if (this.timerInterval) {
      clearInterval(this.timerInterval);
      this.timerInterval = null;
    }
  }

  private downloadFile(blob: Blob, filename: string): void {
    const url = window.URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = filename;
    a.click();
    window.URL.revokeObjectURL(url);
  }
}
