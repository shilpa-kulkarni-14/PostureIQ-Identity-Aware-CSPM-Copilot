package com.cspm.repository;

import com.cspm.model.RegulatoryFindingMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RegulatoryFindingMappingRepository extends JpaRepository<RegulatoryFindingMapping, Long> {

    List<RegulatoryFindingMapping> findByAiFindingDetailsId(Long aiFindingDetailsId);

    @Query("SELECT r.framework, COUNT(r) FROM RegulatoryFindingMapping r " +
           "WHERE r.aiFindingDetails.id IN :detailIds GROUP BY r.framework")
    List<Object[]> countByFrameworkForDetails(@Param("detailIds") List<Long> detailIds);
}
