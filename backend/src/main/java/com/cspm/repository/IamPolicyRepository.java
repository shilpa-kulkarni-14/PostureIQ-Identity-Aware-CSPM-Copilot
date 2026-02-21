package com.cspm.repository;

import com.cspm.model.IamPolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface IamPolicyRepository extends JpaRepository<IamPolicy, String> {
}
