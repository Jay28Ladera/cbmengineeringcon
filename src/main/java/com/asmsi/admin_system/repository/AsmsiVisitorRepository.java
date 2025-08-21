package com.asmsi.admin_system.repository;

import com.asmsi.admin_system.entity.AsmsiVisitor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface AsmsiVisitorRepository extends JpaRepository<AsmsiVisitor, Long> {

    @Query("""
           select v from AsmsiVisitor v
           join fetch v.alumni a
           order by v.visitedAt desc
           """)
    List<AsmsiVisitor> findRecent(Pageable pageable);
}
