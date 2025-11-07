package com.asmsi.admin_system.repository;

import com.asmsi.admin_system.entity.Sale;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public interface SaleRepository extends JpaRepository<Sale, Long> {

    /* ---------- Distinct cashier names for dropdown ---------- */
    @Query("""
           select distinct s.cashierName
           from Sale s
           where s.cashierName is not null
             and trim(s.cashierName) <> ''
           order by s.cashierName asc
           """)
    List<String> findDistinctCashierNames();

    /* ---------- Grand total (all-time) ---------- */
    @Query("select coalesce(sum(s.total), 0) from Sale s")
    BigDecimal sumAllTotals();

    /* ---------- Paging / listing ---------- */
    Page<Sale> findAllByOrderByCreatedAtDesc(Pageable pageable);
    List<Sale> findByCreatedAtBetweenOrderByCreatedAtDesc(LocalDateTime start, LocalDateTime end);
    Page<Sale> findByCashierNameIgnoreCaseOrderByCreatedAtDesc(String cashierName, Pageable pageable);
    List<Sale> findByCashierNameIgnoreCaseAndCreatedAtBetweenOrderByCreatedAtDesc(
            String cashierName, LocalDateTime start, LocalDateTime end);

    /* ---------- Aggregates (range) ---------- */
    @Query("select count(s) from Sale s where s.createdAt between :start and :end")
    long countSalesBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("select coalesce(sum(s.total), 0) from Sale s where s.createdAt between :start and :end")
    BigDecimal sumTotalBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    /* ---------- Aggregates (range + cashier) ---------- */
    @Query("""
           select count(s)
           from Sale s
           where s.createdAt between :start and :end
             and lower(coalesce(s.cashierName,'')) = lower(:cashier)
           """)
    long countSalesBetweenByCashier(@Param("start") LocalDateTime start,
                                    @Param("end") LocalDateTime end,
                                    @Param("cashier") String cashier);

    @Query("""
           select coalesce(sum(s.total), 0)
           from Sale s
           where s.createdAt between :start and :end
             and lower(coalesce(s.cashierName,'')) = lower(:cashier)
           """)
    BigDecimal sumTotalBetweenByCashier(@Param("start") LocalDateTime start,
                                        @Param("end") LocalDateTime end,
                                        @Param("cashier") String cashier);

    /* ---------- Daily totals (range) ---------- */
    @Query("""
           select function('date', s.createdAt) as d, coalesce(sum(s.total),0)
           from Sale s
           where s.createdAt between :start and :end
           group by function('date', s.createdAt)
           order by d asc
           """)
    List<Object[]> dailyTotals(@Param("start") LocalDateTime start,
                               @Param("end") LocalDateTime end);

    /* ---------- Daily totals (range + cashier) ---------- */
    @Query("""
           select function('date', s.createdAt) as d, coalesce(sum(s.total),0)
           from Sale s
           where s.createdAt between :start and :end
             and lower(coalesce(s.cashierName,'')) = lower(:cashier)
           group by function('date', s.createdAt)
           order by d asc
           """)
    List<Object[]> dailyTotalsByCashier(@Param("start") LocalDateTime start,
                                        @Param("end") LocalDateTime end,
                                        @Param("cashier") String cashier);

                                        @Query("SELECT s FROM Sale s WHERE s.receiptNo LIKE :prefix% ORDER BY s.receiptNo DESC")
List<Sale> findByReceiptNoStartingWith(@Param("prefix") String prefix);
}
