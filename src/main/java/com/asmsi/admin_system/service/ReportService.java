package com.asmsi.admin_system.service;

import com.asmsi.admin_system.repository.SaleItemRepository;
import com.asmsi.admin_system.repository.SaleRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class ReportService {

    public record DailyPoint(LocalDate date, BigDecimal total) {}
    public record TopItem(String itemNumber, String name, long qty, BigDecimal revenue) {}

    private final SaleRepository saleRepo;
    private final SaleItemRepository saleItemRepo;

    public ReportService(SaleRepository saleRepo, SaleItemRepository saleItemRepo) {
        this.saleRepo = saleRepo;
        this.saleItemRepo = saleItemRepo;
    }

    /* -------------------- ORIGINAL SIGNATURES (no cashier) -------------------- */
    public BigDecimal totalSalesBetween(LocalDate start, LocalDate end) {
        return totalSalesBetween(start, end, null);
    }
    public long txnCountBetween(LocalDate start, LocalDate end) {
        return txnCountBetween(start, end, null);
    }
    public long itemsSoldBetween(LocalDate start, LocalDate end) {
        return itemsSoldBetween(start, end, null);
    }
    public List<DailyPoint> dailyTotalsBetween(LocalDate start, LocalDate end) {
        return dailyTotalsBetween(start, end, null);
    }
    public List<TopItem> topItemsBetween(LocalDate start, LocalDate end) {
        return topItemsBetween(start, end, null);
    }

    /* -------------------- CASHIER-AWARE OVERLOADS -------------------- */
    public BigDecimal totalSalesBetween(LocalDate start, LocalDate end, String cashier) {
        Range r = toRange(start, end);
        System.out.println("Debug ReportService - totalSalesBetween: range=" + r + ", cashier='" + cashier + "'");
        
        if (cashier == null || cashier.isBlank()) {
            BigDecimal result = nz(saleRepo.sumTotalBetween(r.start(), r.end()));
            System.out.println("Debug - total sales (no cashier filter): " + result);
            return result;
        }
        BigDecimal result = nz(saleRepo.sumTotalBetweenByCashier(r.start(), r.end(), cashier.trim()));
        System.out.println("Debug - total sales (cashier '" + cashier + "'): " + result);
        return result;
    }

    public long txnCountBetween(LocalDate start, LocalDate end, String cashier) {
        Range r = toRange(start, end);
        System.out.println("Debug ReportService - txnCountBetween: range=" + r + ", cashier='" + cashier + "'");
        
        if (cashier == null || cashier.isBlank()) {
            long result = saleRepo.countSalesBetween(r.start(), r.end());
            System.out.println("Debug - transaction count (no cashier filter): " + result);
            return result;
        }
        long result = saleRepo.countSalesBetweenByCashier(r.start(), r.end(), cashier.trim());
        System.out.println("Debug - transaction count (cashier '" + cashier + "'): " + result);
        return result;
    }

    public long itemsSoldBetween(LocalDate start, LocalDate end, String cashier) {
        Range r = toRange(start, end);
        System.out.println("Debug ReportService - itemsSoldBetween: range=" + r + ", cashier='" + cashier + "'");
        
        if (cashier == null || cashier.isBlank()) {
            long result = saleItemRepo.sumQuantityBetween(r.start(), r.end());
            System.out.println("Debug - items sold (no cashier filter): " + result);
            return result;
        }
        long result = saleItemRepo.sumQuantityBetweenByCashier(r.start(), r.end(), cashier.trim());
        System.out.println("Debug - items sold (cashier '" + cashier + "'): " + result);
        return result;
    }

    public List<DailyPoint> dailyTotalsBetween(LocalDate start, LocalDate end, String cashier) {
        Range r = toRange(start, end);

        // Fill all dates in range with 0 for a continuous chart
        Map<LocalDate, BigDecimal> map = new LinkedHashMap<>();
        LocalDate s = r.start().toLocalDate();
        LocalDate e = r.end().minusNanos(1).toLocalDate();
        for (LocalDate d = s; !d.isAfter(e); d = d.plusDays(1)) map.put(d, BigDecimal.ZERO);

        List<Object[]> rows = (cashier == null || cashier.isBlank())
                ? saleRepo.dailyTotals(r.start(), r.end())
                : saleRepo.dailyTotalsByCashier(r.start(), r.end(), cashier.trim());

        for (Object[] row : rows) {
            // row[0] is the SQL DATE (LocalDate or java.sql.Date depending on JPA dialect)
            LocalDate day = (row[0] instanceof LocalDate ld) ? ld
                    : ((java.sql.Date) row[0]).toLocalDate();
            BigDecimal total = (BigDecimal) row[1];
            map.put(day, total);
        }

        List<DailyPoint> points = new ArrayList<>(map.size());
        map.forEach((d, t) -> points.add(new DailyPoint(d, t)));
        return points;
    }

    public List<TopItem> topItemsBetween(LocalDate start, LocalDate end, String cashier) {
        Range r = toRange(start, end);
        List<TopItem> list = new ArrayList<>();
        List<Object[]> rows = (cashier == null || cashier.isBlank())
                ? saleItemRepo.topItemsBetween(r.start(), r.end())
                : saleItemRepo.topItemsBetweenByCashier(r.start(), r.end(), cashier.trim());

        for (Object[] row : rows) {
            list.add(new TopItem(
                    (String) row[0],
                    (String) row[1],
                    ((Number) row[2]).longValue(),
                    (BigDecimal) row[3]
            ));
        }
        return list;
    }

    /* -------------------- helpers -------------------- */
    private record Range(LocalDateTime start, LocalDateTime end) {}
    
    /** 
     * Convert nullable LocalDate [start,end] to half-open [start, end) LocalDateTime.
     * When both start and end are null, return a range that covers all time (past to future).
     */
    private static Range toRange(LocalDate start, LocalDate end) {
        if (start == null && end == null) {
            // No date filter - return a very wide range to capture all data
            LocalDateTime farPast = LocalDateTime.of(2000, 1, 1, 0, 0);
            LocalDateTime farFuture = LocalDateTime.of(2099, 12, 31, 23, 59, 59);
            System.out.println("Debug - No date range specified, using wide range: " + farPast + " to " + farFuture);
            return new Range(farPast, farFuture);
        }
        
        LocalDate s = (start != null) ? start : LocalDate.of(2000, 1, 1);
        LocalDate e = (end   != null) ? end   : LocalDate.of(2099, 12, 31);
        if (e.isBefore(s)) { LocalDate tmp = s; s = e; e = tmp; }
        
        LocalDateTime startTime = s.atStartOfDay();
        LocalDateTime endTime = e.plusDays(1).atStartOfDay();
        System.out.println("Debug - Date range: " + startTime + " to " + endTime);
        return new Range(startTime, endTime);
    }
    
    private static BigDecimal nz(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }
}