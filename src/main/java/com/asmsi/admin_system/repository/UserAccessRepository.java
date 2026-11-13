package com.asmsi.admin_system.repository;

import com.asmsi.admin_system.entity.UserAccess;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAccessRepository extends JpaRepository<UserAccess, Long> {
}
