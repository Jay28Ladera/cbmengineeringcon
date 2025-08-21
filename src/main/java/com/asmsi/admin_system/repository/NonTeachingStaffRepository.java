// src/main/java/com/asmsi/admin_system/repository/NonTeachingStaffRepository.java
package com.asmsi.admin_system.repository;

import com.asmsi.admin_system.entity.NonTeachingStaff;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.stereotype.Repository;

import java.util.*;

@Repository
public interface NonTeachingStaffRepository extends JpaRepository<NonTeachingStaff, Long> {
    Optional<NonTeachingStaff> findByEmployeeId(String employeeId);
    boolean existsByEmployeeId(String employeeId);

    // newest first (uses updatedAt if present, else createdAt)
    @Query("select n from NonTeachingStaff n order by coalesce(n.updatedAt, n.createdAt) desc")
    List<NonTeachingStaff> findRecent(Pageable pageable);
}
