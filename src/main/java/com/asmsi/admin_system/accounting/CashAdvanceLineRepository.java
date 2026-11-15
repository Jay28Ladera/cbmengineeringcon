package com.asmsi.admin_system.accounting;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CashAdvanceLineRepository extends JpaRepository<CashAdvanceLine, Long> {
    List<CashAdvanceLine> findByAdvanceIdOrderByIdAsc(Long advanceId);
}
