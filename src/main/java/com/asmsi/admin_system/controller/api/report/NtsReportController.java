package com.asmsi.admin_system.controller.api.report;

import com.asmsi.admin_system.dto.report.NtsVisitRow;
import com.asmsi.admin_system.dto.report.SimpleListSummary;
import com.asmsi.admin_system.repository.NtsVisitorRepository;
import org.springframework.web.bind.annotation.*;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.List;

@RestController
@RequestMapping("/api/nts/report")
public class NtsReportController {
    private final NtsVisitorRepository repo;
    private static final DateTimeFormatter DF = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TF = DateTimeFormatter.ofPattern("HH:mm:ss");

    public NtsReportController(NtsVisitorRepository repo) {
        this.repo = repo;
    }

    @GetMapping("/summary")
    public SimpleListSummary<NtsVisitRow> summary(
            @RequestParam String start,
            @RequestParam String end
    ) {
        var from = LocalDate.parse(start).atStartOfDay(ZoneId.systemDefault()).toInstant();
        var to   = LocalDate.parse(end).plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant();

        var rows = repo.findInRange(from, to).stream().map(v -> {
            var dt = LocalDateTime.ofInstant(v.getVisitedAt(), ZoneId.systemDefault());
            return new NtsVisitRow(
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
