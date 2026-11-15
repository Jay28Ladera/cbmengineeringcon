package com.asmsi.admin_system.repository;

import com.asmsi.admin_system.entity.PayAdjustment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface PayAdjustmentRepository extends JpaRepository<PayAdjustment, Long> {
    List<PayAdjustment> findByPeriodIdOrderByIdAsc(Long periodId);
    List<PayAdjustment> findByPeriodIdAndPlanIdIsNotNull(Long periodId);
    List<PayAdjustment> findByPlanIdOrderByPeriodIdAsc(Long planId);
    boolean existsByPlanId(Long planId);
    List<PayAdjustment> findByPlanIdIsNotNull();
    boolean existsByEmployeeId(Long employeeId);
    @Modifying @Query("delete from PayAdjustment a where a.periodId = ?1")
    void deleteByPeriod(Long periodId);
}
