package com.cspm.repository;

import com.cspm.model.RemediationAudit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface RemediationAuditRepository extends JpaRepository<RemediationAudit, Long> {
    List<RemediationAudit> findByFindingIdOrderByExecutedAtDesc(String findingId);
    List<RemediationAudit> findByClaudeSessionIdOrderByExecutedAtAsc(String claudeSessionId);

    long countByStatus(String status);
    long count();
    List<RemediationAudit> findByExecutedAtAfter(LocalDateTime after);

    @Query("SELECT r.toolName, COUNT(r) FROM RemediationAudit r GROUP BY r.toolName ORDER BY COUNT(r) DESC")
    List<Object[]> countByToolName();

    @Query("SELECT r.resourceType, COUNT(r) FROM RemediationAudit r WHERE r.status = 'SUCCESS' GROUP BY r.resourceType")
    List<Object[]> countSuccessByResourceType();
}
