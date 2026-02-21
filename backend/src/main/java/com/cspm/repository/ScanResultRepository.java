package com.cspm.repository;

import com.cspm.model.ScanResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ScanResultRepository extends JpaRepository<ScanResult, String> {
    List<ScanResult> findAllByOrderByTimestampDesc();

    @Query("SELECT s FROM ScanResult s JOIN FETCH s.findings WHERE s.scanId = :scanId")
    Optional<ScanResult> findByIdWithFindings(@Param("scanId") String scanId);
}
