package com.asmsi.admin_system.repository;

import com.asmsi.admin_system.entity.AttendanceLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.time.LocalDateTime;
import java.util.List;

public interface AttendanceLogRepository extends JpaRepository<AttendanceLog, Long> {
    List<AttendanceLog> findByPunchTimeBetweenOrderByPunchTimeAsc(LocalDateTime from, LocalDateTime to);
    boolean existsByPinAndPunchTime(String pin, LocalDateTime punchTime);
    boolean existsByEmployeeIdAndPunchTime(Long employeeId, LocalDateTime punchTime);
    long countByDeviceId(Long deviceId);
    @Query("select a.deviceId, count(a) from AttendanceLog a where a.deviceId is not null group by a.deviceId")
    List<Object[]> countPerDevice();

    @Query("select a from AttendanceLog a where a.punchTime >= ?1 and a.punchTime < ?2 order by a.punchTime desc")
    List<AttendanceLog> findRange(LocalDateTime from, LocalDateTime to, Pageable page);

    /** Device IDs that have punches but no employee with that Biometric ID. */
    @Query("select a.pin, count(a), min(a.punchTime), max(a.punchTime) from AttendanceLog a " +
           "where a.pin is not null and a.employeeId is null and not exists (select e.id from Employee e where e.biometricId = a.pin) " +
           "group by a.pin order by a.pin")
    List<Object[]> unmatchedPins();
}
