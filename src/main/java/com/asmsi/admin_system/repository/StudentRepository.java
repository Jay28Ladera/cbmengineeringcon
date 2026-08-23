package com.asmsi.admin_system.repository;

import com.asmsi.admin_system.entity.Student;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Repository
public interface StudentRepository extends JpaRepository<Student, Long> {

    Optional<Student> findByIdNumber(String idNumber);

    Optional<Student> findByLrn(String lrn);

    boolean existsByIdNumber(String idNumber);

    List<Student> findAllByOrderByNameAsc();

    List<Student> findByIdNumberIn(Collection<String> idNumbers);


    // ============================================================
    // STUDENT LOOKUP
    // Used also by Visiting Day
    // ============================================================

    @Query("""
        SELECT s
        FROM Student s
        WHERE TRIM(s.idNumber) = TRIM(:idNumber)
    """)
    Optional<Student> findByIdNumberTrimmed(
            @Param("idNumber") String idNumber
    );


    // ============================================================
    // GENERAL LIMITED SEARCH
    // ============================================================

    @Query("""
        SELECT s
        FROM Student s
        WHERE (
            :q IS NULL
            OR :q = ''
            OR LOWER(s.name) LIKE LOWER(CONCAT('%', :q, '%'))
            OR LOWER(s.idNumber) LIKE LOWER(CONCAT('%', :q, '%'))
        )
        ORDER BY s.name ASC
    """)
    List<Student> searchTop(
            @Param("q") String q,
            Pageable pageable
    );


    // ============================================================
    // STUDENT COUNT PER VIA
    // ============================================================

    @Query("""
        SELECT s.via, COUNT(s)
        FROM Student s
        GROUP BY s.via
    """)
    List<Object[]> countStudentsByViaRaw();


    default Map<String, Long> countStudentsByVia() {

        Map<String, Long> result = new HashMap<>();

        List<Object[]> rows = countStudentsByViaRaw();

        for (Object[] row : rows) {

            String via = (String) row[0];
            Long total = (Long) row[1];

            result.put(via, total);
        }

        return result;
    }


    // ============================================================
    // VISITING DAY MASTERLIST SEARCH
    // KEEP THIS
    // ============================================================

    List<Student>
    findTop50ByNameContainingIgnoreCaseOrIdNumberContainingIgnoreCaseOrLrnContainingIgnoreCase(
            String name,
            String idNumber,
            String lrn
    );


    // ============================================================
    // STUDENT MANAGER
    // BATCH TABS
    // ============================================================

    @Query("""
        SELECT s.batchNumber, COUNT(s)
        FROM Student s
        WHERE s.batchNumber IS NOT NULL
          AND TRIM(s.batchNumber) <> ''
        GROUP BY s.batchNumber
        ORDER BY s.batchNumber
    """)
    List<Object[]> countStudentsByBatch();


    // ============================================================
    // STUDENT MANAGER
    // VIA FILTER OPTIONS
    // ============================================================

    @Query("""
        SELECT DISTINCT s.via
        FROM Student s
        WHERE s.via IS NOT NULL
          AND TRIM(s.via) <> ''
        ORDER BY s.via
    """)
    List<String> findDistinctVias();


    // ============================================================
    // STUDENT MANAGER
    // FAMILY SAINT FILTER OPTIONS
    // ============================================================

    @Query("""
        SELECT DISTINCT s.familySaint
        FROM Student s
        WHERE s.familySaint IS NOT NULL
          AND TRIM(s.familySaint) <> ''
        ORDER BY s.familySaint
    """)
    List<String> findDistinctFamilySaints();


    // ============================================================
    // STUDENT MANAGER SEARCH
    //
    // EXACTLY 5 PARAMETERS:
    // 1. batch
    // 2. query
    // 3. via
    // 4. saint
    // 5. pageable
    // ============================================================

    @Query("""
        SELECT s
        FROM Student s
        WHERE
            (
                :batch = ''
                OR LOWER(s.batchNumber) = LOWER(:batch)
            )
        AND
            (
                :via = ''
                OR LOWER(s.via) = LOWER(:via)
            )
        AND
            (
                :saint = ''
                OR LOWER(s.familySaint) = LOWER(:saint)
            )
        AND
            (
                :query = ''
                OR LOWER(s.name)
                    LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(s.idNumber)
                    LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(s.lrn)
                    LIKE LOWER(CONCAT('%', :query, '%'))
            )
    """)
    Page<Student> searchStudentManager(
            @Param("batch") String batch,
            @Param("query") String query,
            @Param("via") String via,
            @Param("saint") String saint,
            Pageable pageable
    );
}