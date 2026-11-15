package com.asmsi.admin_system.controller;

import com.asmsi.admin_system.entity.Employee;
import com.asmsi.admin_system.entity.EmployeeRateChange;
import com.asmsi.admin_system.payroll.PayrollCalculator;
import com.asmsi.admin_system.payroll.PayrollService;
import com.asmsi.admin_system.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/** HR › Employees: the payroll master list (org chart people plus anyone added here). */
@Controller
@RequestMapping("/HR/Employees")
public class HrEmployeeController {

    private final PayrollService payroll;
    private final EmployeeRepository employees;
    private final EmployeeRateChangeRepository rateChanges;
    private final TimeEntryRepository entries;
    private final PayAdjustmentRepository adjustments;
    private final PayrollResultRepository results;
    private final DeductionPlanRepository plans;
    private final ObjectMapper json;

    public HrEmployeeController(PayrollService payroll, EmployeeRepository employees, EmployeeRateChangeRepository rateChanges,
                                TimeEntryRepository entries, PayAdjustmentRepository adjustments, PayrollResultRepository results,
                                DeductionPlanRepository plans, ObjectMapper json) {
        this.payroll = payroll; this.employees = employees; this.rateChanges = rateChanges; this.entries = entries;
        this.adjustments = adjustments; this.results = results; this.plans = plans; this.json = json;
    }

    @GetMapping
    @Transactional
    public String list(Model model) throws Exception {
        payroll.syncFromOrgChart();
        List<Employee> all = payroll.sorted(employees.findAll());
        var settings = payroll.settings();

        Map<Long, List<EmployeeRateChange>> history = new HashMap<>();
        for (EmployeeRateChange c : rateChanges.findAllByOrderByEffectiveDateDescIdDesc())
            history.computeIfAbsent(c.getEmployeeId(), k -> new ArrayList<>()).add(c);

        Map<String, Object> data = new LinkedHashMap<>();
        for (Employee e : all) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", e.getId());
            m.put("linked", e.isLinked());
            m.put("employeeNo", e.getEmployeeNo());
            m.put("biometricId", e.getBiometricId());
            m.put("fullName", e.getFullName());
            m.put("position", e.getPosition());
            m.put("department", e.getDepartment());
            m.put("payGroup", e.getPayGroup());
            m.put("statusCode", e.getStatusCode());
            m.put("employmentStatus", e.getEmploymentStatus());
            m.put("rateType", e.isMonthly() ? "MONTHLY" : "DAILY");
            m.put("dailyRate", plain(e.getDailyRate()));
            m.put("monthlyRate", plain(e.getMonthlyRate()));
            m.put("computedDaily", plain(PayrollCalculator.money(PayrollCalculator.dailyRate(e, settings))));
            m.put("sss", plain(e.getSssAmount()));
            m.put("philhealth", plain(e.getPhilhealthAmount()));
            m.put("pagibig", plain(e.getPagibigAmount()));
            m.put("contributionCutoff", e.getContributionCutoff());
            m.put("active", e.isActive());
            m.put("dateHired", e.getDateHired() == null ? null : e.getDateHired().toString());
            m.put("remarks", e.getRemarks());
            List<Map<String, Object>> h = new ArrayList<>();
            for (EmployeeRateChange c : history.getOrDefault(e.getId(), List.of())) {
                Map<String, Object> hm = new LinkedHashMap<>();
                hm.put("date", c.getEffectiveDate() == null ? "" : c.getEffectiveDate().toString());
                hm.put("oldDaily", plain(c.getOldDaily())); hm.put("newDaily", plain(c.getNewDaily()));
                hm.put("oldMonthly", plain(c.getOldMonthly())); hm.put("newMonthly", plain(c.getNewMonthly()));
                hm.put("note", c.getNote()); hm.put("by", c.getChangedBy());
                h.add(hm);
            }
            m.put("history", h);
            data.put(String.valueOf(e.getId()), m);
        }

        long active = all.stream().filter(Employee::isActive).count();
        model.addAttribute("employees", all);
        model.addAttribute("employeesJson", json.writeValueAsString(data).replace("</", "<\\/"));
        model.addAttribute("groups", payroll.groups());
        model.addAttribute("activeCount", active);
        model.addAttribute("linkedCount", all.stream().filter(e -> e.isActive() && e.isLinked()).count());
        model.addAttribute("noRateCount", all.stream().filter(e -> e.isActive() && PayrollCalculator.dailyRate(e, settings).signum() == 0).count());
        model.addAttribute("today", LocalDate.now().toString());
        model.addAttribute("activePage", "employees");
        return "hr-employees";
    }

    @PostMapping("/save")
    @Transactional
    public String save(@RequestParam(required = false) Long id,
                       @RequestParam(required = false) String fullName,
                       @RequestParam(required = false) String employeeNo,
                       @RequestParam(required = false) String biometricId,
                       @RequestParam(required = false) String position,
                       @RequestParam(required = false) String department,
                       @RequestParam(required = false) String payGroup,
                       @RequestParam(required = false) String statusCode,
                       @RequestParam(required = false) String employmentStatus,
                       @RequestParam(defaultValue = "DAILY") String rateType,
                       @RequestParam(required = false) BigDecimal dailyRate,
                       @RequestParam(required = false) BigDecimal monthlyRate,
                       @RequestParam(required = false) String rateEffective,
                       @RequestParam(required = false) String rateNote,
                       @RequestParam(required = false) BigDecimal sss,
                       @RequestParam(required = false) BigDecimal philhealth,
                       @RequestParam(required = false) BigDecimal pagibig,
                       @RequestParam(defaultValue = "SECOND") String contributionCutoff,
                       @RequestParam(required = false) String dateHired,
                       @RequestParam(required = false) String remarks,
                       @RequestParam(defaultValue = "false") boolean active,
                       RedirectAttributes redirect) {
        Employee e = id == null ? new Employee() : employees.findById(id).orElse(null);
        if (e == null) { redirect.addFlashAttribute("error", "That employee no longer exists."); return "redirect:/HR/Employees"; }
        boolean isNew = id == null;

        if (!e.isLinked()) {
            if (blank(fullName)) { redirect.addFlashAttribute("error", "Enter the employee's name."); return "redirect:/HR/Employees"; }
            e.setFullName(cut(fullName, 160));
            e.setEmployeeNo(cut(employeeNo, 64));
            e.setPosition(cut(position, 160));
            e.setDepartment(cut(department, 120));
            e.setDateHired(parseDate(dateHired));
        }
        String pin = com.asmsi.admin_system.payroll.AttendanceService.cleanPin(biometricId);
        if (pin != null) {
            var other = employees.findByBiometricId(pin);
            if (other.isPresent() && !other.get().getId().equals(e.getId())) {
                redirect.addFlashAttribute("error", "Biometric ID " + pin + " already belongs to " + other.get().getFullName() + ".");
                return "redirect:/HR/Employees";
            }
        }
        e.setBiometricId(pin);
        e.setPayGroup(cut(payGroup, 80));
        e.setStatusCode(cut(statusCode == null ? null : statusCode.toUpperCase(), 8));
        e.setEmploymentStatus(cut(employmentStatus, 40));
        e.setRateType("MONTHLY".equalsIgnoreCase(rateType) ? "MONTHLY" : "DAILY");

        BigDecimal newDaily = positive(dailyRate), newMonthly = "MONTHLY".equals(e.getRateType()) ? positive(monthlyRate) : null;
        boolean rateChanged = !same(e.getDailyRate(), newDaily) || !same(e.getMonthlyRate(), newMonthly);
        BigDecimal oldDaily = e.getDailyRate(), oldMonthly = e.getMonthlyRate();
        e.setDailyRate(newDaily);
        e.setMonthlyRate(newMonthly);

        e.setSssAmount(positive(sss));
        e.setPhilhealthAmount(positive(philhealth));
        e.setPagibigAmount(positive(pagibig));
        e.setContributionCutoff(switch (contributionCutoff.toUpperCase()) { case "FIRST" -> "FIRST"; case "BOTH" -> "BOTH"; default -> "SECOND"; });
        e.setRemarks(cut(remarks, 600));
        e.setActive(active || isNew);
        employees.save(e);

        if (rateChanged && (newDaily != null || newMonthly != null || oldDaily != null || oldMonthly != null)) {
            EmployeeRateChange c = new EmployeeRateChange();
            c.setEmployeeId(e.getId());
            LocalDate eff = parseDate(rateEffective);
            c.setEffectiveDate(eff == null ? LocalDate.now() : eff);
            c.setOldDaily(oldDaily); c.setNewDaily(newDaily);
            c.setOldMonthly(oldMonthly); c.setNewMonthly(newMonthly);
            c.setNote(isNew ? "Starting rate" : cut(rateNote, 300));
            c.setChangedBy(currentUser());
            rateChanges.save(c);
        }
        redirect.addFlashAttribute("success", (isNew ? "Added " : "Saved ") + e.getFullName() + ".");
        return "redirect:/HR/Employees";
    }

    @PostMapping("/{id}/delete")
    @Transactional
    public String delete(@PathVariable Long id, RedirectAttributes redirect) {
        Employee e = employees.findById(id).orElse(null);
        if (e == null) return "redirect:/HR/Employees";
        if (e.isLinked()) {
            redirect.addFlashAttribute("error", e.getFullName() + " is on the organizational chart. Remove or resign them there, or mark them inactive here.");
        } else if (entries.existsByEmployeeId(id) || adjustments.existsByEmployeeId(id) || results.existsByEmployeeId(id) || plans.existsByEmployeeId(id)) {
            redirect.addFlashAttribute("error", e.getFullName() + " already has payroll records, so they can't be deleted. Mark them inactive instead.");
        } else {
            rateChanges.deleteAll(rateChanges.findByEmployeeIdOrderByEffectiveDateDescIdDesc(id));
            employees.delete(e);
            redirect.addFlashAttribute("success", "Deleted " + e.getFullName() + ".");
        }
        return "redirect:/HR/Employees";
    }

    static String plain(BigDecimal v) { return v == null ? null : v.stripTrailingZeros().toPlainString(); }
    static boolean blank(String s) { return s == null || s.isBlank(); }
    static String cut(String s, int max) {
        if (blank(s)) return null;
        String t = s.trim();
        return t.length() > max ? t.substring(0, max) : t;
    }
    static BigDecimal positive(BigDecimal v) { return v == null || v.signum() <= 0 ? null : v.setScale(2, java.math.RoundingMode.HALF_UP); }
    static boolean same(BigDecimal a, BigDecimal b) { return a == null ? b == null : b != null && a.compareTo(b) == 0; }
    static LocalDate parseDate(String s) {
        if (blank(s)) return null;
        try { return LocalDate.parse(s.trim()); } catch (Exception ex) { return null; }
    }
    static String currentUser() {
        var a = SecurityContextHolder.getContext().getAuthentication();
        return a == null ? null : a.getName();
    }
}
