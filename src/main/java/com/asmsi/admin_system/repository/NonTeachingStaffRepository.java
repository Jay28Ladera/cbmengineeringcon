// src/main/java/com/asmsi/admin_system/repository/NonTeachingStaffRepository.java
package com.asmsi.admin_system.repository;

import com.asmsi.admin_system.entity.NonTeachingStaff;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.*;

@Repository
public interface NonTeachingStaffRepository extends JpaRepository<NonTeachingStaff, Long> {
    Optional<NonTeachingStaff> findByEmployeeId(String employeeId);
    boolean existsByEmployeeId(String employeeId);

    // newest first (uses updatedAt if present, else createdAt)
    @Query("select n from NonTeachingStaff n order by coalesce(n.updatedAt, n.createdAt) desc")
    List<NonTeachingStaff> findRecent(Pageable pageable);

     // Masterlist search (name or employeeId)
    @Query("""
        select n from NonTeachingStaff n
        where (:q is null or :q = '' 
               or lower(n.name) like lower(concat('%', :q, '%'))
               or lower(n.employeeId) like lower(concat('%', :q, '%')))
        order by n.name asc
    """)
    List<NonTeachingStaff> searchTop(@Param("q") String q, Pageable pageable);

}
