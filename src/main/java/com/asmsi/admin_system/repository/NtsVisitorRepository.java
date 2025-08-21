// src/main/java/com/asmsi/admin_system/repository/NtsVisitorRepository.java
package com.asmsi.admin_system.repository;

import com.asmsi.admin_system.entity.NtsVisitor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NtsVisitorRepository extends JpaRepository<NtsVisitor, Long> {

    @Query("select v from NtsVisitor v order by v.visitedAt desc")
    List<NtsVisitor> findRecent(Pageable pageable);
}
