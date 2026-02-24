import { Component, Input, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatChipsModule } from '@angular/material/chips';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { Finding } from '../../models/finding.model';
import { ClaudeService } from '../../services/claude.service';
import { RemediationService } from '../../services/remediation.service';
import { RemediationDialogComponent } from '../remediation-dialog/remediation-dialog.component';
import { AutoRemediationDialogComponent } from '../auto-remediation-dialog/auto-remediation-dialog.component';

@Component({
  selector: 'app-finding-card',
  standalone: true,
  imports: [
    CommonModule,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatChipsModule,
    MatProgressSpinnerModule,
    MatSnackBarModule,
    MatDialogModule
  ],
  templateUrl: './finding-card.component.html',
  styleUrl: './finding-card.component.scss'
})
export class FindingCardComponent {
  @Input({ required: true }) finding!: Finding;

  isLoadingRemediation = signal(false);
  isLoadingAutoRemediation = signal(false);

  constructor(
    private claudeService: ClaudeService,
    private remediationService: RemediationService,
    private snackBar: MatSnackBar,
    private dialog: MatDialog
  ) {}

  getResourceIcon(): string {
    switch (this.finding.resourceType) {
      case 'S3':
        return 'folder';
      case 'IAM':
        return 'admin_panel_settings';
      case 'EC2':
        return 'dns';
      case 'EBS':
        return 'storage';
      default:
        return 'cloud';
    }
  }

  getSeverityIcon(): string {
    switch (this.finding.severity) {
      case 'HIGH':
        return 'error';
      case 'MEDIUM':
        return 'warning';
      case 'LOW':
        return 'info';
      default:
        return 'help';
    }
  }

  getSeverityClass(): string {
    return `severity-${this.finding.severity.toLowerCase()}`;
  }

  getRemediation(): void {
    this.isLoadingRemediation.set(true);

    this.claudeService.getRemediation({
      findingId: this.finding.id,
      resourceType: this.finding.resourceType,
      resourceId: this.finding.resourceId,
      title: this.finding.title,
      description: this.finding.description
    }).subscribe({
      next: (response) => {
        this.isLoadingRemediation.set(false);
        this.dialog.open(RemediationDialogComponent, {
          width: '800px',
          maxWidth: '95vw',
          maxHeight: '90vh',
          data: {
            finding: this.finding,
            remediation: response.remediation
          }
        });
      },
      error: (error) => {
        this.isLoadingRemediation.set(false);
        this.snackBar.open(
          'Error getting remediation. Please try again.',
          'Dismiss',
          { duration: 5000 }
        );
        console.error('Remediation error:', error);
      }
    });
  }

  autoRemediate(): void {
    this.isLoadingAutoRemediation.set(true);

    this.remediationService.triggerAutoRemediation({
      findingId: this.finding.id
    }).subscribe({
      next: (response) => {
        this.isLoadingAutoRemediation.set(false);
        this.dialog.open(AutoRemediationDialogComponent, {
          width: '900px',
          maxWidth: '95vw',
          maxHeight: '90vh',
          data: {
            finding: this.finding,
            response: response
          }
        });
      },
      error: (error) => {
        this.isLoadingAutoRemediation.set(false);
        this.snackBar.open(
          'Error triggering auto-remediation. Please try again.',
          'Dismiss',
          { duration: 5000 }
        );
        console.error('Auto-remediation error:', error);
      }
    });
  }
}
