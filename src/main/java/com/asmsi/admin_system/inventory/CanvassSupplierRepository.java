package com.asmsi.admin_system.inventory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;

public interface CanvassSupplierRepository extends JpaRepository<CanvassSupplier, Long> {
    List<CanvassSupplier> findByCanvassIdOrderByIdAsc(Long canvassId);
    long countByCanvassId(Long canvassId);
}
