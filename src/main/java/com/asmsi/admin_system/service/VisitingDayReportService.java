package com.asmsi.admin_system.service;

import com.asmsi.admin_system.dto.VisitingDaySummary;
import com.asmsi.admin_system.entity.VisitingDayRecord;
import com.asmsi.admin_system.entity.Student;
import com.asmsi.admin_system.repository.StudentRepository;
import com.asmsi.admin_system.repository.VisitingDayRepository;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class VisitingDayReportService {

    private final VisitingDayRepository visitRepo;
    private final StudentRepository studentRepo;

    public VisitingDayReportService(VisitingDayRepository visitRepo, StudentRepository studentRepo) {
        this.visitRepo = visitRepo;
        this.studentRepo = studentRepo;
    }

    // --------- Aggregation ---------

    public VisitingDaySummary buildSummary(LocalDate start, LocalDate end) {
        List<VisitingDayRecord> records = (start != null && end != null)
                ? visitRepo.findByDateBetweenOrderByDateAsc(start, end)
                : visitRepo.findByDate(LocalDate.now());

        int totalAdults   = records.stream().map(r -> safe(r.getAdultVisitor())).reduce(0, Integer::sum);
        int totalKids     = records.stream().map(r -> safe(r.getKidVisitor())).reduce(0, Integer::sum);
        int totalVisitors = totalAdults + totalKids;

        // Distinct students that had visitors
        Set<String> visitedIds = records.stream()
                .map(VisitingDayRecord::getIdNumber)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        int totalStudentsVisited = visitedIds.size();

        // Load students for enrichment (batch number, family saint)
        Map<String, Student> studentsByIdNum = studentRepo.findByIdNumberIn(visitedIds).stream()
                .collect(Collectors.toMap(Student::getIdNumber, s -> s, (a,b)->a, LinkedHashMap::new));

        // Per Family Saint (prefer record value, fallback to student)
        Map<String, Integer> perFamilySaint = new TreeMap<>();
        for (VisitingDayRecord r : records) {
            int add = safe(r.getAdultVisitor()) + safe(r.getKidVisitor());
            String key = firstNonBlank(
                    r.getFamilySaint(),
                    Optional.ofNullable(studentsByIdNum.get(r.getIdNumber()))
                            .map(Student::getFamilySaint).orElse("Unknown")
            );
            perFamilySaint.merge(key, add, Integer::sum);
        }

        // Per Batch Number (prefer record value, fallback to student)
        Map<String, Integer> perBatchNumber = new TreeMap<>();
        for (VisitingDayRecord r : records) {
            int add = safe(r.getAdultVisitor()) + safe(r.getKidVisitor());
            String key = firstNonBlank(
                    r.getBatchNumber(),
                    Optional.ofNullable(studentsByIdNum.get(r.getIdNumber()))
                            .map(Student::getBatchNumber).orElse("Unknown")
            );
            perBatchNumber.merge(key, add, Integer::sum);
        }

        // Build lists: with visitors (from records) and without visitors (from full student table)
        List<Student> allStudents = studentRepo.findAll();

        List<VisitingDaySummary.SimpleStudent> withVisitors = visitedIds.stream()
                .map(id -> toSimple(studentsByIdNum.get(id)))
                .filter(Objects::nonNull)
                .toList();

        List<VisitingDaySummary.SimpleStudent> withoutVisitors = allStudents.stream()
                .filter(s -> !visitedIds.contains(s.getIdNumber()))
                .map(this::toSimple)
                .toList();

        return VisitingDaySummary.builder()
                .totalVisitors(totalVisitors)
                .totalAdults(totalAdults)
                .totalKids(totalKids)
                .totalStudentsVisited(totalStudentsVisited)
                .visitorsPerFamilySaint(perFamilySaint)
                .visitorsPerBatchNumber(perBatchNumber)
                .studentsWithVisitors(withVisitors)
                .studentsWithoutVisitors(withoutVisitors)
                .build();
    }

    private int safe(Integer n) { return n == null ? 0 : Math.max(0, n); }

    private String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) return a;
        if (b != null && !b.isBlank()) return b;
        return "Unknown";
    }

    private VisitingDaySummary.SimpleStudent toSimple(Student s) {
        if (s == null) return null;
        return VisitingDaySummary.SimpleStudent.builder()
                .idNumber(s.getIdNumber())
                .name(s.getName())
                .familySaint(s.getFamilySaint())
                .batchNumber(s.getBatchNumber())
                .build();
    }

    // --------- Excel Export ---------

    public byte[] buildExcel(LocalDate start, LocalDate end) {
        VisitingDaySummary summary = buildSummary(start, end);

        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            // Styles
            CellStyle header = wb.createCellStyle();
            Font headerFont = wb.createFont();
            headerFont.setBold(true);
            header.setFont(headerFont);

            // Sheet 1: Summary
            Sheet s1 = wb.createSheet("Summary");
            int r = 0;
            r = writeRow(s1, r, header, "Metric", "Value");
            r = writeRow(s1, r, null, "Total visitors", String.valueOf(summary.getTotalVisitors()));
            r = writeRow(s1, r, null, "Total adults", String.valueOf(summary.getTotalAdults()));
            r = writeRow(s1, r, null, "Total kids", String.valueOf(summary.getTotalKids()));
            r = writeRow(s1, r, null, "Students with visitors", String.valueOf(summary.getTotalStudentsVisited()));
            autosize(s1, 2);

            // Sheet 2: Visitors per Batch
            Sheet s2 = wb.createSheet("Visitors per Batch");
            r = 0;
            r = writeRow(s2, r, header, "Batch Number", "Visitors");
            for (var e : summary.getVisitorsPerBatchNumber().entrySet()) {
                r = writeRow(s2, r, null, e.getKey(), String.valueOf(e.getValue()));
            }
            autosize(s2, 2);

            // Sheet 3: Visitors per Family Saint
            Sheet s3 = wb.createSheet("Visitors per FamilySaint");
            r = 0;
            r = writeRow(s3, r, header, "Family Saint", "Visitors");
            for (var e : summary.getVisitorsPerFamilySaint().entrySet()) {
                r = writeRow(s3, r, null, e.getKey(), String.valueOf(e.getValue()));
            }
            autosize(s3, 2);

            // Sheet 4: Students with Visitors
            Sheet s4 = wb.createSheet("With Visitors");
            r = 0;
            r = writeRow(s4, r, header, "ID Number", "Name", "Family Saint", "Batch Number");
            for (var st : summary.getStudentsWithVisitors()) {
                r = writeRow(s4, r, null, st.getIdNumber(), st.getName(), st.getFamilySaint(), st.getBatchNumber());
            }
            autosize(s4, 4);

            // Sheet 5: Students without Visitors
            Sheet s5 = wb.createSheet("Without Visitors");
            r = 0;
            r = writeRow(s5, r, header, "ID Number", "Name", "Family Saint", "Batch Number");
            for (var st : summary.getStudentsWithoutVisitors()) {
                r = writeRow(s5, r, null, st.getIdNumber(), st.getName(), st.getFamilySaint(), st.getBatchNumber());
            }
            autosize(s5, 4);

            wb.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate Visiting Day Excel", e);
        }
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

    private void autosize(Sheet s, int cols) {
        for (int i = 0; i < cols; i++) s.autoSizeColumn(i);
    }
}
