package com.cspm.repository;

import com.cspm.model.RemediationAudit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface RemediationAuditRepository extends JpaRepository<RemediationAudit, Long> {
    List<RemediationAudit> findByFindingIdOrderByExecutedAtDesc(String findingId);
    List<RemediationAudit> findByClaudeSessionIdOrderByExecutedAtAsc(String claudeSessionId);
}
