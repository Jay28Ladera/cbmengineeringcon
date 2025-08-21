package com.asmsi.admin_system.repository;

import com.asmsi.admin_system.entity.FacultyVisitor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface FacultyVisitorRepository extends JpaRepository<FacultyVisitor, Long> {

    @Query("""
           select v from FacultyVisitor v
           join fetch v.faculty f
           order by v.visitedAt desc
           """)
    List<FacultyVisitor> findRecent(Pageable pageable);
}
