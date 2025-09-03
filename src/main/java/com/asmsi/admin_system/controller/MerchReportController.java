package com.asmsi.admin_system.controller;

import com.asmsi.admin_system.entity.Sale;
import com.asmsi.admin_system.repository.SaleRepository;
import com.asmsi.admin_system.service.ReportService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.domain.*;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.PrintWriter;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Controller
public class MerchReportController {

    private final ReportService service;
    private final SaleRepository saleRepo;

    public MerchReportController(ReportService service, SaleRepository saleRepo) {
        this.service = service;
        this.saleRepo = saleRepo;
    }

    @GetMapping("/merch-report")
    public String merchReport(
            Model model,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        // ---- KPIs (range-aware via your ReportService) ----
        model.addAttribute("totalSales",   service.totalSalesBetween(start, end));
        model.addAttribute("transactions", service.txnCountBetween(start, end));
        model.addAttribute("itemsSold",    service.itemsSoldBetween(start, end));

        // ---- Decide data source for ONE table list ----
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        List<Sale> tableRows;        // the ONLY list the HTML will iterate
        Page<Sale> salesPage = null; // keep for pager in non-range mode
        boolean isRange = (start != null && end != null);

        if (isRange) {
            // Inclusive day range: [start 00:00, end 23:59:59.999999999]
            LocalDateTime s = start.atStartOfDay();
            LocalDateTime e = end.atTime(LocalTime.MAX);
            tableRows = saleRepo.findByCreatedAtBetweenOrderByCreatedAtDesc(s, e);
        } else {
            salesPage = saleRepo.findAllByOrderByCreatedAtDesc(pageable);
            tableRows = salesPage.getContent();
        }

        // ---- Grand total (all-time, null-safe) ----
        BigDecimal grandTotal = saleRepo.sumAllTotals();
        if (grandTotal == null) grandTotal = BigDecimal.ZERO;

        // ---- Model ----
        model.addAttribute("tableRows", tableRows);
        model.addAttribute("salesPage", salesPage); // null when range is active
        model.addAttribute("isRange", isRange);
        model.addAttribute("grandTotal", grandTotal);

        // Echo params used in the page
        model.addAttribute("start", start);
        model.addAttribute("end", end);
        model.addAttribute("size", size);

        // Optional: sidebar/header flags
        model.addAttribute("merchandiseExpanded", true);
        model.addAttribute("activePage", "merchreport");

        return "merch-report";
    }

    // ---------------- CSV EXPORTS ----------------

    @GetMapping("/merch-report/export-all")
    public void exportAll(HttpServletResponse response) throws Exception {
        response.setContentType("text/csv");
        response.setHeader("Content-Disposition", "attachment; filename=\"sales_all.csv\"");
        try (PrintWriter out = response.getWriter()) {
            out.println("id,receipt_no,created_at,total,cash,change_due");
            int p = 0, chunk = 1000;
            Page<Sale> page;
            do {
                page = saleRepo.findAllByOrderByCreatedAtDesc(PageRequest.of(p++, chunk));
                for (Sale s : page.getContent()) writeCsvRow(out, s);
            } while (page.hasNext());
        }
    }

    @GetMapping("/merch-report/export-range")
    public void exportRange(
            HttpServletResponse response,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end
    ) throws Exception {
        response.setContentType("text/csv");
        response.setHeader("Content-Disposition",
                "attachment; filename=\"sales_" + start + "_to_" + end + ".csv\"");
        LocalDateTime s = start.atStartOfDay();
        LocalDateTime e = end.atTime(LocalTime.MAX);
        List<Sale> rows = saleRepo.findByCreatedAtBetweenOrderByCreatedAtDesc(s, e);

        try (PrintWriter out = response.getWriter()) {
            out.println("id,receipt_no,created_at,total,cash,change_due");
            for (Sale sale : rows) writeCsvRow(out, sale);
        }
    }

    // ---------------- helpers ----------------
    private void writeCsvRow(PrintWriter out, Sale s) {
        out.printf("%d,%s,%s,%.2f,%.2f,%.2f%n",
                s.getId(),
                s.getReceiptNo() == null ? "" : s.getReceiptNo().replace(",", ""),
                s.getCreatedAt(),
                toDouble(s.getTotal()),
                toDouble(s.getCash()),
                toDouble(s.getChangeDue()));
    }
    private double toDouble(BigDecimal v) {
        return v == null ? 0d : v.doubleValue();
    }
}
