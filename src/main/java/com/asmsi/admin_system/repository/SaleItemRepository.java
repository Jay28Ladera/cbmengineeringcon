package com.asmsi.admin_system.repository;

import com.asmsi.admin_system.entity.SaleItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface SaleItemRepository extends JpaRepository<SaleItem, Long> {

    long countByItem_Id(Long itemId);

    @Modifying
    void deleteByItem_Id(Long itemId);

    @Query("select si.item.id, sum(si.quantity) from SaleItem si group by si.item.id")
    List<Object[]> totalSoldByItem();

    @Query("""
           select si.item.itemNumber, si.item.name, sum(si.quantity), sum(si.subTotal)
           from SaleItem si
           where si.sale.createdAt between :start and :end
           group by si.item.itemNumber, si.item.name
           order by sum(si.quantity) desc
           """)
    List<Object[]> topItemsBetween(@Param("start") LocalDateTime start,
                                   @Param("end")   LocalDateTime end);

    /* ========== APPENDED: Cashier-aware + quantity sums for KPIs ========== */

    // Top items within range filtered by cashier
    @Query("""
           select si.item.itemNumber, si.item.name, sum(si.quantity), sum(si.subTotal)
           from SaleItem si
           where si.sale.createdAt between :start and :end
             and lower(coalesce(si.sale.cashierName, '')) = lower(:cashier)
           group by si.item.itemNumber, si.item.name
           order by sum(si.quantity) desc
           """)
    List<Object[]> topItemsBetweenByCashier(@Param("start") LocalDateTime start,
                                            @Param("end")   LocalDateTime end,
                                            @Param("cashier") String cashier);

    // Total quantity in range (for "Items Sold" KPI)
    @Query("""
           select coalesce(sum(si.quantity), 0)
           from SaleItem si
           where si.sale.createdAt between :start and :end
           """)
    long sumQuantityBetween(@Param("start") LocalDateTime start,
                            @Param("end")   LocalDateTime end);

    // Total quantity in range for a given cashier (cashier-aware KPI)
    @Query("""
           select coalesce(sum(si.quantity), 0)
           from SaleItem si
           where si.sale.createdAt between :start and :end
             and lower(coalesce(si.sale.cashierName, '')) = lower(:cashier)
           """)
    long sumQuantityBetweenByCashier(@Param("start") LocalDateTime start,
                                     @Param("end")   LocalDateTime end,
                                     @Param("cashier") String cashier);
}
