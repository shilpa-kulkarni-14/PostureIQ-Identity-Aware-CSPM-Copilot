package com.cspm.repository;

import com.cspm.model.AiFindingDetails;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AiFindingDetailsRepository extends JpaRepository<AiFindingDetails, Long> {
    @Query("SELECT a FROM AiFindingDetails a WHERE a.finding.id = :findingId")
    Optional<AiFindingDetails> findByFindingId(@Param("findingId") String findingId);
}
