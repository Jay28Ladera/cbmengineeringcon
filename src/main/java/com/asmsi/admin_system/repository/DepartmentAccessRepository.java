package com.asmsi.admin_system.repository;

import com.asmsi.admin_system.entity.DepartmentAccess;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface DepartmentAccessRepository extends JpaRepository<DepartmentAccess, Long> {
    Optional<DepartmentAccess> findByDepartmentKey(String departmentKey);
}
