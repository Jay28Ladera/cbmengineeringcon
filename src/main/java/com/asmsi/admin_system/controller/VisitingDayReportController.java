package com.asmsi.admin_system.controller;

import com.asmsi.admin_system.repository.AsmsiVisitorRepository;
import com.asmsi.admin_system.repository.StudentRepository;
import com.asmsi.admin_system.service.VisitingDayReportService;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;

@Controller
public class VisitingDayReportController {

    private final VisitingDayReportService svc;
    private final AsmsiVisitorRepository visitsRepo;
    private final StudentRepository studentRepo;

    public VisitingDayReportController(VisitingDayReportService svc,
                                       AsmsiVisitorRepository visitsRepo,
                                       StudentRepository studentRepo) {
        this.svc = svc;
        this.visitsRepo = visitsRepo;
        this.studentRepo = studentRepo;
    }

    // ========= PAGE =========
    @GetMapping("/visitingdayreport")
    public String visitingDayReportPage(
            Model model,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end
    ) {
        if (start == null || end == null) {
            LocalDate today = LocalDate.now();
            start = today;
            end   = today;
        }
        model.addAttribute("familyDayExpanded", true);
        model.addAttribute("activeFamilyDayPage", "report");
        model.addAttribute("start", start);
        model.addAttribute("end", end);
        return "visitingday-report";
    }
}
