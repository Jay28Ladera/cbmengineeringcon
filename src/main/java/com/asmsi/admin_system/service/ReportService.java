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

    public BigDecimal totalSalesBetween(LocalDate start, LocalDate end) {
        Range r = toRange(start, end);
        return saleRepo.sumTotalBetween(r.start(), r.end());
    }

    public long txnCountBetween(LocalDate start, LocalDate end) {
        Range r = toRange(start, end);
        return saleRepo.countSalesBetween(r.start(), r.end());
    }

    public long itemsSoldBetween(LocalDate start, LocalDate end) {
        Range r = toRange(start, end);
        return saleItemRepo.topItemsBetween(r.start(), r.end())
                .stream()
                .mapToLong(row -> ((Number) row[2]).longValue())
                .sum();
    }

    public List<DailyPoint> dailyTotalsBetween(LocalDate start, LocalDate end) {
        Range r = toRange(start, end);

        // Fill all dates in range with 0 for a continuous chart
        Map<LocalDate, BigDecimal> map = new LinkedHashMap<>();
        LocalDate s = r.start().toLocalDate();
        LocalDate e = r.end().minusNanos(1).toLocalDate();
        for (LocalDate d = s; !d.isAfter(e); d = d.plusDays(1)) map.put(d, BigDecimal.ZERO);

        for (Object[] row : saleRepo.dailyTotals(r.start(), r.end())) {
            LocalDate day = (row[0] instanceof LocalDate ld) ? ld
                    : ((java.sql.Date) row[0]).toLocalDate();
            BigDecimal total = (BigDecimal) row[1];
            map.put(day, total);
        }

        List<DailyPoint> points = new ArrayList<>(map.size());
        map.forEach((d, t) -> points.add(new DailyPoint(d, t)));
        return points;
    }

    public List<TopItem> topItemsBetween(LocalDate start, LocalDate end) {
        Range r = toRange(start, end);
        List<TopItem> list = new ArrayList<>();
        for (Object[] row : saleItemRepo.topItemsBetween(r.start(), r.end())) {
            list.add(new TopItem(
                    (String) row[0],
                    (String) row[1],
                    ((Number) row[2]).longValue(),
                    (BigDecimal) row[3]
            ));
        }
        return list;
    }

    /* helpers */
    private record Range(LocalDateTime start, LocalDateTime end) {}
    /** Convert nullable LocalDate [start,end] to half-open [start, end) LocalDateTime. */
    private static Range toRange(LocalDate start, LocalDate end) {
        LocalDate s = (start != null) ? start : LocalDate.now();
        LocalDate e = (end   != null) ? end   : s;
        if (e.isBefore(s)) { LocalDate tmp = s; s = e; e = tmp; }
        return new Range(s.atStartOfDay(), e.plusDays(1).atStartOfDay());
    }
}
