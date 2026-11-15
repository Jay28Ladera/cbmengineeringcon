package com.asmsi.admin_system.inventory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;

public interface ToolRepository extends JpaRepository<Tool, Long> {
    List<Tool> findAllByOrderByTagAsc();
    boolean existsByTag(String tag);
}
