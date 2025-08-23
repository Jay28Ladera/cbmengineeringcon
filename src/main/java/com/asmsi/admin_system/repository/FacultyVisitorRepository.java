package com.asmsi.admin_system.repository;

import com.asmsi.admin_system.entity.FacultyVisitor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FacultyVisitorRepository extends JpaRepository<FacultyVisitor, Long> {

  @Query("select v from FacultyVisitor v order by v.visitedAt desc")
  List<FacultyVisitor> findRecent(org.springframework.data.domain.Pageable pageable);

  default List<FacultyVisitor> findRecent(int limit) {
    return findRecent(org.springframework.data.domain.PageRequest.of(0, limit));
  }
}
