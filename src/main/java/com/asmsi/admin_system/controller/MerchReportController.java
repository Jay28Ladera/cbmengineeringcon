package com.asmsi.admin_system.controller;

import com.asmsi.admin_system.entity.Sale;
import com.asmsi.admin_system.entity.SaleItem;
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
            @RequestParam(required = false, defaultValue = "") String cashier,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        // Normalize cashier parameter - handle empty string as null
        String normalizedCashier = (cashier == null || cashier.trim().isEmpty()) ? null : cashier.trim();
        
        // ---- populate dropdown options for cashier ----
        List<String> cashierOptions = saleRepo.findDistinctCashierNames();
        model.addAttribute("cashierOptions", cashierOptions);
        model.addAttribute("cashier", cashier != null ? cashier : "");

        // ---- KPIs (range-aware and cashier-aware) ----
        // Debug logging to see what's being passed
        System.out.println("Debug - start: " + start + ", end: " + end + ", cashier: '" + normalizedCashier + "'");
        
        model.addAttribute("totalSales",   service.totalSalesBetween(start, end, normalizedCashier));
        model.addAttribute("transactions", service.txnCountBetween(start, end, normalizedCashier));
        model.addAttribute("itemsSold",    service.itemsSoldBetween(start, end, normalizedCashier));

        // ---- Decide data source for table ----
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        List<Sale> tableRows;
        Page<Sale> salesPage = null;
        boolean isRange = (start != null && end != null);
        boolean hasCashier = (normalizedCashier != null);

        if (isRange) {
            LocalDateTime s = start.atStartOfDay();
            LocalDateTime e = end.atTime(LocalTime.MAX);
            if (hasCashier) {
                tableRows = saleRepo.findByCashierNameIgnoreCaseAndCreatedAtBetweenOrderByCreatedAtDesc(
                        normalizedCashier, s, e);
            } else {
                tableRows = saleRepo.findByCreatedAtBetweenOrderByCreatedAtDesc(s, e);
            }
        } else {
            if (hasCashier) {
                salesPage = saleRepo.findByCashierNameIgnoreCaseOrderByCreatedAtDesc(normalizedCashier, pageable);
            } else {
                salesPage = saleRepo.findAllByOrderByCreatedAtDesc(pageable);
            }
            tableRows = salesPage.getContent();
        }

        // ---- Grand total (all-time, unfiltered) ----
        BigDecimal grandTotal = saleRepo.sumAllTotals();
        if (grandTotal == null) grandTotal = BigDecimal.ZERO;

        // ---- Model attributes ----
        model.addAttribute("tableRows", tableRows);
        model.addAttribute("salesPage", salesPage);
        model.addAttribute("isRange", isRange);
        model.addAttribute("grandTotal", grandTotal);

        // Echo params for the view
        model.addAttribute("start", start);
        model.addAttribute("end", end);
        model.addAttribute("size", size);

        // Sidebar/navigation state
        model.addAttribute("merchandiseExpanded", true);
        model.addAttribute("activePage", "merchreport");
        
        // Add username and role for header/sidebar fragments
        // You may need to adjust these based on your authentication setup
        model.addAttribute("username", getCurrentUsername());
        model.addAttribute("role", getCurrentUserRole());

        return "merch-report";
    }

    // ---------------- CSV EXPORTS ----------------

    @GetMapping("/merch-report/export-all")
    public void exportAll(HttpServletResponse response) throws Exception {
        response.setContentType("text/csv");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=\"sales_all.csv\"");
        
        try (PrintWriter out = response.getWriter()) {
            // Updated header to include products
            out.println("id,receipt_no,created_at,cashier,products,total,cash,change_due");
            int p = 0, chunk = 1000;
            Page<Sale> page;
            do {
                page = saleRepo.findAllByOrderByCreatedAtDesc(PageRequest.of(p++, chunk));
                for (Sale s : page.getContent()) {
                    writeCsvRow(out, s);
                }
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
        response.setCharacterEncoding("UTF-8");
        String filename = String.format("sales_%s_to_%s.csv", start, end);
        response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");
        
        LocalDateTime s = start.atStartOfDay();
        LocalDateTime e = end.atTime(LocalTime.MAX);
        List<Sale> rows = saleRepo.findByCreatedAtBetweenOrderByCreatedAtDesc(s, e);

        try (PrintWriter out = response.getWriter()) {
            // Updated header to include products
            out.println("id,receipt_no,created_at,cashier,products,total,cash,change_due");
            for (Sale sale : rows) {
                writeCsvRow(out, sale);
            }
        }
    }

    @GetMapping("/merch-report/export-filtered")
    public void exportFiltered(
            HttpServletResponse response,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end,
            @RequestParam(required = false, defaultValue = "") String cashier
    ) throws Exception {
        response.setContentType("text/csv");
        response.setCharacterEncoding("UTF-8");
        
        // Generate filename based on filters
        StringBuilder filenameSb = new StringBuilder("sales_filtered");
        if (start != null && end != null) {
            filenameSb.append("_").append(start).append("_to_").append(end);
        }
        if (cashier != null && !cashier.trim().isEmpty()) {
            filenameSb.append("_cashier_").append(cashier.trim().replaceAll("[^a-zA-Z0-9]", "_"));
        }
        filenameSb.append(".csv");
        
        response.setHeader("Content-Disposition", "attachment; filename=\"" + filenameSb.toString() + "\"");

        String normalizedCashier = (cashier == null || cashier.trim().isEmpty()) ? null : cashier.trim();
        boolean isRange = (start != null && end != null);
        boolean hasCashier = (normalizedCashier != null);

        List<Sale> rows;
        if (isRange) {
            LocalDateTime s = start.atStartOfDay();
            LocalDateTime e = end.atTime(LocalTime.MAX);
            if (hasCashier) {
                rows = saleRepo.findByCashierNameIgnoreCaseAndCreatedAtBetweenOrderByCreatedAtDesc(
                        normalizedCashier, s, e);
            } else {
                rows = saleRepo.findByCreatedAtBetweenOrderByCreatedAtDesc(s, e);
            }
        } else {
            if (hasCashier) {
                // Export all rows for the cashier (paginated to avoid memory issues)
                try (PrintWriter out = response.getWriter()) {
                    // Updated header to include products
                    out.println("id,receipt_no,created_at,cashier,products,total,cash,change_due");
                    int p = 0, chunk = 1000;
                    Page<Sale> page;
                    do {
                        page = saleRepo.findByCashierNameIgnoreCaseOrderByCreatedAtDesc(
                                normalizedCashier, PageRequest.of(p++, chunk));
                        for (Sale s : page.getContent()) {
                            writeCsvRow(out, s);
                        }
                    } while (page.hasNext());
                    return;
                }
            } else {
                // No filters at all -> same as export-all
                exportAll(response);
                return;
            }
        }

        try (PrintWriter out = response.getWriter()) {
            // Updated header to include products
            out.println("id,receipt_no,created_at,cashier,products,total,cash,change_due");
            for (Sale s : rows) {
                writeCsvRow(out, s);
            }
        }
    }

    // ---------------- Helper methods ----------------
    
    // Updated writeCsvRow method to include products
    private void writeCsvRow(PrintWriter out, Sale s) {
        out.printf("%d,%s,%s,%s,%s,%.2f,%.2f,%.2f%n",
                s.getId(),
                safeCsv(s.getReceiptNo()),
                s.getCreatedAt(),
                safeCsv(s.getCashierName()),
                safeCsv(formatProductsForCsv(s.getItems())), // NEW: Add products
                toDouble(s.getTotal()),
                toDouble(s.getCash()),
                toDouble(s.getChangeDue()));
    }

    // NEW: Helper method to format products for CSV
    private String formatProductsForCsv(List<SaleItem> items) {
        if (items == null || items.isEmpty()) {
            return "No items";
        }
        
        StringBuilder products = new StringBuilder();
        for (int i = 0; i < items.size(); i++) {
            SaleItem item = items.get(i);
            if (i > 0) products.append("; ");
            
            String productName = item.getItem() != null ? item.getItem().getName() : "Unknown Product";
            products.append(productName).append(" x").append(item.getQuantity());
        }
        
        return products.toString();
    }
    
    private double toDouble(BigDecimal v) { 
        return v == null ? 0d : v.doubleValue(); 
    }
    
    private String safeCsv(String v) { 
        if (v == null) return "";
        // Escape commas and quotes for proper CSV formatting
        if (v.contains(",") || v.contains("\"") || v.contains("\n")) {
            return "\"" + v.replace("\"", "\"\"") + "\"";
        }
        return v;
    }
    
    // You may need to implement these methods based on your authentication setup
    private String getCurrentUsername() {
        // Implement based on your security context
        // For example: return SecurityContextHolder.getContext().getAuthentication().getName();
        return "admin"; // placeholder
    }
    
    private String getCurrentUserRole() {
        // Implement based on your security context
        return "ADMIN"; // placeholder
    }
}