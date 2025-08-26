package com.asmsi.admin_system.service;

import com.asmsi.admin_system.dto.report.VisitingDaySummary;
import com.asmsi.admin_system.entity.Student;
import com.asmsi.admin_system.entity.VisitingDayRecord;
import com.asmsi.admin_system.repository.StudentRepository;
import com.asmsi.admin_system.repository.VisitingDayRepository;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Collectors;

@Service
public class VisitingDayReportService {

    private final VisitingDayRepository visitRepo;
    private final StudentRepository studentRepo;

    public VisitingDayReportService(VisitingDayRepository visitRepo, StudentRepository studentRepo) {
        this.visitRepo = visitRepo;
        this.studentRepo = studentRepo;
    }

    // ------------------------------------------------------------
    // Summary / Aggregation
    // ------------------------------------------------------------
    public VisitingDaySummary buildSummary(LocalDate start, LocalDate end) {
        final List<VisitingDayRecord> records =
                (start != null && end != null)
                        ? visitRepo.findByDateBetweenOrderByDateAsc(start, end)
                        : visitRepo.findByDate(LocalDate.now());

        // Totals
        final int totalAdults   = records.stream().map(r -> safe(r.getAdultVisitor())).reduce(0, Integer::sum);
        final int totalKids     = records.stream().map(r -> safe(r.getKidVisitor())).reduce(0, Integer::sum);
        final int totalVisitors = totalAdults + totalKids;

        // Vehicle counters (for overall cards)
        final Map<String, Integer> vehicles = new HashMap<>();
        for (VisitingDayRecord r : records) {
            final String vt = nv(r.getVehicleType()); // '' if null
            if (!vt.isEmpty()) vehicles.merge(vt, 1, Integer::sum);
        }

        // Distinct student IDs that had visitors in the range
        final Set<String> visitedIds = records.stream()
                .map(VisitingDayRecord::getIdNumber).filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        final int totalStudentsVisited = visitedIds.size();

        // Enrich with student master data
        final Map<String, Student> studentsById = studentRepo.findByIdNumberIn(visitedIds).stream()
                .collect(Collectors.toMap(Student::getIdNumber, s -> s, (a, b) -> a, LinkedHashMap::new));

        // Per family saint / per batch number
        final Map<String, Integer> perFamilySaint = new TreeMap<>();
        final Map<String, Integer> perBatchNumber = new TreeMap<>();

        for (VisitingDayRecord r : records) {
            final int add = safe(r.getAdultVisitor()) + safe(r.getKidVisitor());

            final String fs = firstNonBlank(
                    r.getFamilySaint(),
                    Optional.ofNullable(studentsById.get(r.getIdNumber())).map(Student::getFamilySaint).orElse(null)
            );
            perFamilySaint.merge(fs, add, Integer::sum);

            final String bn = firstNonBlank(
                    r.getBatchNumber(),
                    Optional.ofNullable(studentsById.get(r.getIdNumber())).map(Student::getBatchNumber).orElse(null)
            );
            perBatchNumber.merge(bn, add, Integer::sum);
        }

        // Per-student aggregates for WITH visitors
        final Map<String, Integer> sumAdults = new HashMap<>();
        final Map<String, Integer> sumKids   = new HashMap<>();
        final Map<String, String> earliestTime = new HashMap<>(); // keep "HH:mm:ss" as String
        final Map<String, VisitingDayRecord> latestRecord = new HashMap<>();
        final Map<String, LocalDateTime> latestKey = new HashMap<>();

        for (VisitingDayRecord r : records) {
            final String id = r.getIdNumber();
            if (id == null) continue;

            // sums
            sumAdults.merge(id, safe(r.getAdultVisitor()), Integer::sum);
            sumKids.merge(id, safe(r.getKidVisitor()), Integer::sum);

            // earliest time (string compare is fine for HH:mm:ss)
            final String t = safeStr(r.getTimeIn());
            if (!t.isBlank()) {
                final String cur = earliestTime.get(id);
                if (cur == null || t.compareTo(cur) < 0) earliestTime.put(id, t);
            }

            // latest record (for contact/vehicle/name fallbacks)
            final LocalDate d = (r.getDate() != null) ? r.getDate() : LocalDate.MIN;
            final LocalTime lt = parseTime(t);
            final LocalDateTime key = LocalDateTime.of(d, lt);

            final LocalDateTime curKey = latestKey.get(id);
            if (curKey == null || key.isAfter(curKey)) {
                latestKey.put(id, key);
                latestRecord.put(id, r);
            }
        }

        final List<Student> allStudents = studentRepo.findAll();

        final List<VisitingDaySummary.SimpleStudent> withVisitors = visitedIds.stream()
                .map(id -> {
                    final Student s = studentsById.get(id);
                    final VisitingDayRecord last = latestRecord.get(id);

                    final String familySaint = firstNonBlank(
                            last != null ? last.getFamilySaint() : null,
                            s != null ? s.getFamilySaint() : null
                    );
                    final String batchNumber = firstNonBlank(
                            last != null ? last.getBatchNumber() : null,
                            s != null ? s.getBatchNumber() : null
                    );
                    final String name = (s != null) ? s.getName() : (last != null ? last.getName() : null);

                    final int a = sumAdults.getOrDefault(id, 0);
                    final int k = sumKids.getOrDefault(id, 0);

                    return VisitingDaySummary.SimpleStudent.builder()
                            .idNumber(id)
                            .name(name)
                            .familySaint(familySaint)
                            .batchNumber(batchNumber)
                            .arrivalTime(earliestTime.get(id))
                            .adultVisitor(a)
                            .kidVisitor(k)
                            .totalVisitors(a + k)
                            .contactNumber(last != null ? last.getContactNumber() : null)
                            .vehicleType(last != null ? last.getVehicleType() : null)
                            .build();
                })
                .toList();

        final List<VisitingDaySummary.SimpleStudent> withoutVisitors = allStudents.stream()
                .filter(s -> s.getIdNumber() != null && !visitedIds.contains(s.getIdNumber()))
                .map(s -> VisitingDaySummary.SimpleStudent.builder()
                        .idNumber(s.getIdNumber())
                        .name(s.getName())
                        .familySaint(s.getFamilySaint())
                        .batchNumber(s.getBatchNumber())
                        .build())
                .toList();

        return VisitingDaySummary.builder()
                .totalVisitors(totalVisitors)
                .totalAdults(totalAdults)
                .totalKids(totalKids)
                .totalStudentsVisited(totalStudentsVisited)
                .visitorsPerFamilySaint(perFamilySaint)
                .visitorsPerBatchNumber(perBatchNumber)
                .vehicles(vehicles) // <— fixes summary.getVehicles() error
                .studentsWithVisitors(withVisitors)
                .studentsWithoutVisitors(withoutVisitors)
                .build();
    }

    // ------------------------------------------------------------
    // Excel
    // ------------------------------------------------------------
    public byte[] buildExcel(LocalDate start, LocalDate end) {
        VisitingDaySummary summary = buildSummary(start, end);

        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            // Styles
            CellStyle header = wb.createCellStyle();
            Font headerFont = wb.createFont();
            headerFont.setBold(true);
            header.setFont(headerFont);

            // Summary
            Sheet s1 = wb.createSheet("Summary");
            int r = 0;
            r = writeRow(s1, r, header, "Metric", "Value");
            r = writeRow(s1, r, null, "Total visitors", String.valueOf(nz(summary.getTotalVisitors())));
            r = writeRow(s1, r, null, "Total adults", String.valueOf(nz(summary.getTotalAdults())));
            r = writeRow(s1, r, null, "Total kids", String.valueOf(nz(summary.getTotalKids())));
            r = writeRow(s1, r, null, "Students with visitors", String.valueOf(nz(summary.getTotalStudentsVisited())));
            autosize(s1, 2);

            // Visitors per Batch
            Sheet s2 = wb.createSheet("Visitors per Batch");
            r = 0;
            r = writeRow(s2, r, header, "Batch Number", "Visitors");
            for (Entry<String,Integer> e : summary.getVisitorsPerBatchNumber().entrySet()) {
                r = writeRow(s2, r, null, e.getKey(), String.valueOf(e.getValue()));
            }
            autosize(s2, 2);

            // Visitors per Family Saint
            Sheet s3 = wb.createSheet("Visitors per Family Saint");
            r = 0;
            r = writeRow(s3, r, header, "Family Saint", "Visitors");
            for (Entry<String,Integer> e : summary.getVisitorsPerFamilySaint().entrySet()) {
                r = writeRow(s3, r, null, e.getKey(), String.valueOf(e.getValue()));
            }
            autosize(s3, 2);

            // With visitors
            Sheet s4 = wb.createSheet("With Visitors");
            r = 0;
            r = writeRow(s4, r, header,
                    "ID Number", "Name", "Family Saint", "Batch Number",
                    "Arrival Time", "Adults", "Kids", "Total Visitors",
                    "Contact #", "Vehicle");
            for (var st : summary.getStudentsWithVisitors()) {
                r = writeRow(s4, r, null,
                        nv(st.getIdNumber()),
                        nv(st.getName()),
                        nv(st.getFamilySaint()),
                        nv(st.getBatchNumber()),
                        nv(st.getArrivalTime()),
                        String.valueOf(nz(st.getAdultVisitor())),
                        String.valueOf(nz(st.getKidVisitor())),
                        String.valueOf(nz(st.getTotalVisitors())),
                        nv(st.getContactNumber()),
                        nv(st.getVehicleType())
                );
            }
            autosize(s4, 10);

            // Without visitors
            Sheet s5 = wb.createSheet("Without Visitors");
            r = 0;
            r = writeRow(s5, r, header, "ID Number", "Name", "Family Saint", "Batch Number");
            for (var st : summary.getStudentsWithoutVisitors()) {
                r = writeRow(s5, r, null,
                        nv(st.getIdNumber()),
                        nv(st.getName()),
                        nv(st.getFamilySaint()),
                        nv(st.getBatchNumber()));
            }
            autosize(s5, 4);

            // Vehicles (optional sheet)
            Sheet s6 = wb.createSheet("Vehicles");
            r = 0;
            r = writeRow(s6, r, header, "Type", "Count");
            for (Entry<String,Integer> e : summary.getVehicles().entrySet()) {
                r = writeRow(s6, r, null, e.getKey(), String.valueOf(e.getValue()));
            }
            autosize(s6, 2);

            wb.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate Visiting Day Excel", e);
        }
    }

    // ------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------
    private int safe(Integer n) { return n == null ? 0 : Math.max(0, n); }
    private int nz(Integer n)   { return n == null ? 0 : n; }

    private static final DateTimeFormatter HHMMSS = DateTimeFormatter.ofPattern("HH:mm:ss");

    private String safeStr(LocalTime t) { return (t == null) ? "" : t.format(HHMMSS); }

    private LocalTime parseTime(String hhmmss) {
        try {
            if (hhmmss == null || hhmmss.isBlank()) return LocalTime.MIDNIGHT;
            return LocalTime.parse(hhmmss);
        } catch (Exception ex) {
            return LocalTime.MIDNIGHT;
        }
    }

    private String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) return a;
        if (b != null && !b.isBlank()) return b;
        return "Unknown";
    }

    private int writeRow(Sheet sheet, int rowIndex, CellStyle headerStyle, String... cells) {
        Row row = sheet.createRow(rowIndex++);
        for (int i = 0; i < cells.length; i++) {
            Cell c = row.createCell(i);
            c.setCellValue(cells[i]);
            if (headerStyle != null) c.setCellStyle(headerStyle);
        }
        return rowIndex;
    }

    private void autosize(Sheet s, int cols) { for (int i = 0; i < cols; i++) s.autoSizeColumn(i); }

    private String nv(String v) { return v == null ? "" : v; }
}
