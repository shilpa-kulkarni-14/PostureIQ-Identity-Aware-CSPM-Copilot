import { Component, Inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatChipsModule } from '@angular/material/chips';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { Finding, AutoRemediationResponse, RemediationAction } from '../../models/finding.model';
import { FormatMarkdownPipe } from '../../pipes/format-markdown.pipe';

export interface AutoRemediationDialogData {
  finding: Finding;
  response: AutoRemediationResponse;
}

@Component({
  selector: 'app-auto-remediation-dialog',
  standalone: true,
  imports: [
    CommonModule,
    MatDialogModule,
    MatButtonModule,
    MatIconModule,
    MatSnackBarModule,
    MatChipsModule,
    MatExpansionModule,
    MatProgressBarModule,
    FormatMarkdownPipe
  ],
  templateUrl: './auto-remediation-dialog.component.html',
  styleUrl: './auto-remediation-dialog.component.scss'
})
export class AutoRemediationDialogComponent {
  constructor(
    public dialogRef: MatDialogRef<AutoRemediationDialogComponent>,
    @Inject(MAT_DIALOG_DATA) public data: AutoRemediationDialogData,
    private snackBar: MatSnackBar
  ) {}

  close(): void {
    this.dialogRef.close();
  }

  getSeverityIcon(): string {
    switch (this.data.finding.severity) {
      case 'CRITICAL': return 'dangerous';
      case 'HIGH': return 'error';
      case 'MEDIUM': return 'warning';
      case 'LOW': return 'info';
      default: return 'help';
    }
  }

  getResourceIcon(): string {
    switch (this.data.finding.resourceType) {
      case 'S3': return 'folder';
      case 'IAM': return 'admin_panel_settings';
      case 'EC2': return 'dns';
      case 'EBS': return 'storage';
      default: return 'cloud';
    }
  }

  getStatusIcon(status: string): string {
    switch (status) {
      case 'SUCCESS': case 'MOCK': return 'check_circle';
      case 'FAILED': return 'cancel';
      default: return 'pending';
    }
  }

  getStatusClass(status: string): string {
    switch (status) {
      case 'SUCCESS': case 'MOCK': return 'status-success';
      case 'FAILED': return 'status-failed';
      default: return 'status-pending';
    }
  }

  getToolDisplayName(toolName: string): string {
    return toolName.replace(/_/g, ' ').replace(/\b\w/g, c => c.toUpperCase());
  }

  formatJson(jsonString: string): string {
    try {
      return JSON.stringify(JSON.parse(jsonString), null, 2);
    } catch {
      return jsonString;
    }
  }

  copySessionId(): void {
    navigator.clipboard.writeText(this.data.response.sessionId).then(() => {
      this.snackBar.open('Session ID copied!', 'Dismiss', { duration: 2000 });
    });
  }
}
