package com.asmsi.admin_system.repository;

import com.asmsi.admin_system.entity.InventoryRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface InventoryRecordRepository extends JpaRepository<InventoryRecord, Long> {

    Optional<InventoryRecord> findByInventoryDateAndPreparedByAndManagerNameAndSchoolItemId(
            LocalDate inventoryDate,
            String preparedBy,
            String managerName,
            Long schoolItemId
    );

    // ✅ REPORT: for year + optional shop
    // ✅ FIX: match by shopScope OR shops (some rows may have shopScope null)
    @Query("""
        select r
        from InventoryRecord r
        where r.inventoryDate between :start and :end
          and (
               :shop is null or :shop = ''
               or lower(coalesce(r.shopScope,'')) = lower(:shop)
               or lower(coalesce(r.shops,'')) = lower(:shop)
          )
    """)
    List<InventoryRecord> findForYearAndShop(
            @Param("start") LocalDate start,
            @Param("end") LocalDate end,
            @Param("shop") String shop
    );

    // ✅ Resume support (kept)
    @Query("""
        select distinct r.schoolItemId
        from InventoryRecord r
        where r.inventoryDate between :start and :end
          and lower(r.preparedBy) = lower(:preparedBy)
          and lower(r.managerName) = lower(:managerName)
          and (:shopScope is null or :shopScope = '' or lower(coalesce(r.shopScope,'')) = lower(:shopScope))
    """)
    List<Long> findSavedItemIdsForMonth(
            @Param("start") LocalDate start,
            @Param("end") LocalDate end,
            @Param("preparedBy") String preparedBy,
            @Param("managerName") String managerName,
            @Param("shopScope") String shopScope
    );
}
