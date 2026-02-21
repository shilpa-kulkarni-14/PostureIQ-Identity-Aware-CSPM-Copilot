export interface Finding {
  id: string;
  resourceType: 'S3' | 'IAM' | 'EC2' | 'EBS';
  resourceId: string;
  severity: 'HIGH' | 'MEDIUM' | 'LOW';
  title: string;
  description: string;
  remediation?: string;
}

export interface ScanResult {
  scanId: string;
  timestamp: string;
  status: 'RUNNING' | 'COMPLETED' | 'FAILED';
  findings: Finding[];
  totalFindings: number;
  highSeverity: number;
  mediumSeverity: number;
  lowSeverity: number;
}

export interface RemediationRequest {
  findingId: string;
  resourceType: string;
  resourceId: string;
  title: string;
  description: string;
}

export interface RemediationResponse {
  findingId: string;
  remediation: string;
}
