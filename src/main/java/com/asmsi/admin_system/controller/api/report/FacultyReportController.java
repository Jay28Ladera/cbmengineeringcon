package com.asmsi.admin_system.controller.api.report;

import com.asmsi.admin_system.dto.report.FacultyVisitRow;
import com.asmsi.admin_system.dto.report.SimpleListSummary;
import com.asmsi.admin_system.repository.FacultyVisitorRepository;
import org.springframework.web.bind.annotation.*;

import java.time.*;
import java.time.format.DateTimeFormatter;

@RestController
@RequestMapping("/api/faculty/report")
public class FacultyReportController {

    private final FacultyVisitorRepository repo;

    private static final DateTimeFormatter DF = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TF = DateTimeFormatter.ofPattern("HH:mm:ss");

    public FacultyReportController(FacultyVisitorRepository repo) {
        this.repo = repo;
    }

    @GetMapping("/summary")
    public SimpleListSummary<FacultyVisitRow> summary(
            @RequestParam String start,
            @RequestParam String end
    ) {
        // Inclusive date range: [start, end]
        Instant from = LocalDate.parse(start).atStartOfDay(ZoneId.systemDefault()).toInstant();
        Instant to   = LocalDate.parse(end).plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant();

        var rows = repo.findInRange(from, to).stream().map(v -> {
            LocalDateTime dt = LocalDateTime.ofInstant(v.getVisitedAt(), ZoneId.systemDefault());
            return new FacultyVisitRow(
                v.getImageUrl(),
                v.getName(),
                v.getEmployeeId(),
                v.getDepartment(),
                v.getPosition(),
                DF.format(dt),
                TF.format(dt),
                v.getAdultVisitor(),
                v.getKidVisitor(),
                v.getTotalVisitors(),
                v.getVehicleType() == null ? "" : v.getVehicleType().name()
            );
        }).toList();

        return new SimpleListSummary<>(rows.size(), rows);
    }
}
