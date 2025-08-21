// src/main/java/com/asmsi/admin_system/repository/FacultyRepository.java
package com.asmsi.admin_system.repository;

import com.asmsi.admin_system.entity.Faculty;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FacultyRepository extends JpaRepository<Faculty, Long> {
    Optional<Faculty> findByEmployeeId(String employeeId);
    boolean existsByEmployeeId(String employeeId);
}
