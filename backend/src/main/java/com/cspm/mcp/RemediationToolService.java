package com.cspm.mcp;

import java.util.Map;

/**
 * Interface for PostureIQ agentic remediation tools.
 * Provides both write (remediation) and read-only (query) tool methods
 * that can be invoked by the MCP tool registry.
 */
public interface RemediationToolService {

    record ToolResult(boolean success, String message, Map<String, Object> data) {
        public static ToolResult success(String message, Map<String, Object> data) {
            return new ToolResult(true, message, data);
        }

        public static ToolResult failure(String message) {
            return new ToolResult(false, message, Map.of());
        }
    }

    // ── Write tools ────────────────────────────────────────────────────

    /** Block all public access on an S3 bucket. */
    ToolResult blockS3PublicAccess(String bucketName);

    /** Remove 0.0.0.0/0 ingress rule from a security group for a given port. */
    ToolResult restrictSecurityGroup(String groupId, int port, String protocol);

    /** Create an encrypted copy of an EBS volume via snapshot. */
    ToolResult enableEbsEncryption(String volumeId);

    /** Start logging on a CloudTrail trail. */
    ToolResult enableCloudTrail(String trailName);

    /** Deactivate an old access key and create a new one. */
    ToolResult rotateAccessKey(String username, String accessKeyId);

    /** Delete an unused IAM access key. */
    ToolResult deleteUnusedCredentials(String username, String accessKeyId);

    // ── Read-only tools ────────────────────────────────────────────────

    /** Get all findings from a specific scan. */
    ToolResult getScanFindings(String scanId);

    /** Get details of a specific finding. */
    ToolResult getFindingDetails(String findingId);

    /** List high-risk IAM identities. */
    ToolResult getHighRiskIdentities();
}
