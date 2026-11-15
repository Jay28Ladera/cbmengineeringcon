package com.asmsi.admin_system.accounting;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface BillLineRepository extends JpaRepository<BillLine, Long> {
    List<BillLine> findByBillIdOrderBySortOrderAscIdAsc(Long billId);
}
