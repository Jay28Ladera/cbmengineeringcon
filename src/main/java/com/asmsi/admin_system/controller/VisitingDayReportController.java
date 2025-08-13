package com.asmsi.admin_system.controller;

import com.asmsi.admin_system.dto.VisitingDaySummary;
import com.asmsi.admin_system.service.VisitingDayReportService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@Controller
public class VisitingDayReportController {

    private final VisitingDayReportService svc;

    public VisitingDayReportController(VisitingDayReportService svc) {
        this.svc = svc;
    }

    // ---------- PAGE (renders Thymeleaf template) ----------
    // GET /visitingdayreport
    @GetMapping("/visitingdayreport")
    public String visitingDayReportPage(
            Model model,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end
    ) {
        if (start == null || end == null) {
            LocalDate today = LocalDate.now();
            start = today; end = today;
        }

        VisitingDaySummary summary = svc.buildSummary(start, end);

        // Optional: drive sidebar state or filters on the page
        model.addAttribute("familyDayExpanded", true);
        model.addAttribute("activeFamilyDayPage", "report");
        model.addAttribute("summary", summary);
        model.addAttribute("start", start);
        model.addAttribute("end", end);

        return "visitingday-report"; // templates/visitingday-report.html
    }

    // ---------- APIs (JSON / Excel) ----------
    // GET /api/visitingday/report/summary
    @GetMapping("/api/visitingday/report/summary")
    @ResponseBody
    public VisitingDaySummary summary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end
    ) {
        if (start == null || end == null) {
            LocalDate today = LocalDate.now();
            start = today; end = today;
        }
        return svc.buildSummary(start, end);
    }

    // GET /api/visitingday/report/export
    @GetMapping("/api/visitingday/report/export")
    @ResponseBody
    public ResponseEntity<byte[]> export(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end
    ) {
        if (start == null || end == null) {
            LocalDate today = LocalDate.now();
            start = today; end = today;
        }

        byte[] bytes = svc.buildExcel(start, end);
        String filename = String.format("VisitingDayReport_%s_to_%s.xlsx", start, end);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(bytes);
    }
}
