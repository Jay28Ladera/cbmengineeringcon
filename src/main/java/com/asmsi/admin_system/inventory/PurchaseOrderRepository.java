package com.asmsi.admin_system.inventory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long> {
    List<PurchaseOrder> findAllByOrderByIdDesc();
    List<PurchaseOrder> findByStatusInOrderByIdDesc(List<String> statuses);
    List<PurchaseOrder> findByCanvassId(Long canvassId);
    List<PurchaseOrder> findByJobOrderIdOrderByIdDesc(Long jobOrderId);
}
