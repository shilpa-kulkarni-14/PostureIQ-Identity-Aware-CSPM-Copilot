package com.cspm.service;

import com.cspm.model.IamIdentity;
import com.cspm.model.IamPolicy;
import com.cspm.repository.IamIdentityRepository;
import com.cspm.repository.IamPolicyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "aws.scanner.enabled", havingValue = "false", matchIfMissing = true)
public class MockIamIngestionService implements IamIngestionService {

    private final IamIdentityRepository iamIdentityRepository;
    private final IamPolicyRepository iamPolicyRepository;

    @Override
    @Transactional
    public List<IamIdentity> ingestIdentities() {
        log.info("Running mock IAM identity ingestion (no AWS credentials configured)");

        iamIdentityRepository.deleteAll();
        iamPolicyRepository.deleteAll();

        List<IamIdentity> identities = generateMockIdentities();
        List<IamIdentity> saved = iamIdentityRepository.saveAll(identities);

        log.info("Mock IAM ingestion completed: {} identities created", saved.size());
        return saved;
    }

    private List<IamIdentity> generateMockIdentities() {
        List<IamIdentity> identities = new ArrayList<>();

        // Policy: AdministratorAccess (admin-like)
        IamPolicy adminPolicy = IamPolicy.builder()
                .id(UUID.randomUUID().toString())
                .policyName("AdministratorAccess")
                .arn("arn:aws:iam::aws:policy/AdministratorAccess")
                .policyDocument("{\"Version\":\"2012-10-17\",\"Statement\":[{\"Effect\":\"Allow\",\"Action\":\"*\",\"Resource\":\"*\"}]}")
                .isAdminLike(true)
                .hasWildcardActions(true)
                .build();

        // Policy: PowerUserAccess
        IamPolicy powerUserPolicy = IamPolicy.builder()
                .id(UUID.randomUUID().toString())
                .policyName("PowerUserAccess")
                .arn("arn:aws:iam::aws:policy/PowerUserAccess")
                .policyDocument("{\"Version\":\"2012-10-17\",\"Statement\":[{\"Effect\":\"Allow\",\"NotAction\":\"iam:*\",\"Resource\":\"*\"}]}")
                .isAdminLike(false)
                .hasWildcardActions(false)
                .build();

        // Policy: AmazonS3FullAccess
        IamPolicy s3FullAccess = IamPolicy.builder()
                .id(UUID.randomUUID().toString())
                .policyName("AmazonS3FullAccess")
                .arn("arn:aws:iam::aws:policy/AmazonS3FullAccess")
                .policyDocument("{\"Version\":\"2012-10-17\",\"Statement\":[{\"Effect\":\"Allow\",\"Action\":\"s3:*\",\"Resource\":\"*\"}]}")
                .isAdminLike(false)
                .hasWildcardActions(false)
                .build();

        // Policy: AmazonEC2FullAccess
        IamPolicy ec2FullAccess = IamPolicy.builder()
                .id(UUID.randomUUID().toString())
                .policyName("AmazonEC2FullAccess")
                .arn("arn:aws:iam::aws:policy/AmazonEC2FullAccess")
                .policyDocument("{\"Version\":\"2012-10-17\",\"Statement\":[{\"Effect\":\"Allow\",\"Action\":\"ec2:*\",\"Resource\":\"*\"}]}")
                .isAdminLike(false)
                .hasWildcardActions(false)
                .build();

        // Policy: ReadOnlyAccess
        IamPolicy readOnlyPolicy = IamPolicy.builder()
                .id(UUID.randomUUID().toString())
                .policyName("ReadOnlyAccess")
                .arn("arn:aws:iam::aws:policy/ReadOnlyAccess")
                .policyDocument("{\"Version\":\"2012-10-17\",\"Statement\":[{\"Effect\":\"Allow\",\"Action\":[\"s3:Get*\",\"s3:List*\",\"ec2:Describe*\"],\"Resource\":\"*\"}]}")
                .isAdminLike(false)
                .hasWildcardActions(false)
                .build();

        // 1. admin-user: AdministratorAccess, console, MFA enabled
        identities.add(IamIdentity.builder()
                .id(UUID.randomUUID().toString())
                .identityType(IamIdentity.IdentityType.USER)
                .name("admin-user")
                .arn("arn:aws:iam::123456789012:user/admin-user")
                .hasConsoleAccess(true)
                .mfaEnabled(true)
                .lastUsed(Instant.now().minus(1, ChronoUnit.DAYS))
                .createdAt(Instant.now().minus(365, ChronoUnit.DAYS))
                .policies(new ArrayList<>(List.of(adminPolicy)))
                .build());

        // 2. dev-user: PowerUserAccess, console, NO MFA
        identities.add(IamIdentity.builder()
                .id(UUID.randomUUID().toString())
                .identityType(IamIdentity.IdentityType.USER)
                .name("dev-user")
                .arn("arn:aws:iam::123456789012:user/dev-user")
                .hasConsoleAccess(true)
                .mfaEnabled(false)
                .lastUsed(Instant.now().minus(2, ChronoUnit.DAYS))
                .createdAt(Instant.now().minus(200, ChronoUnit.DAYS))
                .policies(new ArrayList<>(List.of(powerUserPolicy)))
                .build());

        // 3. ci-role: S3FullAccess + EC2FullAccess, no console, service role
        identities.add(IamIdentity.builder()
                .id(UUID.randomUUID().toString())
                .identityType(IamIdentity.IdentityType.ROLE)
                .name("ci-role")
                .arn("arn:aws:iam::123456789012:role/ci-role")
                .hasConsoleAccess(false)
                .mfaEnabled(false)
                .lastUsed(Instant.now().minus(1, ChronoUnit.HOURS))
                .createdAt(Instant.now().minus(150, ChronoUnit.DAYS))
                .policies(new ArrayList<>(List.of(s3FullAccess, ec2FullAccess)))
                .build());

        // 4. dormant-admin: AdministratorAccess, last used 180 days ago
        identities.add(IamIdentity.builder()
                .id(UUID.randomUUID().toString())
                .identityType(IamIdentity.IdentityType.USER)
                .name("dormant-admin")
                .arn("arn:aws:iam::123456789012:user/dormant-admin")
                .hasConsoleAccess(true)
                .mfaEnabled(false)
                .lastUsed(Instant.now().minus(180, ChronoUnit.DAYS))
                .createdAt(Instant.now().minus(400, ChronoUnit.DAYS))
                .policies(new ArrayList<>(List.of(adminPolicy)))
                .build());

        // 5. readonly-user: ReadOnlyAccess, safe user
        identities.add(IamIdentity.builder()
                .id(UUID.randomUUID().toString())
                .identityType(IamIdentity.IdentityType.USER)
                .name("readonly-user")
                .arn("arn:aws:iam::123456789012:user/readonly-user")
                .hasConsoleAccess(true)
                .mfaEnabled(true)
                .lastUsed(Instant.now().minus(5, ChronoUnit.DAYS))
                .createdAt(Instant.now().minus(100, ChronoUnit.DAYS))
                .policies(new ArrayList<>(List.of(readOnlyPolicy)))
                .build());

        return identities;
    }
}
