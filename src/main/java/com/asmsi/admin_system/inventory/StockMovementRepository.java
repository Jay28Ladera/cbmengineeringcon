package com.asmsi.admin_system.inventory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;

public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {
    List<StockMovement> findByMaterialIdOrderByIdDesc(Long materialId);
    List<StockMovement> findByJobOrderIdOrderByIdDesc(Long jobOrderId);
    boolean existsByMaterialId(Long materialId);
    @Query("select m.jobOrderId, sum(m.qty * m.unitCost) from StockMovement m where m.jobOrderId is not null group by m.jobOrderId")
    List<Object[]> costByJobOrder();
    /** Net quantity moved per material for one job order: issues are negative, returns and voids positive. */
    @Query("select m.materialId, sum(m.qty) from StockMovement m where m.jobOrderId = ?1 group by m.materialId")
    List<Object[]> netQtyByMaterialForJob(Long jobOrderId);
}
