// src/main/java/com/asmsi/admin_system/service/AttendanceReportService.java
package com.asmsi.admin_system.service;

import com.asmsi.admin_system.dto.AttendanceReportResponse;
import com.asmsi.admin_system.entity.AttendanceLog;
import com.asmsi.admin_system.repository.AttendanceLogRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AttendanceReportService {

    private final AttendanceLogRepository repo;
    private static final DateTimeFormatter D = DateTimeFormatter.ISO_LOCAL_DATE;

    public AttendanceReportService(AttendanceLogRepository repo) {
        this.repo = repo;
    }

    public AttendanceReportResponse buildReport(LocalDate from, LocalDate to, String account) {
        var rows = repo.findBetweenDatesWithOptionalAccount(from, to, account);

        var resp = new AttendanceReportResponse();
        resp.summary = buildSummary(rows);
        resp.dailyCounts = buildDailyCounts(rows);
        resp.topStudents = buildTopStudents(rows);
        resp.records = buildRecords(rows);
        return resp;
    }

    private AttendanceReportResponse.Summary buildSummary(List<AttendanceLog> rows) {
        var s = new AttendanceReportResponse.Summary();
        s.total = rows.size();
        s.present = rows.stream().filter(r -> "present".equalsIgnoreCase(nz(r.getStatus()))).count();
        s.absent  = rows.stream().filter(r -> "absent".equalsIgnoreCase(nz(r.getStatus()))).count();
        s.uniqueStudents = rows.stream()
                .map(r -> nz(r.getIdNumber()))
                .filter(id -> !id.isEmpty())
                .collect(Collectors.toSet()).size();
        return s;
    }

    private List<AttendanceReportResponse.DailyCount> buildDailyCounts(List<AttendanceLog> rows) {
        var map = rows.stream()
                .collect(Collectors.groupingBy(AttendanceLog::getDate, Collectors.counting()));

        return map.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> {
                    var d = new AttendanceReportResponse.DailyCount();
                    d.date = e.getKey() != null ? e.getKey().format(D) : "";
                    d.count = e.getValue();
                    return d;
                })
                .toList();
    }

    private List<AttendanceReportResponse.TopStudent> buildTopStudents(List<AttendanceLog> rows) {
        var counts = rows.stream()
                .collect(Collectors.groupingBy(this::keyForStudent, Collectors.counting()));

        return counts.entrySet().stream()
                .sorted((a,b) -> Long.compare(b.getValue(), a.getValue()))
                .limit(10)
                .map(e -> {
                    var ts = new AttendanceReportResponse.TopStudent();
                    ts.name = e.getKey();
                    ts.count = e.getValue();
                    return ts;
                })
                .toList();
    }

    private String keyForStudent(AttendanceLog r) {
        String id = nz(r.getIdNumber());
        String name = nz(r.getName());
        return !id.isEmpty() ? (name.isEmpty() ? id : name + " (" + id + ")") : name;
    }

    private List<AttendanceReportResponse.Record> buildRecords(List<AttendanceLog> rows) {
        return rows.stream()
                .sorted(
                    Comparator.<AttendanceLog, LocalDate>comparing(AttendanceLog::getDate,
                            Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(AttendanceLog::getTimeIn, Comparator.nullsLast(Comparator.naturalOrder()))
                        .reversed()
                )
                .map(r -> {
                    var rec = new AttendanceReportResponse.Record();
                    rec.imageUrl = nz(r.getImageUrl());
                    rec.name = nz(r.getName());
                    rec.idNumber = nz(r.getIdNumber());
                    rec.date = r.getDate() != null ? r.getDate().format(D) : "";
                    rec.timeIn = r.getTimeIn() != null ? r.getTimeIn().toString() : "";
                    rec.timeOut = r.getTimeOut() != null ? r.getTimeOut().toString() : "";
                    rec.status = nz(r.getStatus());
                    rec.account = nz(r.getAccount());
                    return rec;
                })
                .toList();
    }

    private static String nz(String s) { return s == null ? "" : s; }
}
