package com.asmsi.admin_system.repository;

import com.asmsi.admin_system.entity.TimesheetRow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface TimesheetRowRepository extends JpaRepository<TimesheetRow, Long> {
    List<TimesheetRow> findByPeriodId(Long periodId);
    @Modifying @Query("delete from TimesheetRow t where t.periodId = ?1")
    void deleteByPeriod(Long periodId);
}
