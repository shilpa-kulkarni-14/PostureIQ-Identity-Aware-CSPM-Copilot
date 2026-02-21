package com.cspm.service;

import com.cspm.model.IamIdentity;
import com.cspm.model.IamPolicy;
import com.cspm.repository.IamIdentityRepository;
import com.cspm.repository.IamPolicyRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.iam.IamClient;
import software.amazon.awssdk.services.iam.model.*;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "aws.scanner.enabled", havingValue = "true")
public class AwsIamIngestionService implements IamIngestionService {

    private final IamClient iamClient;
    private final IamIdentityRepository iamIdentityRepository;
    private final IamPolicyRepository iamPolicyRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    @Transactional
    public List<IamIdentity> ingestIdentities() {
        log.info("Starting IAM identity ingestion from AWS");

        iamIdentityRepository.deleteAll();
        iamPolicyRepository.deleteAll();

        List<IamIdentity> allIdentities = new ArrayList<>();

        allIdentities.addAll(ingestUsers());
        allIdentities.addAll(ingestRoles());
        allIdentities.addAll(ingestGroups());

        List<IamIdentity> saved = iamIdentityRepository.saveAll(allIdentities);
        log.info("IAM ingestion completed: {} identities ingested", saved.size());
        return saved;
    }

    private List<IamIdentity> ingestUsers() {
        List<IamIdentity> identities = new ArrayList<>();
        try {
            ListUsersResponse usersResponse = iamClient.listUsers();
            for (User user : usersResponse.users()) {
                boolean consoleAccess = hasConsoleAccess(user.userName());
                boolean mfa = hasMfaEnabled(user.userName());
                Instant lastUsed = user.passwordLastUsed();

                List<IamPolicy> policies = getAttachedUserPolicies(user.userName());

                IamIdentity identity = IamIdentity.builder()
                        .id(UUID.randomUUID().toString())
                        .identityType(IamIdentity.IdentityType.USER)
                        .name(user.userName())
                        .arn(user.arn())
                        .hasConsoleAccess(consoleAccess)
                        .mfaEnabled(mfa)
                        .lastUsed(lastUsed)
                        .createdAt(user.createDate())
                        .policies(policies)
                        .build();

                identities.add(identity);
            }
        } catch (IamException e) {
            log.error("Error ingesting IAM users: {}", e.awsErrorDetails().errorMessage());
        }
        return identities;
    }

    private List<IamIdentity> ingestRoles() {
        List<IamIdentity> identities = new ArrayList<>();
        try {
            ListRolesResponse rolesResponse = iamClient.listRoles();
            for (Role role : rolesResponse.roles()) {
                Instant lastUsed = role.roleLastUsed() != null ? role.roleLastUsed().lastUsedDate() : null;

                List<IamPolicy> policies = getAttachedRolePolicies(role.roleName());

                IamIdentity identity = IamIdentity.builder()
                        .id(UUID.randomUUID().toString())
                        .identityType(IamIdentity.IdentityType.ROLE)
                        .name(role.roleName())
                        .arn(role.arn())
                        .hasConsoleAccess(false)
                        .mfaEnabled(false)
                        .lastUsed(lastUsed)
                        .createdAt(role.createDate())
                        .policies(policies)
                        .build();

                identities.add(identity);
            }
        } catch (IamException e) {
            log.error("Error ingesting IAM roles: {}", e.awsErrorDetails().errorMessage());
        }
        return identities;
    }

    private List<IamIdentity> ingestGroups() {
        List<IamIdentity> identities = new ArrayList<>();
        try {
            ListGroupsResponse groupsResponse = iamClient.listGroups();
            for (Group group : groupsResponse.groups()) {
                List<IamPolicy> policies = getAttachedGroupPolicies(group.groupName());

                IamIdentity identity = IamIdentity.builder()
                        .id(UUID.randomUUID().toString())
                        .identityType(IamIdentity.IdentityType.GROUP)
                        .name(group.groupName())
                        .arn(group.arn())
                        .hasConsoleAccess(false)
                        .mfaEnabled(false)
                        .createdAt(group.createDate())
                        .policies(policies)
                        .build();

                identities.add(identity);
            }
        } catch (IamException e) {
            log.error("Error ingesting IAM groups: {}", e.awsErrorDetails().errorMessage());
        }
        return identities;
    }

    private boolean hasConsoleAccess(String userName) {
        try {
            iamClient.getLoginProfile(r -> r.userName(userName));
            return true;
        } catch (NoSuchEntityException e) {
            return false;
        } catch (IamException e) {
            log.warn("Error checking console access for {}: {}", userName, e.awsErrorDetails().errorMessage());
            return false;
        }
    }

    private boolean hasMfaEnabled(String userName) {
        try {
            ListMfaDevicesResponse mfaResponse = iamClient.listMFADevices(r -> r.userName(userName));
            return !mfaResponse.mfaDevices().isEmpty();
        } catch (IamException e) {
            log.warn("Error checking MFA for {}: {}", userName, e.awsErrorDetails().errorMessage());
            return false;
        }
    }

    private List<IamPolicy> getAttachedUserPolicies(String userName) {
        try {
            ListAttachedUserPoliciesResponse response = iamClient.listAttachedUserPolicies(r -> r.userName(userName));
            return response.attachedPolicies().stream()
                    .map(this::fetchPolicy)
                    .filter(Objects::nonNull)
                    .toList();
        } catch (IamException e) {
            log.warn("Error fetching policies for user {}: {}", userName, e.awsErrorDetails().errorMessage());
            return List.of();
        }
    }

    private List<IamPolicy> getAttachedRolePolicies(String roleName) {
        try {
            ListAttachedRolePoliciesResponse response = iamClient.listAttachedRolePolicies(r -> r.roleName(roleName));
            return response.attachedPolicies().stream()
                    .map(this::fetchPolicy)
                    .filter(Objects::nonNull)
                    .toList();
        } catch (IamException e) {
            log.warn("Error fetching policies for role {}: {}", roleName, e.awsErrorDetails().errorMessage());
            return List.of();
        }
    }

    private List<IamPolicy> getAttachedGroupPolicies(String groupName) {
        try {
            ListAttachedGroupPoliciesResponse response = iamClient.listAttachedGroupPolicies(r -> r.groupName(groupName));
            return response.attachedPolicies().stream()
                    .map(this::fetchPolicy)
                    .filter(Objects::nonNull)
                    .toList();
        } catch (IamException e) {
            log.warn("Error fetching policies for group {}: {}", groupName, e.awsErrorDetails().errorMessage());
            return List.of();
        }
    }

    private IamPolicy fetchPolicy(AttachedPolicy attachedPolicy) {
        try {
            GetPolicyResponse policyResponse = iamClient.getPolicy(r -> r.policyArn(attachedPolicy.policyArn()));
            Policy policy = policyResponse.policy();

            GetPolicyVersionResponse versionResponse = iamClient.getPolicyVersion(r -> r
                    .policyArn(policy.arn())
                    .versionId(policy.defaultVersionId()));

            String document = URLDecoder.decode(
                    versionResponse.policyVersion().document(), StandardCharsets.UTF_8);

            boolean adminLike = isAdminLike(document);
            boolean wildcardActions = hasWildcardActions(document);

            return IamPolicy.builder()
                    .id(UUID.randomUUID().toString())
                    .policyName(policy.policyName())
                    .arn(policy.arn())
                    .policyDocument(document)
                    .isAdminLike(adminLike)
                    .hasWildcardActions(wildcardActions)
                    .build();
        } catch (IamException e) {
            log.warn("Error fetching policy {}: {}", attachedPolicy.policyArn(), e.awsErrorDetails().errorMessage());
            return null;
        }
    }

    private boolean isAdminLike(String policyDocument) {
        try {
            JsonNode root = objectMapper.readTree(policyDocument);
            JsonNode statements = root.get("Statement");
            if (statements == null || !statements.isArray()) return false;

            for (JsonNode statement : statements) {
                if (fieldContains(statement, "Action", "*")
                        && fieldContains(statement, "Resource", "*")) {
                    return true;
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse policy document: {}", e.getMessage());
        }
        return false;
    }

    private boolean hasWildcardActions(String policyDocument) {
        try {
            JsonNode root = objectMapper.readTree(policyDocument);
            JsonNode statements = root.get("Statement");
            if (statements == null || !statements.isArray()) return false;

            for (JsonNode statement : statements) {
                if (fieldContains(statement, "Action", "*")) {
                    return true;
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse policy document: {}", e.getMessage());
        }
        return false;
    }

    private boolean fieldContains(JsonNode node, String fieldName, String value) {
        JsonNode field = node.get(fieldName);
        if (field == null) return false;
        if (field.isTextual()) return value.equals(field.asText());
        if (field.isArray()) {
            for (JsonNode element : field) {
                if (element.isTextual() && value.equals(element.asText())) return true;
            }
        }
        return false;
    }
}
