// src/main/java/com/asmsi/admin_system/repository/AsmsiAlumniRepository.java
package com.asmsi.admin_system.repository;

import com.asmsi.admin_system.entity.AsmsiAlumni;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AsmsiAlumniRepository extends JpaRepository<AsmsiAlumni, Long> {

    Optional<AsmsiAlumni> findByIdNumber(String idNumber);
    boolean existsByIdNumber(String idNumber);

    @Query("""
            select a.batchNumber, count(a)
            from AsmsiAlumni a
            group by a.batchNumber
            order by a.batchNumber
           """)
    List<Object[]> countByBatch();

    @Query("""
            select
              sum(case when lower(a.employed) = 'yes' then 1 else 0 end),
              sum(case when lower(a.selfEmployed) = 'yes' then 1 else 0 end)
            from AsmsiAlumni a
           """)
    List<Object[]> employmentTotals();

    /* ------------ Masterlist search (ID + full name) ------------- */
    @Query("""
        select a from AsmsiAlumni a
        where (:q is null or :q = '')
           or lower(a.idNumber) like lower(concat('%', :q, '%'))
           or lower(
                concat(
                    coalesce(a.lastName, ''), ', ',
                    coalesce(a.firstName, ''), ' ',
                    coalesce(a.middleName, '')
                )
           ) like lower(concat('%', :q, '%'))
           or lower(
                concat(
                    coalesce(a.firstName, ''), ' ',
                    coalesce(a.middleName, ''), ' ',
                    coalesce(a.lastName, '')
                )
           ) like lower(concat('%', :q, '%'))
        order by a.lastName asc, a.firstName asc, a.middleName asc
    """)
    List<AsmsiAlumni> searchTop(@Param("q") String q, Pageable pageable);

    /* ------------ NEW: paged search for DataTables ------------- */
    @Query("""
        select a from AsmsiAlumni a
        where
            lower(a.idNumber) like concat('%', :q, '%')
         or lower(coalesce(a.batchNumber, '')) like concat('%', :q, '%')
         or lower(coalesce(a.lastName, '')) like concat('%', :q, '%')
         or lower(coalesce(a.firstName, '')) like concat('%', :q, '%')
         or lower(coalesce(a.middleName, '')) like concat('%', :q, '%')
         or lower(
                concat(
                    coalesce(a.lastName, ''), ', ',
                    coalesce(a.firstName, ''), ' ',
                    coalesce(a.middleName, '')
                )
           ) like concat('%', :q, '%')
         or lower(
                concat(
                    coalesce(a.firstName, ''), ' ',
                    coalesce(a.middleName, ''), ' ',
                    coalesce(a.lastName, '')
                )
           ) like concat('%', :q, '%')
    """)
    Page<AsmsiAlumni> searchPage(@Param("q") String q, Pageable pageable);
}