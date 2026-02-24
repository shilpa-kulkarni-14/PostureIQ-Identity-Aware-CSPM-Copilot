package com.cspm.mcp;

import com.cspm.mcp.RemediationToolService.ToolResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Registry that exposes {@link RemediationToolService} methods as Claude
 * tool-use compatible JSON Schema definitions, and dispatches tool
 * invocations to the active service implementation.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class McpToolRegistry {

    private final RemediationToolService remediationToolService;

    /**
     * Returns a list of Claude tool definitions (name, description, input_schema).
     */
    public List<Map<String, Object>> getToolDefinitions() {
        List<Map<String, Object>> tools = new ArrayList<>();

        // ── Write tools ────────────────────────────────────────────

        tools.add(toolDef(
                "block_s3_public_access",
                "Block all public access on an S3 bucket by enabling BlockPublicAcls, IgnorePublicAcls, BlockPublicPolicy, and RestrictPublicBuckets.",
                schemaObject(
                        Map.of("bucketName", schemaString("The name of the S3 bucket to secure")),
                        List.of("bucketName")
                )
        ));

        tools.add(toolDef(
                "restrict_security_group",
                "Remove the 0.0.0.0/0 ingress rule from a security group for a specific port and protocol.",
                schemaObject(
                        Map.of(
                                "groupId", schemaString("The security group ID (e.g. sg-0abc1234)"),
                                "port", schemaInteger("The port number to restrict (e.g. 22, 3389)"),
                                "protocol", schemaStringWithDefault("The IP protocol (tcp, udp, icmp)", "tcp")
                        ),
                        List.of("groupId", "port")
                )
        ));

        tools.add(toolDef(
                "enable_ebs_encryption",
                "Create an encrypted copy of an unencrypted EBS volume by taking a snapshot and copying it with encryption enabled.",
                schemaObject(
                        Map.of("volumeId", schemaString("The EBS volume ID (e.g. vol-0abc1234)")),
                        List.of("volumeId")
                )
        ));

        tools.add(toolDef(
                "enable_cloudtrail",
                "Start logging on a CloudTrail trail that is currently not recording API activity.",
                schemaObject(
                        Map.of("trailName", schemaString("The name or ARN of the CloudTrail trail")),
                        List.of("trailName")
                )
        ));

        tools.add(toolDef(
                "rotate_access_key",
                "Deactivate an existing IAM access key and create a new one for the same user.",
                schemaObject(
                        Map.of(
                                "username", schemaString("The IAM username"),
                                "accessKeyId", schemaString("The access key ID to deactivate")
                        ),
                        List.of("username", "accessKeyId")
                )
        ));

        tools.add(toolDef(
                "delete_unused_credentials",
                "Delete an unused IAM access key that has not been used within the retention period.",
                schemaObject(
                        Map.of(
                                "username", schemaString("The IAM username"),
                                "accessKeyId", schemaString("The access key ID to delete")
                        ),
                        List.of("username", "accessKeyId")
                )
        ));

        // ── Read-only tools ────────────────────────────────────────

        tools.add(toolDef(
                "get_scan_findings",
                "Retrieve all security findings from a specific scan, including severity breakdown and remediation guidance.",
                schemaObject(
                        Map.of("scanId", schemaString("The scan ID to retrieve findings for")),
                        List.of("scanId")
                )
        ));

        tools.add(toolDef(
                "get_finding_details",
                "Get the full details of a specific security finding including description, remediation steps, and affected resource.",
                schemaObject(
                        Map.of("findingId", schemaString("The finding ID to retrieve details for")),
                        List.of("findingId")
                )
        ));

        tools.add(toolDef(
                "get_high_risk_identities",
                "List IAM identities that are high-risk due to missing MFA, excessive permissions, or stale credentials.",
                schemaObject(Map.of(), List.of())
        ));

        return Collections.unmodifiableList(tools);
    }

    /**
     * Dispatches a tool invocation to the correct {@link RemediationToolService} method.
     *
     * @param toolName the tool name (snake_case)
     * @param input    the input parameter map
     * @return the {@link ToolResult} from the service
     */
    public ToolResult dispatch(String toolName, Map<String, Object> input) {
        log.info("Dispatching tool: {} with input: {}", toolName, input);

        return switch (toolName) {
            case "block_s3_public_access" ->
                    remediationToolService.blockS3PublicAccess(
                            requireString(input, "bucketName"));

            case "restrict_security_group" ->
                    remediationToolService.restrictSecurityGroup(
                            requireString(input, "groupId"),
                            requireInt(input, "port"),
                            optionalString(input, "protocol", "tcp"));

            case "enable_ebs_encryption" ->
                    remediationToolService.enableEbsEncryption(
                            requireString(input, "volumeId"));

            case "enable_cloudtrail" ->
                    remediationToolService.enableCloudTrail(
                            requireString(input, "trailName"));

            case "rotate_access_key" ->
                    remediationToolService.rotateAccessKey(
                            requireString(input, "username"),
                            requireString(input, "accessKeyId"));

            case "delete_unused_credentials" ->
                    remediationToolService.deleteUnusedCredentials(
                            requireString(input, "username"),
                            requireString(input, "accessKeyId"));

            case "get_scan_findings" ->
                    remediationToolService.getScanFindings(
                            requireString(input, "scanId"));

            case "get_finding_details" ->
                    remediationToolService.getFindingDetails(
                            requireString(input, "findingId"));

            case "get_high_risk_identities" ->
                    remediationToolService.getHighRiskIdentities();

            default -> ToolResult.failure("Unknown tool: " + toolName);
        };
    }

    // ── JSON Schema helpers ────────────────────────────────────────────

    private Map<String, Object> toolDef(String name, String description,
                                        Map<String, Object> inputSchema) {
        Map<String, Object> def = new LinkedHashMap<>();
        def.put("name", name);
        def.put("description", description);
        def.put("input_schema", inputSchema);
        return def;
    }

    private Map<String, Object> schemaObject(Map<String, Map<String, Object>> properties,
                                             List<String> required) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        if (!required.isEmpty()) {
            schema.put("required", required);
        }
        return schema;
    }

    private Map<String, Object> schemaString(String description) {
        Map<String, Object> prop = new LinkedHashMap<>();
        prop.put("type", "string");
        prop.put("description", description);
        return prop;
    }

    private Map<String, Object> schemaStringWithDefault(String description, String defaultValue) {
        Map<String, Object> prop = new LinkedHashMap<>();
        prop.put("type", "string");
        prop.put("description", description);
        prop.put("default", defaultValue);
        return prop;
    }

    private Map<String, Object> schemaInteger(String description) {
        Map<String, Object> prop = new LinkedHashMap<>();
        prop.put("type", "integer");
        prop.put("description", description);
        return prop;
    }

    // ── Input extraction helpers ───────────────────────────────────────

    private String requireString(Map<String, Object> input, String key) {
        Object value = input.get(key);
        if (value == null) {
            throw new IllegalArgumentException("Missing required parameter: " + key);
        }
        return value.toString();
    }

    private int requireInt(Map<String, Object> input, String key) {
        Object value = input.get(key);
        if (value == null) {
            throw new IllegalArgumentException("Missing required parameter: " + key);
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.parseInt(value.toString());
    }

    private String optionalString(Map<String, Object> input, String key, String defaultValue) {
        Object value = input.get(key);
        return value != null ? value.toString() : defaultValue;
    }
}
