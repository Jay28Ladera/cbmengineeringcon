// src/main/java/com/asmsi/admin_system/controller/AttendanceReportApiController.java
package com.asmsi.admin_system.controller;

import com.asmsi.admin_system.dto.AttendanceReportResponse;
import com.asmsi.admin_system.service.AttendanceReportService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/attendance")
public class AttendanceReportApiController {

    private final AttendanceReportService service;

    public AttendanceReportApiController(AttendanceReportService service) {
        this.service = service;
    }

    // GET /api/attendance/report?from=YYYY-MM-DD&to=YYYY-MM-DD&account=xxx
    @GetMapping("/report")
    public ResponseEntity<AttendanceReportResponse> report(
            @RequestParam("from") String from,
            @RequestParam("to") String to,
            @RequestParam(value = "account", required = false) String account
    ) {
        LocalDate f = LocalDate.parse(from);
        LocalDate t = LocalDate.parse(to);
        return ResponseEntity.ok(service.buildReport(f, t, account));
    }
}
