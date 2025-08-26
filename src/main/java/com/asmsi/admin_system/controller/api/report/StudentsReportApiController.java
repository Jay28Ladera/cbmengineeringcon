package com.asmsi.admin_system.controller.api.report;

import com.asmsi.admin_system.dto.report.StudentNoVisitorRow;
import com.asmsi.admin_system.dto.report.StudentWithVisitorRow;
import com.asmsi.admin_system.dto.report.StudentsReportSummary;
import com.asmsi.admin_system.repository.AsmsiVisitorRepository;
import com.asmsi.admin_system.repository.AsmsiVisitorRepository.AsmsiVisitRow;
import com.asmsi.admin_system.repository.StudentRepository;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/visitingday/report-legacy")
public class StudentsReportApiController {

    private final AsmsiVisitorRepository visitsRepo;
    private final StudentRepository studentRepo;

    public StudentsReportApiController(AsmsiVisitorRepository visitsRepo,
                                       StudentRepository studentRepo) {
        this.visitsRepo = visitsRepo;
        this.studentRepo = studentRepo;
    }

    @GetMapping("/summary")
    public StudentsReportSummary summary(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end
    ) {
        // 1) All visit rows in range (projection, not entity)
        List<AsmsiVisitRow> rows = visitsRepo.findRangeReport(start, end);

        // 2) Totals
        long totalAdults   = rows.stream().map(r -> nzInt(r.getAdultVisitor())).reduce(0, Integer::sum);
        long totalKids     = rows.stream().map(r -> nzInt(r.getKidVisitor())).reduce(0, Integer::sum);
        long totalVisitors = rows.stream().map(r -> nzInt(r.getTotalVisitors())).reduce(0, Integer::sum);

        // 3) Per batch & per saint (sum of totalVisitors)
        Map<String, Long> perBatch = rows.stream().collect(
                Collectors.groupingBy(r -> nz(r.getBatchNumber()),
                        Collectors.summingLong(r -> nzInt(r.getTotalVisitors()))));

        Map<String, Long> perSaint = rows.stream().collect(
                Collectors.groupingBy(r -> nz(r.getFamilySaint()),
                        Collectors.summingLong(r -> nzInt(r.getTotalVisitors()))));

        // 4) Vehicles (only the keys the UI reads)
        Map<String, Long> vehicleCounts = rows.stream().collect(
                Collectors.groupingBy(r -> r.getVehicleType() == null ? "NONE" : r.getVehicleType(),
                        Collectors.counting()));
        Map<String, Long> vehicles = Map.of(
                "FOUR_WHEELED", vehicleCounts.getOrDefault("FOUR_WHEELED", 0L),
                "MOTORCYCLE",   vehicleCounts.getOrDefault("MOTORCYCLE",   0L)
        );

        // 5) Students WITH visitors: earliest (date,timeIn) + sum of visitors
        Map<String, List<AsmsiVisitRow>> byStudent =
                rows.stream().collect(Collectors.groupingBy(r -> nz(r.getIdNumber())));

        List<StudentWithVisitorRow> withRows = byStudent.entrySet().stream().map(ent -> {
            String id = ent.getKey();
            List<AsmsiVisitRow> list = ent.getValue();

            AsmsiVisitRow first = list.stream()
                    .min(Comparator
                            .comparing(AsmsiVisitRow::getDate, Comparator.nullsLast(Comparator.naturalOrder()))
                            .thenComparing(AsmsiVisitRow::getTimeIn, Comparator.nullsLast(Comparator.naturalOrder()))
                    ).orElse(list.get(0));

            int sumVisitors = list.stream().map(r -> nzInt(r.getTotalVisitors())).reduce(0, Integer::sum);

            return new StudentWithVisitorRow(
                    id,
                    nz(first.getName()),
                    nz(first.getFamilySaint()),
                    nz(first.getBatchNumber()),
                    first.getTimeIn() == null ? "" : first.getTimeIn().toString(),
                    sumVisitors
            );
        }).sorted(Comparator.comparing(StudentWithVisitorRow::idNumber)).toList();

        long visitedCount = withRows.size();

        // 6) Students WITHOUT visitors = all students - visited ids
        Set<String> visitedIds = byStudent.keySet();
        List<StudentNoVisitorRow> withoutRows = studentRepo.findAll().stream()
                .filter(s -> !visitedIds.contains(nz(s.getIdNumber())))
                .map(s -> new StudentNoVisitorRow(
                        nz(s.getIdNumber()),
                        nz(s.getName()),
                        nz(s.getFamilySaint()),
                        nz(s.getBatchNumber())
                ))
                .sorted(Comparator.comparing(StudentNoVisitorRow::idNumber))
                .toList();

        return new StudentsReportSummary(
                totalVisitors,
                totalAdults,
                totalKids,
                visitedCount,
                perBatch,
                perSaint,
                withRows,
                withoutRows,
                vehicles
        );
    }

    private static String nz(String s) { return s == null ? "" : s; }
    private static int nzInt(Integer i) { return i == null ? 0 : i; }
}
