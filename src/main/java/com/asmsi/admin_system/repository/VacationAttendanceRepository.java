// src/main/java/com/asmsi/admin_system/repository/AttendanceLogRepository.java
package com.asmsi.admin_system.repository;

import com.asmsi.admin_system.entity.VacationAttendance;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface VacationAttendanceRepository extends JpaRepository<VacationAttendance, Long> {

    // for submit flow
    Optional<VacationAttendance> findTopByIdNumberAndDateOrderByTimeInDesc(String idNumber, LocalDate date);
    List<VacationAttendance> findByIdNumber(String idNumber);

    // REPORT QUERY — filter by account OR recorded_by (optional)
    @Query("""
           SELECT a FROM AttendanceLog a
           WHERE a.date BETWEEN :from AND :to
             AND (
                 :account IS NULL OR :account = '' OR
                 a.account = :account OR a.recordedBy = :account
             )
           """)
    List<VacationAttendance> findBetweenDatesWithOptionalAccount(@Param("from") LocalDate from,
                                                            @Param("to") LocalDate to,
                                                            @Param("account") String account);

    // DISTINCT ACCOUNTS for dropdown (merge account/recorded_by)
    @Query("""
           SELECT DISTINCT COALESCE(a.account, a.recordedBy)
           FROM AttendanceLog a
           WHERE COALESCE(a.account, a.recordedBy) IS NOT NULL
             AND COALESCE(a.account, a.recordedBy) <> ''
           ORDER BY COALESCE(a.account, a.recordedBy) ASC
           """)
    List<String> findDistinctAccounts();
}
