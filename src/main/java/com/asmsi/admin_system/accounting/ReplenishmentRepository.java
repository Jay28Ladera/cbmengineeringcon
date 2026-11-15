package com.asmsi.admin_system.accounting;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ReplenishmentRepository extends JpaRepository<Replenishment, Long> {
    List<Replenishment> findTop50ByOrderByIdDesc();
}
