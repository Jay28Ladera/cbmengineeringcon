package com.asmsi.admin_system.accounting;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface YearCloseRepository extends JpaRepository<YearClose, Long> {
    Optional<YearClose> findFirstByYearAndStatus(int year, String status);
    List<YearClose> findAllByOrderByYearDesc();
}
