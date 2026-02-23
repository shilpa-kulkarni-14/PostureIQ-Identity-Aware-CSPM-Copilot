package com.cspm.service;

import com.cspm.model.AiFindingDetails;
import com.cspm.model.Finding;
import com.cspm.model.IamIdentity;
import com.cspm.model.RegulatoryFindingMapping;
import com.cspm.model.RemediationRequest;
import com.cspm.service.RegulatoryRetrievalService.RegulatoryChunkResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Service
@Slf4j
public class ClaudeService {

    @Value("${anthropic.api-key:}")
    private String apiKey;

    @Autowired
    private RegulatoryRetrievalService regulatoryRetrievalService;

    private final RestTemplate restTemplate = new RestTemplate();
    private static final String CLAUDE_API_URL = "https://api.anthropic.com/v1/messages";

    public String getRemediation(RemediationRequest request) {
        if (apiKey == null || apiKey.isEmpty()) {
            return getMockRemediation(request);
        }

        try {
            return callClaudeApi(request);
        } catch (Exception e) {
            return getMockRemediation(request);
        }
    }

    private String callClaudeApi(RemediationRequest request) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-api-key", apiKey);
        headers.set("anthropic-version", "2023-06-01");

        String prompt = buildPrompt(request);

        Map<String, Object> body = new HashMap<>();
        body.put("model", "claude-sonnet-4-20250514");
        body.put("max_tokens", 2048);
        body.put("messages", List.of(
                Map.of("role", "user", "content", prompt)
        ));

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        ResponseEntity<Map> response = restTemplate.exchange(
                CLAUDE_API_URL,
                HttpMethod.POST,
                entity,
                Map.class
        );

        if (response.getBody() != null) {
            List<Map<String, Object>> content = (List<Map<String, Object>>) response.getBody().get("content");
            if (content != null && !content.isEmpty()) {
                return (String) content.get(0).get("text");
            }
        }
        return getMockRemediation(request);
    }

    private String buildPrompt(RemediationRequest request) {
        return String.format("""
                You are a cloud security expert. Generate remediation code for this AWS security finding:

                Resource Type: %s
                Resource ID: %s
                Issue: %s

                Provide:
                1. AWS CLI command to fix this
                2. Terraform snippet (if applicable)
                3. Brief explanation of the fix

                Format your response clearly with headers for each section.
                """,
                request.getResourceType(),
                request.getResourceId(),
                request.getDescription()
        );
    }

    private String getMockRemediation(RemediationRequest request) {
        return switch (request.getResourceType()) {
            case "S3" -> getS3Remediation(request);
            case "IAM" -> getIamRemediation(request);
            case "EC2" -> getEc2Remediation(request);
            case "EBS" -> getEbsRemediation(request);
            case "CloudTrail" -> getCloudTrailRemediation(request);
            case "VPC" -> getVpcRemediation(request);
            default -> getGenericRemediation(request);
        };
    }

    private String getS3Remediation(RemediationRequest request) {
        String bucketName = extractBucketName(request.getResourceId());
        if (request.getTitle().contains("Public Access")) {
            return String.format("""
                    ## AWS CLI Command

                    ```bash
                    # Block all public access to the bucket
                    aws s3api put-public-access-block \\
                        --bucket %s \\
                        --public-access-block-configuration \\
                        "BlockPublicAcls=true,IgnorePublicAcls=true,BlockPublicPolicy=true,RestrictPublicBuckets=true"
                    ```

                    ## Terraform

                    ```hcl
                    resource "aws_s3_bucket_public_access_block" "%s_public_access" {
                      bucket = aws_s3_bucket.%s.id

                      block_public_acls       = true
                      block_public_policy     = true
                      ignore_public_acls      = true
                      restrict_public_buckets = true
                    }
                    ```

                    ## Explanation

                    This fix blocks all public access to your S3 bucket by:
                    - **BlockPublicAcls**: Rejects any new public ACLs
                    - **IgnorePublicAcls**: Ignores existing public ACLs
                    - **BlockPublicPolicy**: Rejects public bucket policies
                    - **RestrictPublicBuckets**: Restricts access to bucket with public policies

                    After applying, verify with: `aws s3api get-public-access-block --bucket %s`
                    """, bucketName, bucketName.replace("-", "_"), bucketName.replace("-", "_"), bucketName);
        } else if (request.getTitle().contains("Encryption")) {
            return String.format("""
                    ## AWS CLI Command

                    ```bash
                    # Enable default encryption with AES-256
                    aws s3api put-bucket-encryption \\
                        --bucket %s \\
                        --server-side-encryption-configuration '{
                            "Rules": [{
                                "ApplyServerSideEncryptionByDefault": {
                                    "SSEAlgorithm": "AES256"
                                },
                                "BucketKeyEnabled": true
                            }]
                        }'
                    ```

                    ## Terraform

                    ```hcl
                    resource "aws_s3_bucket_server_side_encryption_configuration" "%s_encryption" {
                      bucket = aws_s3_bucket.%s.id

                      rule {
                        apply_server_side_encryption_by_default {
                          sse_algorithm = "AES256"
                        }
                        bucket_key_enabled = true
                      }
                    }
                    ```

                    ## Explanation

                    This enables server-side encryption for all new objects stored in the bucket:
                    - Uses AES-256 encryption (SSE-S3)
                    - Bucket Key reduces API calls to KMS (if using KMS encryption)
                    - Existing objects are NOT automatically encrypted

                    To encrypt existing objects, you'll need to copy them in place:
                    ```bash
                    aws s3 cp s3://%s s3://%s --recursive --sse AES256
                    ```
                    """, bucketName, bucketName.replace("-", "_"), bucketName.replace("-", "_"), bucketName, bucketName);
        }
        return getS3AclRemediation(request);
    }

    private String getS3AclRemediation(RemediationRequest request) {
        String bucketName = extractBucketName(request.getResourceId());
        return String.format("""
                ## AWS CLI Command

                ```bash
                # Remove public ACLs and set private ACL
                aws s3api put-bucket-acl --bucket %s --acl private

                # Also block future public access
                aws s3api put-public-access-block \\
                    --bucket %s \\
                    --public-access-block-configuration \\
                    "BlockPublicAcls=true,IgnorePublicAcls=true,BlockPublicPolicy=true,RestrictPublicBuckets=true"
                ```

                ## Terraform

                ```hcl
                resource "aws_s3_bucket_acl" "%s_acl" {
                  bucket = aws_s3_bucket.%s.id
                  acl    = "private"
                }

                resource "aws_s3_bucket_public_access_block" "%s_public_access" {
                  bucket = aws_s3_bucket.%s.id

                  block_public_acls       = true
                  block_public_policy     = true
                  ignore_public_acls      = true
                  restrict_public_buckets = true
                }
                ```

                ## Explanation

                This fix removes public ACL permissions:
                - Sets bucket ACL to private (only bucket owner has access)
                - Blocks any future public access attempts
                - Ensures logs remain confidential
                """, bucketName, bucketName, bucketName.replace("-", "_"), bucketName.replace("-", "_"),
                bucketName.replace("-", "_"), bucketName.replace("-", "_"));
    }

    private String getIamRemediation(RemediationRequest request) {
        if (request.getTitle().contains("Full Administrative Access") || request.getTitle().contains("Excessive")) {
            return """
                    ## AWS CLI Command

                    ```bash
                    # First, identify what the policy actually allows
                    aws iam get-policy-version \\
                        --policy-arn arn:aws:iam::123456789012:policy/DeveloperFullAccess \\
                        --version-id v1

                    # Create a new restricted policy version
                    aws iam create-policy-version \\
                        --policy-arn arn:aws:iam::123456789012:policy/DeveloperFullAccess \\
                        --policy-document file://restricted-policy.json \\
                        --set-as-default
                    ```

                    ## Restricted Policy Example (restricted-policy.json)

                    ```json
                    {
                        "Version": "2012-10-17",
                        "Statement": [
                            {
                                "Sid": "AllowEC2Management",
                                "Effect": "Allow",
                                "Action": [
                                    "ec2:Describe*",
                                    "ec2:StartInstances",
                                    "ec2:StopInstances"
                                ],
                                "Resource": "*",
                                "Condition": {
                                    "StringEquals": {
                                        "aws:RequestedRegion": "us-east-1"
                                    }
                                }
                            },
                            {
                                "Sid": "AllowS3Access",
                                "Effect": "Allow",
                                "Action": [
                                    "s3:GetObject",
                                    "s3:PutObject",
                                    "s3:ListBucket"
                                ],
                                "Resource": [
                                    "arn:aws:s3:::company-dev-*",
                                    "arn:aws:s3:::company-dev-*/*"
                                ]
                            }
                        ]
                    }
                    ```

                    ## Terraform

                    ```hcl
                    resource "aws_iam_policy" "developer_access" {
                      name        = "DeveloperAccess"
                      description = "Restricted developer access policy"

                      policy = jsonencode({
                        Version = "2012-10-17"
                        Statement = [
                          {
                            Sid      = "AllowEC2Management"
                            Effect   = "Allow"
                            Action   = ["ec2:Describe*", "ec2:StartInstances", "ec2:StopInstances"]
                            Resource = "*"
                            Condition = {
                              StringEquals = {
                                "aws:RequestedRegion" = "us-east-1"
                              }
                            }
                          }
                        ]
                      })
                    }
                    ```

                    ## Explanation

                    Follow the principle of least privilege:
                    1. **Audit current usage**: Use IAM Access Analyzer to see what permissions are actually used
                    2. **Scope to specific actions**: Replace `*` with explicit action lists
                    3. **Scope to specific resources**: Use resource ARNs instead of `*`
                    4. **Add conditions**: Restrict by region, IP, MFA, etc.
                    5. **Use managed policies**: Prefer AWS managed policies where possible
                    """;
        }
        return """
                ## AWS CLI Command

                ```bash
                # Add permission boundary to the role
                aws iam put-role-permissions-boundary \\
                    --role-name LambdaExecutionRole \\
                    --permissions-boundary arn:aws:iam::123456789012:policy/DeveloperBoundary
                ```

                ## Create Permission Boundary Policy

                ```bash
                aws iam create-policy --policy-name DeveloperBoundary --policy-document '{
                    "Version": "2012-10-17",
                    "Statement": [
                        {
                            "Effect": "Allow",
                            "Action": ["*"],
                            "Resource": "*"
                        },
                        {
                            "Effect": "Deny",
                            "Action": ["iam:*", "organizations:*", "account:*"],
                            "Resource": "*"
                        }
                    ]
                }'
                ```

                ## Terraform

                ```hcl
                resource "aws_iam_role" "lambda_execution" {
                  name                 = "LambdaExecutionRole"
                  permissions_boundary = aws_iam_policy.boundary.arn
                  assume_role_policy   = data.aws_iam_policy_document.lambda_assume.json
                }

                resource "aws_iam_policy" "boundary" {
                  name = "DeveloperBoundary"
                  policy = jsonencode({
                    Version = "2012-10-17"
                    Statement = [
                      {
                        Effect   = "Allow"
                        Action   = ["*"]
                        Resource = "*"
                      },
                      {
                        Effect   = "Deny"
                        Action   = ["iam:*", "organizations:*"]
                        Resource = "*"
                      }
                    ]
                  })
                }
                ```

                ## Explanation

                Permission boundaries set the maximum permissions a role can have:
                - Prevents privilege escalation even if role policy is misconfigured
                - Denies sensitive actions like IAM modifications
                - Should be applied to all roles created by developers
                """;
    }

    private String getEc2Remediation(RemediationRequest request) {
        String sgId = request.getResourceId();
        if (request.getTitle().contains("SSH")) {
            return String.format("""
                    ## AWS CLI Command

                    ```bash
                    # Remove the overly permissive SSH rule
                    aws ec2 revoke-security-group-ingress \\
                        --group-id %s \\
                        --protocol tcp \\
                        --port 22 \\
                        --cidr 0.0.0.0/0

                    # Add restricted SSH access (replace with your IP/CIDR)
                    aws ec2 authorize-security-group-ingress \\
                        --group-id %s \\
                        --protocol tcp \\
                        --port 22 \\
                        --cidr 10.0.0.0/8  # Internal network only
                    ```

                    ## Terraform

                    ```hcl
                    resource "aws_security_group" "ssh_restricted" {
                      name        = "ssh-restricted"
                      description = "Allow SSH from internal network only"
                      vpc_id      = var.vpc_id

                      ingress {
                        description = "SSH from internal network"
                        from_port   = 22
                        to_port     = 22
                        protocol    = "tcp"
                        cidr_blocks = ["10.0.0.0/8"]  # Internal only
                      }

                      egress {
                        from_port   = 0
                        to_port     = 0
                        protocol    = "-1"
                        cidr_blocks = ["0.0.0.0/0"]
                      }
                    }
                    ```

                    ## Better Alternative: Use AWS Systems Manager Session Manager

                    ```bash
                    # No need to open port 22 at all!
                    # Connect via SSM instead:
                    aws ssm start-session --target i-0abc123def456
                    ```

                    ## Explanation

                    - **Never expose SSH to 0.0.0.0/0** - this invites brute force attacks
                    - **Use bastion hosts** or VPN for external access
                    - **Best practice**: Use AWS Systems Manager Session Manager
                      - No open inbound ports needed
                      - Audit logging built-in
                      - IAM-based access control
                    """, sgId, sgId);
        } else if (request.getTitle().contains("RDP")) {
            return String.format("""
                    ## AWS CLI Command

                    ```bash
                    # Remove the overly permissive RDP rule
                    aws ec2 revoke-security-group-ingress \\
                        --group-id %s \\
                        --protocol tcp \\
                        --port 3389 \\
                        --cidr 0.0.0.0/0

                    # Add restricted RDP access
                    aws ec2 authorize-security-group-ingress \\
                        --group-id %s \\
                        --protocol tcp \\
                        --port 3389 \\
                        --cidr 10.0.0.0/8  # Internal network only
                    ```

                    ## Terraform

                    ```hcl
                    resource "aws_security_group" "rdp_restricted" {
                      name        = "rdp-restricted"
                      description = "Allow RDP from internal network only"
                      vpc_id      = var.vpc_id

                      ingress {
                        description = "RDP from internal network"
                        from_port   = 3389
                        to_port     = 3389
                        protocol    = "tcp"
                        cidr_blocks = ["10.0.0.0/8"]
                      }
                    }
                    ```

                    ## Better Alternative: Use AWS Systems Manager Fleet Manager

                    Fleet Manager provides browser-based RDP without opening port 3389.

                    ## Explanation

                    - RDP exposed to internet is a major attack vector
                    - Restrict to VPN or internal network CIDR
                    - Consider AWS Systems Manager for remote management
                    """, sgId, sgId);
        }
        return String.format("""
                ## AWS CLI Command

                ```bash
                # Restrict egress to specific destinations
                aws ec2 revoke-security-group-egress \\
                    --group-id %s \\
                    --protocol all \\
                    --cidr 0.0.0.0/0

                # Add specific egress rules
                aws ec2 authorize-security-group-egress \\
                    --group-id %s \\
                    --protocol tcp \\
                    --port 443 \\
                    --cidr 0.0.0.0/0  # HTTPS to anywhere

                aws ec2 authorize-security-group-egress \\
                    --group-id %s \\
                    --protocol tcp \\
                    --port 5432 \\
                    --cidr 10.0.0.0/8  # PostgreSQL to internal
                ```

                ## Terraform

                ```hcl
                resource "aws_security_group" "restricted_egress" {
                  name   = "restricted-egress"
                  vpc_id = var.vpc_id

                  egress {
                    description = "HTTPS to internet"
                    from_port   = 443
                    to_port     = 443
                    protocol    = "tcp"
                    cidr_blocks = ["0.0.0.0/0"]
                  }

                  egress {
                    description = "PostgreSQL to internal"
                    from_port   = 5432
                    to_port     = 5432
                    protocol    = "tcp"
                    cidr_blocks = ["10.0.0.0/8"]
                  }
                }
                ```

                ## Explanation

                Restricting egress traffic helps:
                - Prevent data exfiltration
                - Block command & control communications
                - Limit lateral movement in case of compromise
                """, sgId, sgId, sgId);
    }

    private String getEbsRemediation(RemediationRequest request) {
        if (request.getTitle().contains("Not Encrypted")) {
            String volumeId = request.getResourceId();
            return String.format("""
                    ## AWS CLI Command

                    ```bash
                    # EBS volumes cannot be encrypted in-place
                    # You must create an encrypted copy

                    # 1. Create a snapshot of the unencrypted volume
                    aws ec2 create-snapshot \\
                        --volume-id %s \\
                        --description "Snapshot for encryption migration"

                    # 2. Copy the snapshot with encryption enabled
                    aws ec2 copy-snapshot \\
                        --source-region us-east-1 \\
                        --source-snapshot-id snap-xxxx \\
                        --encrypted \\
                        --description "Encrypted copy"

                    # 3. Create new encrypted volume from the encrypted snapshot
                    aws ec2 create-volume \\
                        --snapshot-id snap-yyyy \\
                        --availability-zone us-east-1a \\
                        --encrypted

                    # 4. Stop instance, detach old volume, attach new volume
                    # 5. Delete old unencrypted volume and snapshots
                    ```

                    ## Enable Default Encryption for Future Volumes

                    ```bash
                    aws ec2 enable-ebs-encryption-by-default
                    ```

                    ## Terraform

                    ```hcl
                    # Enable default encryption
                    resource "aws_ebs_encryption_by_default" "enabled" {
                      enabled = true
                    }

                    # All new volumes will be encrypted
                    resource "aws_ebs_volume" "encrypted" {
                      availability_zone = "us-east-1a"
                      size              = 100
                      encrypted         = true
                      kms_key_id        = aws_kms_key.ebs.arn  # Optional: use custom key
                    }
                    ```

                    ## Explanation

                    - EBS encryption uses AES-256 and is handled by AWS
                    - Minimal performance impact (<1%%)
                    - Enable default encryption to prevent future unencrypted volumes
                    - Existing volumes require migration (snapshot → copy with encryption → new volume)
                    """, volumeId);
        }
        return """
                ## AWS CLI Command

                ```bash
                # Make the snapshot private
                aws ec2 modify-snapshot-attribute \\
                    --snapshot-id snap-0fedcba9876543210 \\
                    --attribute createVolumePermission \\
                    --operation-type remove \\
                    --group-names all
                ```

                ## Terraform

                ```hcl
                # EBS snapshots are private by default in Terraform
                resource "aws_ebs_snapshot" "private" {
                  volume_id = aws_ebs_volume.example.id

                  # No aws_snapshot_create_volume_permission means it stays private
                }
                ```

                ## Explanation

                - Public snapshots can be accessed by ANY AWS account
                - Attackers scan for public snapshots containing sensitive data
                - Always keep snapshots private unless explicitly needed
                - Use AWS RAM for controlled cross-account sharing
                """;
    }

    private String getCloudTrailRemediation(RemediationRequest request) {
        if (request.getTitle().contains("Disabled")) {
            return """
                    ## Enable CloudTrail Logging

                    ### AWS CLI

                    ```bash
                    # Start logging on the existing trail
                    aws cloudtrail start-logging --name management-trail

                    # Verify logging is active
                    aws cloudtrail get-trail-status --name management-trail
                    ```

                    ### Terraform

                    ```hcl
                    resource "aws_cloudtrail" "management" {
                      name                          = "management-trail"
                      s3_bucket_name                = aws_s3_bucket.cloudtrail_logs.id
                      is_multi_region_trail         = true
                      enable_logging                = true
                      enable_log_file_validation    = true
                      include_global_service_events = true

                      event_selector {
                        read_write_type           = "All"
                        include_management_events = true
                      }
                    }
                    ```

                    ### Why This Matters

                    - CloudTrail is your primary audit log for all AWS API activity
                    - Without it, you cannot detect unauthorized access, privilege escalation, or data exfiltration
                    - Many compliance frameworks (SOC 2, PCI-DSS, HIPAA) require API audit logging
                    - Enable log file validation to detect tampering
                    """;
        }
        return """
                ## Configure Multi-Region CloudTrail

                ### AWS CLI

                ```bash
                # Update trail to multi-region
                aws cloudtrail update-trail \\
                    --name management-trail \\
                    --is-multi-region-trail

                # Enable log file validation
                aws cloudtrail update-trail \\
                    --name management-trail \\
                    --enable-log-file-validation
                ```

                ### Why This Matters

                - Attackers may operate in regions you don't actively monitor
                - Multi-region trails capture activity across all AWS regions
                - This is a CIS AWS Benchmark Level 1 requirement
                """;
    }

    private String getVpcRemediation(RemediationRequest request) {
        return """
                ## Remediate Default VPC Usage

                ### Step 1: Audit Resources in Default VPC

                ```bash
                # List instances in the default VPC
                aws ec2 describe-instances \\
                    --filters "Name=vpc-id,Values=<default-vpc-id>" \\
                    --query "Reservations[].Instances[].{ID:InstanceId,State:State.Name}"

                # List subnets in the default VPC
                aws ec2 describe-subnets \\
                    --filters "Name=vpc-id,Values=<default-vpc-id>"
                ```

                ### Step 2: Create a Custom VPC

                ```hcl
                resource "aws_vpc" "main" {
                  cidr_block           = "10.0.0.0/16"
                  enable_dns_support   = true
                  enable_dns_hostnames = true

                  tags = {
                    Name = "production-vpc"
                  }
                }

                resource "aws_subnet" "private" {
                  vpc_id            = aws_vpc.main.id
                  cidr_block        = "10.0.1.0/24"
                  availability_zone = "us-east-1a"

                  tags = {
                    Name = "private-subnet"
                  }
                }
                ```

                ### Step 3: Migrate and Delete

                ```bash
                # After migrating all resources, delete the default VPC
                aws ec2 delete-vpc --vpc-id <default-vpc-id>
                ```

                ### Why This Matters

                - Default VPCs have public subnets with auto-assign public IP enabled
                - Default security groups and NACLs are overly permissive
                - Custom VPCs enforce network segmentation and least-privilege access
                - CIS AWS Benchmark recommends removing or hardening default VPCs
                """;
    }

    private String getGenericRemediation(RemediationRequest request) {
        return String.format("""
                ## Generic Remediation Steps

                For this %s finding, we recommend:

                1. Review the current configuration of the resource
                2. Apply the principle of least privilege
                3. Enable encryption and logging where applicable
                4. Regularly audit and rotate credentials

                ## AWS CLI

                ```bash
                # Get current resource configuration
                aws %s describe-* --query "..." --output json
                ```

                Please consult AWS documentation for specific remediation steps.
                """, request.getResourceType(), request.getResourceType().toLowerCase());
    }

    private String extractBucketName(String resourceId) {
        if (resourceId.startsWith("arn:aws:s3:::")) {
            return resourceId.substring("arn:aws:s3:::".length());
        }
        return resourceId;
    }

    public String generateAttackPathNarrative(Finding correlatedFinding, IamIdentity identity, List<Finding> relatedFindings) {
        return generateAttackPathNarrative(correlatedFinding, identity, relatedFindings, "");
    }

    public String generateAttackPathNarrative(Finding correlatedFinding, IamIdentity identity, List<Finding> relatedFindings, String regulatoryContext) {
        if (apiKey == null || apiKey.isEmpty()) {
            return getMockAttackPathNarrative(correlatedFinding, identity);
        }

        try {
            return callClaudeForAttackPath(correlatedFinding, identity, relatedFindings, regulatoryContext);
        } catch (Exception e) {
            return getMockAttackPathNarrative(correlatedFinding, identity);
        }
    }

    private String callClaudeForAttackPath(Finding correlatedFinding, IamIdentity identity, List<Finding> relatedFindings, String regulatoryContext) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-api-key", apiKey);
        headers.set("anthropic-version", "2023-06-01");

        StringBuilder relatedDesc = new StringBuilder();
        for (Finding f : relatedFindings) {
            relatedDesc.append(String.format("- [%s] %s: %s\n", f.getSeverity(), f.getTitle(), f.getDescription()));
        }

        String regulatorySection = regulatoryContext != null && !regulatoryContext.isEmpty()
                ? regulatoryContext + "\n\n" +
                  "REGULATORY_ANALYSIS:\n" +
                  "[Narrative analysis of which regulatory frameworks and specific controls are violated by this finding. Explain the compliance implications for financial services organizations.]\n\n" +
                  "REGULATORY_MAPPINGS:\n" +
                  "[For each violated control, provide one line in this exact format:\n" +
                  "FRAMEWORK|CONTROL_ID|CONTROL_TITLE|VIOLATION_SUMMARY|REMEDIATION_GUIDANCE|RELEVANCE_SCORE\n" +
                  "Where RELEVANCE_SCORE is 0.0 to 1.0. Only include controls with relevance >= 0.5.]\n"
                : "";

        String prompt = String.format("""
                You are a cloud security expert performing attack path analysis for financial services organizations. Analyze the following correlated security finding:

                **Correlated Finding:**
                - Title: %s
                - Severity: %s
                - Resource: %s (%s)
                - Description: %s

                **Identity Involved:**
                - Name: %s
                - Type: %s
                - ARN: %s
                - Console Access: %s
                - MFA Enabled: %s

                **Related Findings:**
                %s
                %s

                Provide your analysis in exactly this format:

                ATTACK_PATH:
                [Step-by-step attack path narrative describing how an attacker could exploit this combination of findings]

                BUSINESS_IMPACT:
                [Assessment of potential business impact including data exposure, compliance, and operational risks]

                REMEDIATION_STEPS:
                [Prioritized list of remediation actions to address the correlated risk]

                %s
                """,
                correlatedFinding.getTitle(),
                correlatedFinding.getSeverity(),
                correlatedFinding.getResourceId(),
                correlatedFinding.getResourceType(),
                correlatedFinding.getDescription(),
                identity.getName(),
                identity.getIdentityType(),
                identity.getArn(),
                identity.isHasConsoleAccess(),
                identity.isMfaEnabled(),
                relatedDesc.toString(),
                regulatoryContext != null && !regulatoryContext.isEmpty() ? regulatoryContext : "",
                regulatorySection);

        Map<String, Object> body = new HashMap<>();
        body.put("model", "claude-sonnet-4-20250514");
        body.put("max_tokens", 2048);
        body.put("messages", List.of(
                Map.of("role", "user", "content", prompt)
        ));

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        ResponseEntity<Map> response = restTemplate.exchange(
                CLAUDE_API_URL,
                HttpMethod.POST,
                entity,
                Map.class
        );

        if (response.getBody() != null) {
            List<Map<String, Object>> content = (List<Map<String, Object>>) response.getBody().get("content");
            if (content != null && !content.isEmpty()) {
                return (String) content.get(0).get("text");
            }
        }
        return getMockAttackPathNarrative(correlatedFinding, identity);
    }

    private String getMockAttackPathNarrative(Finding finding, IamIdentity identity) {
        String title = finding.getTitle() != null ? finding.getTitle() : "";

        if (title.contains("public S3") || title.contains("S3 bucket")) {
            return String.format("""
                    ATTACK_PATH:
                    1. Attacker discovers publicly accessible S3 bucket '%s' through automated scanning
                    2. Attacker enumerates bucket contents and identifies sensitive data
                    3. If credentials for identity '%s' are compromised (phishing, leaked keys), attacker gains S3:* permissions
                    4. Attacker uses the identity's broad S3 access to exfiltrate data from all buckets, not just the public one
                    5. Attacker may also modify or delete objects, causing data integrity issues

                    BUSINESS_IMPACT:
                    - Data breach exposure of potentially sensitive customer or business data
                    - Compliance violations (PCI-DSS, HIPAA, FFIEC) due to unauthorized data access
                    - Reputational damage and potential regulatory fines
                    - Operational disruption if data is modified or deleted

                    REMEDIATION_STEPS:
                    1. Immediately block public access on the S3 bucket using S3 Block Public Access
                    2. Reduce permissions for identity '%s' to only required S3 actions and specific bucket ARNs
                    3. Enable S3 access logging and CloudTrail data events for audit trail
                    4. Implement S3 bucket policies with explicit deny for public access
                    5. Review and rotate any access keys associated with the identity

                    REGULATORY_ANALYSIS:
                    This finding violates multiple regulatory frameworks critical for financial services. PCI-DSS Requirement 3 mandates protection of stored cardholder data, and a publicly accessible S3 bucket directly contradicts this requirement. HIPAA Security Rule 164.312(a)(1) requires access controls to protect electronic PHI, which cannot be assured with public bucket access. FFIEC guidance on information security requires institutions to implement controls commensurate with the sensitivity of data, and CIS AWS Benchmark 2.1.1 explicitly requires S3 Block Public Access. The combination of public exposure with a high-privilege identity creates an amplified compliance risk.

                    REGULATORY_MAPPINGS:
                    PCI_DSS|PCI-DSS 3.4.1|Protect stored account data|Public S3 bucket may expose cardholder data without encryption or access controls|Enable S3 Block Public Access and server-side encryption with KMS|0.95
                    HIPAA|HIPAA 164.312(a)(1)|Access Control|Public bucket access violates requirement to implement access controls for ePHI|Restrict bucket access to authorized IAM principals only|0.90
                    FFIEC|FFIEC-IS-3.2|Information Security Controls|Insufficient access controls on cloud storage containing financial data|Implement least-privilege access and enable S3 access logging|0.85
                    CIS_AWS|CIS-AWS-2.1.1|S3 Block Public Access|S3 bucket does not have Block Public Access enabled at account or bucket level|Enable S3 Block Public Access at the account level|0.98
                    NYDFS_500|NYDFS-500.15|Encryption of Nonpublic Information|Nonpublic information potentially exposed without encryption in public bucket|Enable SSE-KMS encryption and restrict all public access|0.82
                    """, finding.getResourceId(), identity.getName(), identity.getName());
        } else if (title.contains("EC2") || title.contains("security group")) {
            return String.format("""
                    ATTACK_PATH:
                    1. Attacker identifies exposed security group '%s' with open SSH/RDP ports via network scanning
                    2. Attacker attempts brute force or uses known exploits against exposed services
                    3. If identity '%s' credentials are compromised, attacker gains EC2 management access
                    4. Attacker can launch, modify, or terminate instances for cryptomining or lateral movement
                    5. Combined with open security group, attacker establishes persistent access

                    BUSINESS_IMPACT:
                    - Unauthorized access to compute resources leading to cryptomining costs
                    - Lateral movement risk to other connected services and databases
                    - Potential data exfiltration through compromised instances
                    - Service disruption if instances are terminated or modified

                    REMEDIATION_STEPS:
                    1. Restrict security group rules to specific IP ranges and required ports only
                    2. Scope EC2 permissions for identity '%s' to specific instance ARNs
                    3. Implement AWS Systems Manager Session Manager instead of direct SSH/RDP
                    4. Enable VPC Flow Logs and GuardDuty for network monitoring
                    5. Use EC2 Instance Connect or SSM for secure shell access

                    REGULATORY_ANALYSIS:
                    Open security groups with unrestricted inbound access (0.0.0.0/0) violate multiple regulatory requirements for network segmentation and access control. PCI-DSS Requirement 1 mandates firewall configurations that restrict connections to the cardholder data environment. FFIEC guidance requires financial institutions to implement network segmentation controls. The combination of open network access with a high-privilege EC2 identity creates a remote compromise vector that violates NYDFS 500.07 access privilege limitations.

                    REGULATORY_MAPPINGS:
                    PCI_DSS|PCI-DSS 1.3.1|Restrict inbound traffic to CDE|Security group allows unrestricted inbound access violating CDE isolation requirements|Restrict security group rules to specific IP ranges and necessary ports|0.95
                    FFIEC|FFIEC-IS-4.1|Network Security Controls|Open security group fails to implement required network segmentation for financial systems|Implement VPC segmentation with restricted security group rules|0.88
                    CIS_AWS|CIS-AWS-5.2.1|Restrict SSH access|Security group permits SSH access from 0.0.0.0/0|Remove SSH rules with 0.0.0.0/0 and restrict to specific CIDR blocks|0.96
                    NYDFS_500|NYDFS-500.07|Access Privileges|Overly broad network and compute access privileges for identity|Implement least-privilege access controls for EC2 resources|0.82
                    """, finding.getResourceId(), identity.getName(), identity.getName());
        } else {
            return String.format("""
                    ATTACK_PATH:
                    1. Attacker identifies misconfigured resources through reconnaissance
                    2. Admin identity '%s' provides broad access across all AWS services
                    3. If admin credentials are compromised, attacker has full control of the AWS environment
                    4. Attacker exploits existing misconfigurations to establish persistence
                    5. Multiple high-severity findings compound the blast radius of a credential compromise

                    BUSINESS_IMPACT:
                    - Full environment compromise risk due to admin-level access
                    - Existing misconfigurations amplify the impact of credential theft
                    - Potential for data exfiltration, resource abuse, and service disruption
                    - Compliance and audit failures due to excessive privileges

                    REMEDIATION_STEPS:
                    1. Replace admin-like policies with least-privilege policies based on actual usage (use IAM Access Analyzer)
                    2. Enable and enforce MFA for all console-capable identities
                    3. Address all high-severity configuration findings to reduce the attack surface
                    4. Implement SCPs (Service Control Policies) to set permission guardrails
                    5. Set up CloudTrail alerts for sensitive API calls made by admin identities

                    REGULATORY_ANALYSIS:
                    Admin-level IAM access without MFA and proper access controls violates fundamental principles across all major financial regulatory frameworks. PCI-DSS Requirement 7 mandates restricting access to cardholder data by business need-to-know, and Requirement 8 requires multi-factor authentication. NYDFS 500.12 explicitly requires MFA for accessing internal networks from external networks. FFIEC guidance emphasizes the principle of least privilege for all system access. The combination of admin privileges with infrastructure misconfigurations creates a critical compliance gap.

                    REGULATORY_MAPPINGS:
                    PCI_DSS|PCI-DSS 7.2.1|Restrict access by need-to-know|Admin-like IAM policy grants unrestricted access violating least-privilege requirements|Replace with role-specific policies using IAM Access Analyzer|0.95
                    PCI_DSS|PCI-DSS 8.3.1|MFA for administrative access|Admin identity lacks MFA enforcement for console and API access|Enable and enforce MFA for all administrative identities|0.92
                    NYDFS_500|NYDFS-500.12|Multi-Factor Authentication|Admin account without MFA violates NYDFS requirement for privileged access authentication|Implement MFA using hardware tokens or virtual MFA devices|0.94
                    FFIEC|FFIEC-AC-2.1|Least Privilege Access|Admin-level permissions exceed what is required for job function|Implement least-privilege access based on IAM Access Analyzer recommendations|0.88
                    SOX|SOX-ITGC-AC-1|Access to Programs and Data|Excessive privileges create segregation of duties violations for financial systems|Implement role-based access control with proper segregation of duties|0.80
                    """, identity.getName());
        }
    }

    public AiFindingDetails enrichFinding(Finding finding) {
        IamIdentity mockIdentity = IamIdentity.builder()
                .name(finding.getPrimaryIdentityArn() != null ?
                        finding.getPrimaryIdentityArn().substring(finding.getPrimaryIdentityArn().lastIndexOf('/') + 1) : "unknown")
                .arn(finding.getPrimaryIdentityArn() != null ? finding.getPrimaryIdentityArn() : "unknown")
                .identityType(IamIdentity.IdentityType.USER)
                .build();

        // RAG: Retrieve relevant regulatory context
        String regulatoryContext = "";
        List<RegulatoryChunkResult> regulatoryChunks = Collections.emptyList();
        try {
            regulatoryChunks = regulatoryRetrievalService.retrieveRelevantControls(finding);
            regulatoryContext = regulatoryRetrievalService.buildRegulatoryContextPrompt(regulatoryChunks);
            if (!regulatoryChunks.isEmpty()) {
                log.info("RAG: Retrieved {} regulatory chunks for finding '{}' (top: {} @ {})",
                        regulatoryChunks.size(), finding.getTitle(),
                        regulatoryChunks.get(0).controlId(),
                        String.format("%.3f", regulatoryChunks.get(0).similarity()));
            }
        } catch (Exception e) {
            log.warn("RAG retrieval failed for finding '{}': {}. Continuing without regulatory context.",
                    finding.getTitle(), e.getMessage());
        }

        String narrative = generateAttackPathNarrative(finding, mockIdentity, List.of(), regulatoryContext);
        return parseNarrative(narrative, finding);
    }

    private AiFindingDetails parseNarrative(String narrative, Finding finding) {
        String attackPath = "";
        String businessImpact = "";
        String remediationSteps = "";
        String regulatoryAnalysis = "";
        String regulatoryMappingsRaw = "";

        String[] sections = narrative.split("(?=ATTACK_PATH:|BUSINESS_IMPACT:|REMEDIATION_STEPS:|REGULATORY_ANALYSIS:|REGULATORY_MAPPINGS:)");
        for (String section : sections) {
            if (section.startsWith("ATTACK_PATH:")) {
                attackPath = section.substring("ATTACK_PATH:".length()).trim();
            } else if (section.startsWith("BUSINESS_IMPACT:")) {
                businessImpact = section.substring("BUSINESS_IMPACT:".length()).trim();
            } else if (section.startsWith("REMEDIATION_STEPS:")) {
                remediationSteps = section.substring("REMEDIATION_STEPS:".length()).trim();
            } else if (section.startsWith("REGULATORY_ANALYSIS:")) {
                regulatoryAnalysis = section.substring("REGULATORY_ANALYSIS:".length()).trim();
            } else if (section.startsWith("REGULATORY_MAPPINGS:")) {
                regulatoryMappingsRaw = section.substring("REGULATORY_MAPPINGS:".length()).trim();
            }
        }

        // Parse structured regulatory mappings
        List<RegulatoryFindingMapping> mappings = parseRegulatoryMappings(regulatoryMappingsRaw);

        AiFindingDetails details = AiFindingDetails.builder()
                .finding(finding)
                .finalSeverity(finding.getSeverity())
                .attackPathNarrative(attackPath)
                .businessImpact(businessImpact)
                .remediationSteps(remediationSteps)
                .regulatoryAnalysis(regulatoryAnalysis)
                .regulatoryMappings(mappings)
                .build();

        // Set the back-reference for each mapping
        for (RegulatoryFindingMapping mapping : mappings) {
            mapping.setAiFindingDetails(details);
        }

        return details;
    }

    private List<RegulatoryFindingMapping> parseRegulatoryMappings(String raw) {
        if (raw == null || raw.isBlank()) {
            return new ArrayList<>();
        }

        List<RegulatoryFindingMapping> mappings = new ArrayList<>();
        for (String line : raw.split("\n")) {
            line = line.trim();
            if (!line.contains("|")) continue;

            String[] parts = line.split("\\|", 6);
            if (parts.length < 5) continue;

            try {
                RegulatoryFindingMapping mapping = RegulatoryFindingMapping.builder()
                        .framework(parts[0].trim())
                        .controlId(parts[1].trim())
                        .controlTitle(parts[2].trim())
                        .violationSummary(parts[3].trim())
                        .remediationGuidance(parts[4].trim())
                        .relevanceScore(parts.length > 5 ? parseScore(parts[5].trim()) : 0.7)
                        .build();
                mappings.add(mapping);
            } catch (Exception e) {
                log.debug("Skipping malformed regulatory mapping line: {}", line);
            }
        }

        return mappings;
    }

    private double parseScore(String scoreStr) {
        try {
            return Double.parseDouble(scoreStr);
        } catch (NumberFormatException e) {
            return 0.7;
        }
    }
}
