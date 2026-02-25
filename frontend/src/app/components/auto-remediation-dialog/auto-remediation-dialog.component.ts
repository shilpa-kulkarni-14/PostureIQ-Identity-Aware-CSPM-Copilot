import { Component, Inject, OnInit, OnDestroy, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatChipsModule } from '@angular/material/chips';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { Finding, ApprovalRequest, AutoRemediationResponse, RemediationAction, RemediationProgressEvent } from '../../models/finding.model';
import { RemediationService } from '../../services/remediation.service';
import { FormatMarkdownPipe } from '../../pipes/format-markdown.pipe';
import { Subscription } from 'rxjs';

export interface AutoRemediationDialogData {
  finding: Finding;
  sessionId: string;
  response?: AutoRemediationResponse;
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
    MatProgressSpinnerModule,
    FormatMarkdownPipe
  ],
  templateUrl: './auto-remediation-dialog.component.html',
  styleUrl: './auto-remediation-dialog.component.scss'
})
export class AutoRemediationDialogComponent implements OnInit, OnDestroy {
  progressEvents = signal<RemediationProgressEvent[]>([]);
  finalResponse = signal<AutoRemediationResponse | null>(null);
  isStreaming = signal(true);
  errorMessage = signal<string | null>(null);
  pendingApproval = signal(false);
  approvalRequests = signal<ApprovalRequest[]>([]);
  isApproving = signal(false);

  currentStep = computed(() => {
    const events = this.progressEvents();
    if (events.length === 0) return null;
    return events[events.length - 1];
  });

  isCompleted = computed(() => {
    const last = this.currentStep();
    return last?.type === 'COMPLETED' || last?.type === 'ERROR';
  });

  private sseSubscription: Subscription | null = null;
  private postSubscription: Subscription | null = null;

  constructor(
    public dialogRef: MatDialogRef<AutoRemediationDialogComponent>,
    @Inject(MAT_DIALOG_DATA) public data: AutoRemediationDialogData,
    private remediationService: RemediationService,
    private snackBar: MatSnackBar
  ) {}

  ngOnInit(): void {
    // If a pre-computed response was passed (legacy path), use it directly
    if (this.data.response) {
      this.finalResponse.set(this.data.response);
      this.isStreaming.set(false);
      return;
    }

    const sessionId = this.data.sessionId;

    // 1. Connect to SSE stream first
    this.sseSubscription = this.remediationService.streamProgress(sessionId).subscribe({
      next: (event) => {
        this.progressEvents.update(events => [...events, event]);
      },
      error: () => {
        this.isStreaming.set(false);
      },
      complete: () => {
        this.isStreaming.set(false);
      }
    });

    // 2. Then trigger the POST with requireApproval=true to get plan first
    this.postSubscription = this.remediationService.triggerAutoRemediation({
      findingId: this.data.finding.id,
      sessionId: sessionId,
      requireApproval: true
    }).subscribe({
      next: (response) => {
        if (response.pendingApproval && response.approvalRequests?.length) {
          // Show review mode instead of executing immediately
          this.pendingApproval.set(true);
          this.approvalRequests.set(response.approvalRequests);
          this.finalResponse.set(response);
          this.isStreaming.set(false);
          // Close the SSE stream since we're not executing yet
          this.sseSubscription?.unsubscribe();
        } else {
          this.finalResponse.set(response);
        }
      },
      error: (error) => {
        this.isStreaming.set(false);
        this.errorMessage.set('Remediation request failed: ' + (error?.error?.message || error?.message || 'Unknown error'));
        console.error('Auto-remediation error:', error);
      }
    });
  }

  ngOnDestroy(): void {
    this.sseSubscription?.unsubscribe();
    this.postSubscription?.unsubscribe();
  }

  approveAndExecute(): void {
    this.isApproving.set(true);
    this.pendingApproval.set(false);
    this.isStreaming.set(true);
    this.progressEvents.set([]);

    const sessionId = this.data.sessionId;
    const requests = this.approvalRequests();
    if (requests.length === 0) return;

    // Re-connect SSE stream for the execution phase
    this.sseSubscription = this.remediationService.streamProgress(sessionId).subscribe({
      next: (event) => {
        this.progressEvents.update(events => [...events, event]);
      },
      error: () => {
        this.isStreaming.set(false);
        this.isApproving.set(false);
      },
      complete: () => {
        this.isStreaming.set(false);
        this.isApproving.set(false);
      }
    });

    // Send approval for the first request (which triggers all tool executions in the session)
    const approval: ApprovalRequest = { ...requests[0], approved: true };
    this.postSubscription = this.remediationService.approveRemediation(approval).subscribe({
      next: (response) => {
        this.finalResponse.set(response);
        this.isApproving.set(false);
      },
      error: (error) => {
        this.isStreaming.set(false);
        this.isApproving.set(false);
        this.errorMessage.set('Approved remediation failed: ' + (error?.error?.message || error?.message || 'Unknown error'));
        console.error('Approval execution error:', error);
      }
    });
  }

  cancelApproval(): void {
    this.pendingApproval.set(false);
    this.approvalRequests.set([]);
    this.dialogRef.close();
  }

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

  getEventIcon(type: string): string {
    switch (type) {
      case 'STARTED': return 'play_circle';
      case 'TOOL_EXECUTING': return 'settings';
      case 'TOOL_COMPLETED': return 'check_circle';
      case 'THINKING': return 'psychology';
      case 'COMPLETED': return 'verified';
      case 'ERROR': return 'error';
      default: return 'circle';
    }
  }

  getEventClass(type: string): string {
    switch (type) {
      case 'STARTED': return 'event-started';
      case 'TOOL_EXECUTING': return 'event-executing';
      case 'TOOL_COMPLETED': return 'event-completed';
      case 'THINKING': return 'event-thinking';
      case 'COMPLETED': return 'event-done';
      case 'ERROR': return 'event-error';
      default: return 'event-default';
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
    const sid = this.finalResponse()?.sessionId || this.data.sessionId;
    navigator.clipboard.writeText(sid).then(() => {
      this.snackBar.open('Session ID copied!', 'Dismiss', { duration: 2000 });
    });
  }
}
