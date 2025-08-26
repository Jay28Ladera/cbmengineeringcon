package com.asmsi.admin_system.repository;

import com.asmsi.admin_system.entity.NtsVisitor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface NtsVisitorRepository extends JpaRepository<NtsVisitor, Long> {

  @Query("select v from NtsVisitor v order by v.visitedAt desc")
  List<NtsVisitor> findRecent(org.springframework.data.domain.Pageable pageable);

  default List<NtsVisitor> findRecent(int limit) {
    return findRecent(org.springframework.data.domain.PageRequest.of(0, limit));
  }

  @Query("""
       select v from NtsVisitor v
       where v.visitedAt >= :from and v.visitedAt < :to
       order by v.visitedAt desc
       """)
List<NtsVisitor> findInRange(@Param("from") Instant from, @Param("to") Instant to);
}
