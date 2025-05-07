package com.asmsi.admin_system.controller;

import com.asmsi.admin_system.dto.ExamPlaceStats;
import com.asmsi.admin_system.dto.FamilySaintStats;
import com.asmsi.admin_system.repository.AdmissionDataRepository;
import com.asmsi.admin_system.service.AdmissionService;
import com.asmsi.admin_system.entity.AdmissionData;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import com.asmsi.admin_system.dto.FamilySaintGridEntry;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Controller
@RequestMapping("/reports")
public class ReportController {

    @Autowired
    private AdmissionService admissionService;

    @Autowired
    private AdmissionDataRepository admissionDataRepository;

    // ✅ Optimized: Using SQL aggregation to reduce data load
    @GetMapping("/exam-place")
    @ResponseBody
    public List<ExamPlaceStats> getExamPlaceStats() {
        List<Object[]> results = admissionDataRepository.getExamPlaceStats();
        return results.stream()
                .map(row -> new ExamPlaceStats(
                        (String) row[0], // exam_place
                        (Long) row[1], // total students
                        (Long) row[2])) // arrived students
                .collect(Collectors.toList());
    }

    // ✅ Optimized: Fast count without loading full list
    @GetMapping("/overall-stats")
    @ResponseBody
    @Transactional(readOnly = true)
    public Map<String, Long> getOverallStats() {
        long total = admissionDataRepository.count();
        long arrived = admissionDataRepository.countByStatusIgnoreCase("Arrived");

        Map<String, Long> stats = new HashMap<>();
        stats.put("total", total);
        stats.put("arrived", arrived);
        return stats;
    }

    // ✅ Optimized: SQL aggregation instead of full list
    @GetMapping("/family-saint-stats")
    @ResponseBody
    @Transactional(readOnly = true)
    public List<FamilySaintStats> getFamilySaintStats() {
        List<Object[]> rawStats = admissionDataRepository.getFamilySaintStatsSummary();
        return rawStats.stream()
                .map(row -> {
                    String saint = (String) row[0];
                    Long total = ((Number) row[1]).longValue();
                    String codeCsv = (String) row[2];
                    List<Integer> codes = parseCodes(codeCsv);
                    return new FamilySaintStats(saint, total, codes);
                }).collect(Collectors.toList());
    }

    private List<Integer> parseCodes(String csv) {
        if (csv == null || csv.isEmpty())
            return Collections.emptyList();
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Integer::valueOf)
                .sorted()
                .collect(Collectors.toList());
    }

    @GetMapping("/family-saint-stats-grid")
    @ResponseBody
    @Transactional(readOnly = true)
    public Map<String, List<Map<String, String>>> getFamilySaintStatsGrid() {
        List<AdmissionData> allData = admissionDataRepository.findAllByFamilySaintIsNotNullAndCodeNumberIsNotNull();
        Map<String, List<AdmissionData>> groupedBySaint = allData.stream()
                .collect(Collectors.groupingBy(AdmissionData::getFamilySaint));

        Map<String, List<Map<String, String>>> result = new LinkedHashMap<>();

        for (Map.Entry<String, List<AdmissionData>> entry : groupedBySaint.entrySet()) {
            String saint = entry.getKey();
            List<AdmissionData> students = entry.getValue();

            List<Map<String, String>> grid = new ArrayList<>();

            for (int i = 1; i <= 50; i++) {
                final int cn = i;
                AdmissionData matched = students.stream()
                        .filter(d -> d.getCodeNumber() != null && d.getCodeNumber() == cn)
                        .findFirst()
                        .orElse(null);

                Map<String, String> row = new HashMap<>();
                row.put("codeNumber", String.valueOf(cn));
                row.put("studentName", matched != null ? matched.getLastName() + " " + matched.getFirstName() : "");
                row.put("province", matched != null ? matched.getProvince() : "");

                grid.add(row);
            }

            result.put(saint, grid);
        }

        return result;
    }

    @GetMapping("/reports/family-saint-grid")
    @ResponseBody
    public Map<String, List<Map<String, Object>>> getFamilySaintGrid() {
        List<AdmissionData> allData = admissionDataRepository.findAll();
        Map<String, List<Map<String, Object>>> saintGrid = new HashMap<>();

        for (AdmissionData data : allData) {
            String saint = data.getFamilySaint();
            saintGrid.putIfAbsent(saint, new ArrayList<>());

            Map<String, Object> entry = new HashMap<>();
            entry.put("codeNumber", data.getCodeNumber());
            entry.put("studentName", data.getLastName() + ", " + data.getFirstName());
            entry.put("province", data.getProvince());

            saintGrid.get(saint).add(entry);
        }

        return saintGrid;
    }

}
