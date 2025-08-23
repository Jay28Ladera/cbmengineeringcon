// src/main/java/com/asmsi/admin_system/repository/AsmsiAlumniRepository.java
package com.asmsi.admin_system.repository;

import com.asmsi.admin_system.entity.AsmsiAlumni;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface AsmsiAlumniRepository extends JpaRepository<AsmsiAlumni, Long> {

    /* lookups used by your API/service */
    Optional<AsmsiAlumni> findByIdNumber(String idNumber);
    boolean existsByIdNumber(String idNumber);

    /* ---------- Graduates per batch (label, count) ---------- */
    @Query("""
            select a.batchNumber, count(a)
            from AsmsiAlumni a
            group by a.batchNumber
            order by a.batchNumber
           """)
    List<Object[]> countByBatch();
    // each row -> [0]=String batchNumber, [1]=Long total

    /* ---------- Employed & Self-employed totals (single row) ---------- */
    @Query("""
            select
              sum(case when lower(a.employed) = 'yes' then 1 else 0 end),
              sum(case when lower(a.selfEmployed) = 'yes' then 1 else 0 end)
            from AsmsiAlumni a
           """)
    List<Object[]> employmentTotals();
    // first row -> [0]=Long employedCount, [1]=Long selfEmployedCount
}
