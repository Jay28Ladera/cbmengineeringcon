package com.asmsi.admin_system.inventory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;

public interface CanvassItemRepository extends JpaRepository<CanvassItem, Long> {
    List<CanvassItem> findByCanvassIdOrderBySortOrderAscIdAsc(Long canvassId);
}
