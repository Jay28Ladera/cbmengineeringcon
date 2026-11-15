package com.asmsi.admin_system.repository;

import com.asmsi.admin_system.entity.PayrollResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface PayrollResultRepository extends JpaRepository<PayrollResult, Long> {
    List<PayrollResult> findByPeriodId(Long periodId);
    boolean existsByEmployeeId(Long employeeId);
    @Modifying @Query("delete from PayrollResult r where r.periodId = ?1")
    void deleteByPeriod(Long periodId);
}
