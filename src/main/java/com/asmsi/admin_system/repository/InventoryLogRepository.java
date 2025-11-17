// InventoryLogRepository.java
package com.asmsi.admin_system.repository;

import com.asmsi.admin_system.entity.InventoryLog;

import java.util.List;
import java.time.LocalDate;

import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryLogRepository extends JpaRepository<InventoryLog, Long> {
    List<InventoryLog> findBySessionDateBetween(LocalDate start, LocalDate end);
}
