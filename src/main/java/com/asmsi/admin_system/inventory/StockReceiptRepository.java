package com.asmsi.admin_system.inventory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;

public interface StockReceiptRepository extends JpaRepository<StockReceipt, Long> {
    List<StockReceipt> findAllByOrderByIdDesc();
    List<StockReceipt> findByPoIdOrderByIdDesc(Long poId);
}
