package com.asmsi.admin_system.inventory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;

public interface ToolIssueRepository extends JpaRepository<ToolIssue, Long> {
    List<ToolIssue> findByToolIdOrderByIdDesc(Long toolId);
    List<ToolIssue> findByReturnedOnIsNull();
    boolean existsByToolId(Long toolId);
    List<ToolIssue> findByJobOrderIdOrderByIdDesc(Long jobOrderId);
}
