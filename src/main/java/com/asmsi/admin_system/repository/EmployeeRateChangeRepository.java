package com.asmsi.admin_system.repository;

import com.asmsi.admin_system.entity.EmployeeRateChange;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface EmployeeRateChangeRepository extends JpaRepository<EmployeeRateChange, Long> {
    List<EmployeeRateChange> findByEmployeeIdOrderByEffectiveDateDescIdDesc(Long employeeId);
    List<EmployeeRateChange> findAllByOrderByEffectiveDateDescIdDesc();
}
