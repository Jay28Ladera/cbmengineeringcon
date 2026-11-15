package com.asmsi.admin_system.inventory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;

public interface StockReceiptLineRepository extends JpaRepository<StockReceiptLine, Long> {
    List<StockReceiptLine> findByReceiptId(Long receiptId);
    List<StockReceiptLine> findByReceiptIdIn(java.util.Collection<Long> ids);
}
