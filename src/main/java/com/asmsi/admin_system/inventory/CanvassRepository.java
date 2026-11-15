package com.asmsi.admin_system.inventory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;

public interface CanvassRepository extends JpaRepository<Canvass, Long> {
    List<Canvass> findAllByOrderByIdDesc();
    List<Canvass> findByJobOrderIdOrderByIdDesc(Long jobOrderId);
    List<Canvass> findByStatusInOrderByIdAsc(java.util.Collection<String> statuses);
}
