package com.cspm.repository;

import com.cspm.model.Finding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FindingRepository extends JpaRepository<Finding, String> {

    List<Finding> findByPrimaryIdentityArn(String primaryIdentityArn);
}
