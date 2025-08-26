package com.asmsi.admin_system.controller.api;

import com.asmsi.admin_system.dto.report.VisitingDaySummary;
import com.asmsi.admin_system.service.VisitingDayReportService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/visitingday/report")
public class VisitingDayReportApiController {

    private final VisitingDayReportService service;

    public VisitingDayReportApiController(VisitingDayReportService service) {
        this.service = service;
    }

    @GetMapping("/summary")
    public VisitingDaySummary summary(
            @RequestParam(required = false) LocalDate start,
            @RequestParam(required = false) LocalDate end) {

        return service.buildSummary(start, end);
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> exportExcel(
            @RequestParam(required = false) LocalDate start,
            @RequestParam(required = false) LocalDate end) {

        byte[] data = service.buildExcel(start, end);
        String filename = String.format("VisitingDay_%s_%s.xlsx",
                start == null ? "today" : start, end == null ? "today" : end);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(data);
    }
}
