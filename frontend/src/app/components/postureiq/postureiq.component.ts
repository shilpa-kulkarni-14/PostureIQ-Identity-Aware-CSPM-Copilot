import { Component, signal, computed, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatChipsModule } from '@angular/material/chips';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatStepperModule } from '@angular/material/stepper';
import { MatTableModule } from '@angular/material/table';
import { RouterLink } from '@angular/router';
import { Subscription } from 'rxjs';
import { PostureIqService } from '../../services/postureiq.service';
import { DashboardService } from '../../services/dashboard.service';
import { ScanResult, Finding, AiFindingDetails, HighRiskIdentity, ComplianceSummaryResponse } from '../../models/finding.model';

@Component({
  selector: 'app-postureiq',
  standalone: true,
  imports: [
    CommonModule,
    MatButtonModule,
    MatCardModule,
    MatIconModule,
    MatChipsModule,
    MatProgressSpinnerModule,
    MatProgressBarModule,
    MatSnackBarModule,
    MatStepperModule,
    MatTableModule,
    RouterLink
  ],
  templateUrl: './postureiq.component.html',
  styleUrl: './postureiq.component.scss'
})
export class PostureIqComponent implements OnInit, OnDestroy {
  isScanning = signal(false);
  iamScanResult = signal<ScanResult | null>(null);
  correlationResult = signal<ScanResult | null>(null);
  enrichmentResults = signal<AiFindingDetails[]>([]);
  highRiskIdentities = signal<HighRiskIdentity[]>([]);
  complianceSummary = signal<ComplianceSummaryResponse | null>(null);
  activeStep = signal(1);
  hasCspmScans = signal(false);

  identityColumns = ['name', 'type', 'riskScore', 'findingCount'];
  expandedFindingId = signal<string | null>(null);

  scanningLabel = computed(() => {
    const step = this.activeStep();
    switch (step) {
      case 1: return 'Scanning IAM users, roles, and policies...';
      case 2: return 'Correlating IAM findings with infrastructure misconfigurations...';
      case 3: return 'Generating AI-powered attack path narratives...';
      default: return 'Processing...';
    }
  });

  private subscriptions: Subscription[] = [];

  correlatedFindings = computed(() => {
    const result = this.correlationResult();
    if (!result) return [];
    return result.findings;
  });

  enrichmentMap = computed(() => {
    const enrichments = this.enrichmentResults();
    const map = new Map<string, AiFindingDetails>();
    for (const e of enrichments) {
      map.set(e.findingId, e);
    }
    return map;
  });

  constructor(
    private postureIqService: PostureIqService,
    private dashboardService: DashboardService,
    private snackBar: MatSnackBar
  ) {}

  ngOnInit(): void {
    this.loadHighRiskIdentities();
    this.checkCspmScans();
  }

  ngOnDestroy(): void {
    this.subscriptions.forEach(s => s.unsubscribe());
  }

  runIamScan(): void {
    this.isScanning.set(true);
    const sub = this.postureIqService.runIamScan().subscribe({
      next: (result) => {
        this.iamScanResult.set(result);
        this.isScanning.set(false);
        this.activeStep.set(2);
        this.snackBar.open(
          `IAM scan completed: ${result.totalFindings} findings`,
          'Dismiss',
          { duration: 3000 }
        );
      },
      error: (err) => {
        this.isScanning.set(false);
        this.snackBar.open(
          'Error running IAM scan. Ensure the backend is running.',
          'Dismiss',
          { duration: 5000 }
        );
        console.error('IAM scan error:', err);
      }
    });
    this.subscriptions.push(sub);
  }

  correlate(): void {
    this.isScanning.set(true);
    const sub = this.postureIqService.correlate().subscribe({
      next: (result) => {
        this.correlationResult.set(result);
        this.isScanning.set(false);
        this.activeStep.set(3);
        this.snackBar.open(
          `Correlation completed: ${result.totalFindings} correlated findings`,
          'Dismiss',
          { duration: 3000 }
        );
      },
      error: (err) => {
        this.isScanning.set(false);
        this.snackBar.open(
          'Error correlating findings. Please try again.',
          'Dismiss',
          { duration: 5000 }
        );
        console.error('Correlation error:', err);
      }
    });
    this.subscriptions.push(sub);
  }

  enrichFindings(): void {
    const scanId = this.correlationResult()?.scanId;
    if (!scanId) {
      this.snackBar.open(
        'Please run correlation first before enriching findings.',
        'Dismiss',
        { duration: 5000 }
      );
      return;
    }

    this.isScanning.set(true);
    const sub = this.postureIqService.enrichScan(scanId).subscribe({
      next: (results) => {
        this.enrichmentResults.set(results);
        this.isScanning.set(false);
        this.activeStep.set(4);
        this.loadHighRiskIdentities();
        this.loadComplianceSummary(scanId!);
        this.snackBar.open(
          `AI enrichment completed: ${results.length} findings enriched`,
          'Dismiss',
          { duration: 3000 }
        );
      },
      error: (err) => {
        this.isScanning.set(false);
        this.snackBar.open(
          'Error enriching findings. Please try again.',
          'Dismiss',
          { duration: 5000 }
        );
        console.error('Enrichment error:', err);
      }
    });
    this.subscriptions.push(sub);
  }

  checkCspmScans(): void {
    const sub = this.dashboardService.getStats().subscribe({
      next: (stats) => this.hasCspmScans.set(stats.totalScans > 0),
      error: () => this.hasCspmScans.set(false)
    });
    this.subscriptions.push(sub);
  }

  loadHighRiskIdentities(): void {
    const sub = this.postureIqService.getHighRiskIdentities().subscribe({
      next: (identities) => this.highRiskIdentities.set(identities),
      error: (err) => console.error('Error loading high-risk identities:', err)
    });
    this.subscriptions.push(sub);
  }

  toggleExpanded(findingId: string): void {
    this.expandedFindingId.update(current =>
      current === findingId ? null : findingId
    );
  }

  getSeverityIcon(severity: string): string {
    switch (severity) {
      case 'CRITICAL': return 'dangerous';
      case 'HIGH': return 'error';
      case 'MEDIUM': return 'warning';
      case 'LOW': return 'info';
      default: return 'help';
    }
  }

  getCategoryLabel(category?: string): string {
    switch (category) {
      case 'CONFIG': return 'Config';
      case 'IAM': return 'IAM';
      case 'CORRELATED': return 'Correlated';
      default: return 'Unknown';
    }
  }

  loadComplianceSummary(scanId: string): void {
    const sub = this.postureIqService.getComplianceSummary(scanId).subscribe({
      next: (summary) => this.complianceSummary.set(summary),
      error: (err) => console.error('Error loading compliance summary:', err)
    });
    this.subscriptions.push(sub);
  }

  getFrameworkDisplayName(framework: string): string {
    const names: Record<string, string> = {
      'PCI_DSS': 'PCI-DSS',
      'HIPAA': 'HIPAA',
      'FFIEC': 'FFIEC',
      'NYDFS_500': 'NYDFS 500',
      'SOX': 'SOX',
      'CIS_AWS': 'CIS AWS'
    };
    return names[framework] || framework;
  }

  getRiskLevelClass(riskLevel: string): string {
    switch (riskLevel) {
      case 'CRITICAL': return 'risk-critical';
      case 'HIGH': return 'risk-high';
      case 'MEDIUM': return 'risk-medium';
      case 'LOW': return 'risk-low';
      default: return '';
    }
  }
}
