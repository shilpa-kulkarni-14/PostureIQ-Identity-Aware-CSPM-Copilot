package com.cspm.repository;

import com.cspm.model.IamIdentity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface IamIdentityRepository extends JpaRepository<IamIdentity, String> {
}
