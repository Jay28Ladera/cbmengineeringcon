package com.asmsi.admin_system.repository;

import com.asmsi.admin_system.entity.PayrollPeriod;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PayrollPeriodRepository extends JpaRepository<PayrollPeriod, Long> {
    List<PayrollPeriod> findAllByOrderByStartDateDesc();
    Optional<PayrollPeriod> findFirstByOrderByEndDateDesc();
    boolean existsByStartDateLessThanEqualAndEndDateGreaterThanEqual(LocalDate end, LocalDate start);
    boolean existsByStartDateLessThanEqualAndEndDateGreaterThanEqualAndIdNot(LocalDate end, LocalDate start, Long id);
}
