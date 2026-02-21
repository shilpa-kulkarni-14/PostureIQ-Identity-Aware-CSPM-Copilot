package com.cspm.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "iam_identities")
public class IamIdentity {

    @Id
    private String id;

    @Enumerated(EnumType.STRING)
    private IdentityType identityType;

    private String name;
    private String arn;
    private boolean hasConsoleAccess;
    private boolean mfaEnabled;
    private Instant lastUsed;

    @Column(length = 4000)
    private String tags;

    private Instant createdAt;

    @ManyToMany(cascade = {CascadeType.PERSIST, CascadeType.MERGE}, fetch = FetchType.LAZY)
    @JoinTable(
            name = "iam_identity_policies",
            joinColumns = @JoinColumn(name = "identity_id"),
            inverseJoinColumns = @JoinColumn(name = "policy_id")
    )
    @Builder.Default
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<IamPolicy> policies = new ArrayList<>();

    public enum IdentityType {
        USER, ROLE, GROUP
    }
}
