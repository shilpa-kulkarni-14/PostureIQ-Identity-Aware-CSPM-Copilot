package com.cspm.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "iam_policies")
public class IamPolicy {

    @Id
    private String id;

    private String policyName;
    private String arn;

    @Column(columnDefinition = "TEXT")
    private String policyDocument;

    private boolean isAdminLike;
    private boolean hasWildcardActions;

    @ManyToMany(mappedBy = "policies", fetch = FetchType.LAZY)
    @Builder.Default
    @JsonIgnore
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<IamIdentity> identities = new ArrayList<>();
}
