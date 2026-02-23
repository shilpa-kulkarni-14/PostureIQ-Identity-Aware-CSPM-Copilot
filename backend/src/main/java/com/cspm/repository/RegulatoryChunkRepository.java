package com.cspm.repository;

import com.cspm.model.RegulatoryChunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RegulatoryChunkRepository extends JpaRepository<RegulatoryChunk, Long> {
    List<RegulatoryChunk> findByFramework(String framework);
}
