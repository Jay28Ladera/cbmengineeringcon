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

    // -------- Metrics & aggregates (range) --------

    @Query("select count(s) from Sale s where s.createdAt between :start and :end")
    long countSalesBetween(@Param("start") LocalDateTime start,
                           @Param("end")   LocalDateTime end);

    @Query("select coalesce(sum(s.total), 0) from Sale s where s.createdAt between :start and :end")
    BigDecimal sumTotalBetween(@Param("start") LocalDateTime start,
                               @Param("end")   LocalDateTime end);

    @Query("""
           select function('date', s.createdAt) as d, coalesce(sum(s.total),0)
           from Sale s
           where s.createdAt between :start and :end
           group by function('date', s.createdAt)
           order by d
           """)
    List<Object[]> dailyTotals(@Param("start") LocalDateTime start,
                               @Param("end")   LocalDateTime end);

    // -------- Lists & paging --------

    // All (unpaged)
    List<Sale> findAllByOrderByCreatedAtDesc();

    // All (paged)
    Page<Sale> findAllByOrderByCreatedAtDesc(Pageable pageable);

    // Range (unpaged) — used for Overview range table
    List<Sale> findByCreatedAtBetweenOrderByCreatedAtDesc(LocalDateTime start, LocalDateTime end);

    // Range (paged) — used when user filters by date range in the Summary tab
    Page<Sale> findByCreatedAtBetweenOrderByCreatedAtDesc(LocalDateTime start, LocalDateTime end, Pageable pageable);

    // -------- Grand totals --------

    @Query("select coalesce(sum(s.total), 0) from Sale s")
    BigDecimal sumAllTotals();
}
