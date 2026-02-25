export interface Finding {
  id: string;
  resourceType: 'S3' | 'IAM' | 'EC2' | 'EBS';
  resourceId: string;
  severity: 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW';
  title: string;
  description: string;
  remediation?: string;
  category?: 'CONFIG' | 'IAM' | 'CORRELATED';
  primaryIdentityArn?: string;
  remediationStatus?: 'OPEN' | 'REMEDIATED' | 'FAILED' | 'PARTIAL';
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

export interface RegulatoryFindingMapping {
  id: number;
  framework: string;
  controlId: string;
  controlTitle: string;
  violationSummary: string;
  remediationGuidance: string;
  relevanceScore: number;
}

export interface AiFindingDetails {
  id: number;
  findingId: string;
  finalSeverity: string;
  attackPathNarrative: string;
  businessImpact: string;
  remediationSteps: string;
  regulatoryAnalysis?: string;
  regulatoryMappings?: RegulatoryFindingMapping[];
}

export interface FrameworkSummary {
  framework: string;
  violationCount: number;
  criticalControls: string[];
  overallRiskLevel: string;
}

export interface ComplianceSummaryResponse {
  scanId: string;
  frameworkSummaries: FrameworkSummary[];
  totalViolations: number;
  frameworksCovered: string[];
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

export interface AutoRemediationRequest {
  findingId: string;
  scanId?: string;
  sessionId?: string;
  dryRun?: boolean;
  requireApproval?: boolean;
}

export interface ApprovalRequest {
  findingId: string;
  sessionId: string;
  toolName: string;
  toolInput: string;
  resourceType: string;
  resourceId: string;
  description: string;
  approved: boolean;
}

export interface RemediationProgressEvent {
  type: 'STARTED' | 'TOOL_EXECUTING' | 'TOOL_COMPLETED' | 'THINKING' | 'COMPLETED' | 'ERROR';
  findingId: string;
  sessionId: string;
  toolName?: string;
  message?: string;
  status?: string;
  beforeState?: string;
  afterState?: string;
  stepNumber: number;
  totalSteps: number;
  elapsedMs: number;
  demoMode: boolean;
}

export interface RemediationAction {
  toolName: string;
  input: string;
  output: string;
  status: 'SUCCESS' | 'FAILED' | 'MOCK';
  beforeState: string;
  afterState: string;
  durationMs: number;
}

export interface AutoRemediationResponse {
  findingId: string;
  sessionId: string;
  status: 'COMPLETED' | 'FAILED' | 'PARTIAL' | 'PENDING_APPROVAL';
  actions: RemediationAction[];
  summary: string;
  totalDurationMs: number;
  demoMode: boolean;
  pendingApproval?: boolean;
  approvalRequests?: ApprovalRequest[];
}

export interface RemediationAudit {
  id: number;
  findingId: string;
  scanId: string;
  toolName: string;
  toolInput: string;
  toolOutput: string;
  status: string;
  resourceType: string;
  resourceId: string;
  initiatedBy: string;
  beforeState: string;
  afterState: string;
  claudeSessionId: string;
  executedAt: string;
  isMock: boolean;
}
