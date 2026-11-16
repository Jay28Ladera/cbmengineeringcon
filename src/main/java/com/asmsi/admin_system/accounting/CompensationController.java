package com.asmsi.admin_system.accounting;

import com.asmsi.admin_system.admin.SettingsService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.nio.charset.StandardCharsets;
import java.time.YearMonth;
import java.util.List;

/** Accounting › Payroll Taxes: monthly 1601-C and contributions, yearly alphalist and BIR 2316. */
@Controller
@RequestMapping("/Accounting/PayrollTaxes")
public class CompensationController {

    private final CompensationService comp;
    private final SettingsService settings;

    public CompensationController(CompensationService comp, SettingsService settings) { this.comp = comp; this.settings = settings; }

    @GetMapping
    public String page(@RequestParam(required = false) String view, @RequestParam(required = false) String month,
                       @RequestParam(required = false) String year, Model model) {
        boolean yearly = "year".equals(view);
        YearMonth ym = ym(month);
        int y = year(year, ym);
        model.addAttribute("yearly", yearly);
        model.addAttribute("ym", ym);
        model.addAttribute("year", y);
        model.addAttribute("monthValue", ym.toString());
        model.addAttribute("prevMonth", ym.minusMonths(1).toString());
        model.addAttribute("nextMonth", ym.plusMonths(1).toString());
        if (yearly) {
            var s = comp.year(y);
            model.addAttribute("s", s);
            model.addAttribute("missingTin", s.rows().stream().filter(CompensationService.Emp::isMissingTin).count());
        } else {
            var s = comp.month(ym);
            model.addAttribute("s", s);
            model.addAttribute("r", comp.r1601c(s));
        }
        model.addAttribute("co", settings.company());
        model.addAttribute("activePage", "acc-payroll-taxes");
        return "acc-payroll-taxes";
    }

    @GetMapping("/2316")
    public String form2316(@RequestParam(required = false) String year, @RequestParam(required = false) Long employee, Model model) {
        int y = year(year, YearMonth.now().minusMonths(1));
        var s = comp.year(y);
        List<CompensationService.Emp> rows = employee == null ? s.rows() : s.rows().stream().filter(e -> employee.equals(e.employeeId)).toList();
        model.addAttribute("s", s);
        model.addAttribute("rows", rows);
        model.addAttribute("year", y);
        model.addAttribute("co", settings.company());
        return "acc-2316";
    }

    @GetMapping("/contributions.csv")
    public ResponseEntity<byte[]> contributions(@RequestParam(required = false) String month) {
        YearMonth ym = ym(month);
        return csv("Contributions-" + ym + ".csv", comp.contributionsCsv(comp.month(ym)));
    }

    @GetMapping("/alphalist.csv")
    public ResponseEntity<byte[]> alphalist(@RequestParam(required = false) String year) {
        int y = year(year, YearMonth.now().minusMonths(1));
        return csv("Alphalist-1604C-" + y + ".csv", comp.alphalistCsv(comp.year(y)));
    }

    private static YearMonth ym(String s) {
        try { return s == null || s.isBlank() ? YearMonth.now() : YearMonth.parse(s.trim()); } catch (RuntimeException e) { return YearMonth.now(); }
    }

    private static int year(String s, YearMonth fallback) {
        try { int y = Integer.parseInt(s.trim()); return y < 2000 || y > 2100 ? fallback.getYear() : y; } catch (RuntimeException e) { return fallback.getYear(); }
    }

    private static ResponseEntity<byte[]> csv(String name, String body) {
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + name + "\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8)).body(("﻿" + body).getBytes(StandardCharsets.UTF_8));
    }

}
