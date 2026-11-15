package com.asmsi.admin_system.inventory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;

public interface StockIssueLineRepository extends JpaRepository<StockIssueLine, Long> {
    List<StockIssueLine> findByIssueId(Long issueId);
    List<StockIssueLine> findByIssueIdIn(java.util.Collection<Long> ids);
}
