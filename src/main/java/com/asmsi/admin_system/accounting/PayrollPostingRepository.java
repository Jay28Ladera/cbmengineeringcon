package com.asmsi.admin_system.accounting;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface PayrollPostingRepository extends JpaRepository<PayrollPosting, Long> {
    Optional<PayrollPosting> findFirstByPeriodIdAndStatusNot(Long periodId, String status);
    List<PayrollPosting> findTop100ByOrderByIdDesc();
}
