package com.asmsi.admin_system.inventory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;

public interface StockAdjustmentRepository extends JpaRepository<StockAdjustment, Long> {
    List<StockAdjustment> findAllByOrderByIdDesc();
}
