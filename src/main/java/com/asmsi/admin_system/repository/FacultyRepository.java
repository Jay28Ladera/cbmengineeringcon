// src/main/java/com/asmsi/admin_system/repository/FacultyRepository.java
package com.asmsi.admin_system.repository;

import com.asmsi.admin_system.entity.Faculty;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface FacultyRepository extends JpaRepository<Faculty, Long> {
    Optional<Faculty> findByEmployeeId(String employeeId);
    
    boolean existsByEmployeeId(String employeeId);

     @Query("""
        select f from Faculty f
        where (:q is null or :q = '')
           or lower(f.name)       like lower(concat('%', :q, '%'))
           or lower(f.employeeId) like lower(concat('%', :q, '%'))
        order by f.name asc
    """)
    List<Faculty> searchTop(@Param("q") String q, Pageable pageable);
}
