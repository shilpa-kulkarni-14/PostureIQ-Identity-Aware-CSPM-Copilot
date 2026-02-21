export interface Finding {
  id: string;
  resourceType: 'S3' | 'IAM' | 'EC2' | 'EBS';
  resourceId: string;
  severity: 'HIGH' | 'MEDIUM' | 'LOW';
  title: string;
  description: string;
  remediation?: string;
  category?: 'CONFIG' | 'IAM' | 'CORRELATED';
  primaryIdentityArn?: string;
}

export interface IamIdentity {
  id: string;
  identityType: 'USER' | 'ROLE' | 'GROUP';
  name: string;
  arn: string;
  hasConsoleAccess: boolean;
  mfaEnabled: boolean;
  lastUsed: string;
  policies: IamPolicy[];
}

export interface IamPolicy {
  id: string;
  policyName: string;
  arn: string;
  isAdminLike: boolean;
  hasWildcardActions: boolean;
}

export interface AiFindingDetails {
  id: number;
  findingId: string;
  finalSeverity: string;
  attackPathNarrative: string;
  businessImpact: string;
  remediationSteps: string;
}

export interface HighRiskIdentity {
  identityArn: string;
  identityName: string;
  identityType: string;
  riskScore: number;
  findingCount: number;
  highSeverityCount: number;
  findings: Finding[];
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
