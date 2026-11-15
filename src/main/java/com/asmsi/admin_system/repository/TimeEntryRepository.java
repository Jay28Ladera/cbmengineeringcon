package com.asmsi.admin_system.repository;

import com.asmsi.admin_system.entity.TimeEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface TimeEntryRepository extends JpaRepository<TimeEntry, Long> {
    List<TimeEntry> findByPeriodId(Long periodId);
    long countByPeriodId(Long periodId);
    @Query("select t.periodId, count(t) from TimeEntry t group by t.periodId")
    List<Object[]> countPerPeriod();
    boolean existsByEmployeeId(Long employeeId);
    @Modifying @Query("delete from TimeEntry t where t.periodId = ?1")
    void deleteByPeriod(Long periodId);
    @Modifying @Query("delete from TimeEntry t where t.periodId = ?1 and (t.workDate < ?2 or t.workDate > ?3)")
    void deleteOutside(Long periodId, java.time.LocalDate start, java.time.LocalDate end);
}
