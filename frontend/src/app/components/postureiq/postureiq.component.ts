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
import { Subscription } from 'rxjs';
import { PostureIqService } from '../../services/postureiq.service';
import { ScanResult, Finding, AiFindingDetails, HighRiskIdentity } from '../../models/finding.model';

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
    MatTableModule
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
  activeStep = signal(1);

  identityColumns = ['name', 'type', 'riskScore', 'findingCount'];
  expandedFindingId = signal<string | null>(null);

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
    private snackBar: MatSnackBar
  ) {}

  ngOnInit(): void {
    this.loadHighRiskIdentities();
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
    const scanId = this.correlationResult()?.scanId || this.iamScanResult()?.scanId;
    if (!scanId) return;

    this.isScanning.set(true);
    const sub = this.postureIqService.enrichScan(scanId).subscribe({
      next: (results) => {
        this.enrichmentResults.set(results);
        this.isScanning.set(false);
        this.activeStep.set(4);
        this.loadHighRiskIdentities();
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
}
