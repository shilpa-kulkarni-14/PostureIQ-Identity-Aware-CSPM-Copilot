import { Component, Inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { Finding } from '../../models/finding.model';
import { FormatMarkdownPipe } from '../../pipes/format-markdown.pipe';

export interface RemediationDialogData {
  finding: Finding;
  remediation: string;
}

@Component({
  selector: 'app-remediation-dialog',
  standalone: true,
  imports: [
    CommonModule,
    MatDialogModule,
    MatButtonModule,
    MatIconModule,
    MatSnackBarModule,
    FormatMarkdownPipe
  ],
  templateUrl: './remediation-dialog.component.html',
  styleUrl: './remediation-dialog.component.scss'
})
export class RemediationDialogComponent {
  constructor(
    public dialogRef: MatDialogRef<RemediationDialogComponent>,
    @Inject(MAT_DIALOG_DATA) public data: RemediationDialogData,
    private snackBar: MatSnackBar
  ) {}

  copyToClipboard(): void {
    navigator.clipboard.writeText(this.data.remediation).then(() => {
      this.snackBar.open('Copied to clipboard!', 'Dismiss', {
        duration: 2000
      });
    }).catch(() => {
      this.snackBar.open('Failed to copy to clipboard', 'Dismiss', {
        duration: 3000
      });
    });
  }

  close(): void {
    this.dialogRef.close();
  }

  getSeverityIcon(): string {
    switch (this.data.finding.severity) {
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
}
