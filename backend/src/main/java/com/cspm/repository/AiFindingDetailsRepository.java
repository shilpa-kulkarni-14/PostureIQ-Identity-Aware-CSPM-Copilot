package com.cspm.repository;

import com.cspm.model.AiFindingDetails;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AiFindingDetailsRepository extends JpaRepository<AiFindingDetails, Long> {
    Optional<AiFindingDetails> findByFindingId(String findingId);
}
