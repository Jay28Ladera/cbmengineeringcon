package com.asmsi.admin_system.accounting;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DepreciationRunRepository extends JpaRepository<DepreciationRun, Long> {
    List<DepreciationRun> findAllByOrderByMonthEndDesc();
    Optional<DepreciationRun> findFirstByMonthEndAndStatus(LocalDate monthEnd, String status);
    Optional<DepreciationRun> findFirstByStatusOrderByMonthEndDesc(String status);
}
