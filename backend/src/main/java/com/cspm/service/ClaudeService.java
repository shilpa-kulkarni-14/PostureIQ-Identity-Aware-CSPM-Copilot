package com.cspm.service;

import com.cspm.model.RemediationRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Service
public class ClaudeService {

    @Value("${anthropic.api-key:}")
    private String apiKey;

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
}
