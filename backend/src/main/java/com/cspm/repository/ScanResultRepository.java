package com.cspm.repository;

import com.cspm.model.ScanResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ScanResultRepository extends JpaRepository<ScanResult, String> {
    List<ScanResult> findAllByOrderByTimestampDesc();
}
