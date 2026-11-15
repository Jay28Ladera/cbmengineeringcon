package com.asmsi.admin_system.accounting;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CashAdvanceRepository extends JpaRepository<CashAdvance, Long> {
    List<CashAdvance> findTop200ByOrderByIdDesc();
    List<CashAdvance> findBySourceIdAndReplenishmentIdIsNullAndStatusNotOrderByIdAsc(Long sourceId, String status);
    List<CashAdvance> findByReplenishmentIdOrderByIdAsc(Long replenishmentId);
}
