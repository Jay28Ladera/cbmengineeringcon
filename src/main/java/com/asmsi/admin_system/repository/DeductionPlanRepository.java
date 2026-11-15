package com.asmsi.admin_system.repository;

import com.asmsi.admin_system.entity.DeductionPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface DeductionPlanRepository extends JpaRepository<DeductionPlan, Long> {
    List<DeductionPlan> findAllByOrderByActiveDescStartDateDesc();
    List<DeductionPlan> findByActiveTrue();
    boolean existsByEmployeeId(Long employeeId);
}
