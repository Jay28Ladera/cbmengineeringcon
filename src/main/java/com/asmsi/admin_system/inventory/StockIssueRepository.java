package com.asmsi.admin_system.inventory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;

public interface StockIssueRepository extends JpaRepository<StockIssue, Long> {
    List<StockIssue> findAllByOrderByIdDesc();
    List<StockIssue> findByJobOrderIdOrderByIdDesc(Long jobOrderId);
}
