package com.asmsi.admin_system.controller;

import com.asmsi.admin_system.entity.*;
import com.asmsi.admin_system.payroll.*;
import com.asmsi.admin_system.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static com.asmsi.admin_system.controller.HrEmployeeController.*;

/** HR › Payroll Periods, Time Sheets, Pay Sheets, Holidays, Loans & Deductions and Pay Rules. */
@Controller
@RequestMapping("/HR")
public class HrPayrollController {

    private final PayrollService payroll;
    private final PayrollPeriodRepository periods;
    private final TimeEntryRepository entries;
    private final TimesheetRowRepository rows;
    private final PayAdjustmentRepository adjustments;
    private final HolidayRepository holidays;
    private final DeductionPlanRepository plans;
    private final EmployeeRepository employees;
    private final PayrollSettingsRepository settingsRepo;
    private final PayrollExcelExporter exporter;
    private final ObjectMapper json;

    public HrPayrollController(PayrollService payroll, PayrollPeriodRepository periods, TimeEntryRepository entries,
                               TimesheetRowRepository rows, PayAdjustmentRepository adjustments, HolidayRepository holidays,
                               DeductionPlanRepository plans, EmployeeRepository employees, PayrollSettingsRepository settingsRepo,
                               PayrollExcelExporter exporter, ObjectMapper json) {
        this.payroll = payroll; this.periods = periods; this.entries = entries; this.rows = rows; this.adjustments = adjustments;
        this.holidays = holidays; this.plans = plans; this.employees = employees; this.settingsRepo = settingsRepo;
        this.exporter = exporter; this.json = json;
    }

    // ================================================================ Payroll periods

    @Transactional
    @GetMapping("/PayrollPeriod")
    public String periods(Model model) {
        List<PayrollPeriod> list = periods.findAllByOrderByStartDateDesc();
        Map<Long, Long> counts = new HashMap<>();
        for (PayrollPeriod p : list) counts.put(p.getId(), 0L);
        for (Object[] r : entries.countPerPeriod()) counts.put((Long) r[0], (Long) r[1]);
        LocalDate[] next = payroll.suggestNextPeriod();
        model.addAttribute("periods", list);
        model.addAttribute("entryCounts", counts);
        model.addAttribute("nextStart", next[0].toString());
        model.addAttribute("nextEnd", next[1].toString());
        model.addAttribute("nextLabel", next[0].format(DateTimeFormatter.ofPattern("MMM d")) + " – " + next[1].format(DateTimeFormatter.ofPattern("MMM d, yyyy")));
        model.addAttribute("activePage", "payroll-periods");
        return "hr-periods";
    }

    @PostMapping("/PayrollPeriod")
    @Transactional
    public String createPeriod(@RequestParam String startDate, @RequestParam String endDate,
                               @RequestParam(required = false) String notes, RedirectAttributes redirect) {
        LocalDate s = parseDate(startDate), e = parseDate(endDate);
        String problem = checkRange(s, e, null);
        if (problem != null) { redirect.addFlashAttribute("error", problem); return "redirect:/HR/PayrollPeriod"; }
        PayrollPeriod p = new PayrollPeriod();
        p.setStartDate(s); p.setEndDate(e); p.setNotes(cut(notes, 600));
        periods.save(p);
        redirect.addFlashAttribute("success", "Payroll period " + p.getLabel() + " created. Enter the time sheet next.");
        return "redirect:/HR/TimeSheets?period=" + p.getId();
    }

    @PostMapping("/PayrollPeriod/{id}/update")
    @Transactional
    public String updatePeriod(@PathVariable Long id, @RequestParam String startDate, @RequestParam String endDate,
                               @RequestParam(required = false) String notes, RedirectAttributes redirect) {
        PayrollPeriod p = periods.findById(id).orElse(null);
        if (p == null) return "redirect:/HR/PayrollPeriod";
        if (p.isLocked()) { redirect.addFlashAttribute("error", "Unlock the period before changing it."); return "redirect:/HR/PayrollPeriod"; }
        LocalDate s = parseDate(startDate), e = parseDate(endDate);
        String problem = checkRange(s, e, id);
        if (problem != null) { redirect.addFlashAttribute("error", problem); return "redirect:/HR/PayrollPeriod"; }
        p.setStartDate(s); p.setEndDate(e); p.setNotes(cut(notes, 600));
        periods.save(p);
        entries.deleteOutside(id, s, e);
        redirect.addFlashAttribute("success", "Period updated to " + p.getLabel() + ".");
        return "redirect:/HR/PayrollPeriod";
    }

    private String checkRange(LocalDate s, LocalDate e, Long ignoreId) {
        if (s == null || e == null) return "Enter both the start and end dates.";
        if (e.isBefore(s)) return "The end date must be on or after the start date.";
        if (ChronoUnit.DAYS.between(s, e) > 31) return "A payroll period can't be longer than a month.";
        boolean overlap = ignoreId == null
                ? periods.existsByStartDateLessThanEqualAndEndDateGreaterThanEqual(e, s)
                : periods.existsByStartDateLessThanEqualAndEndDateGreaterThanEqualAndIdNot(e, s, ignoreId);
        return overlap ? "Those dates overlap another payroll period." : null;
    }

    @PostMapping("/PayrollPeriod/{id}/delete")
    public String deletePeriod(@PathVariable Long id, RedirectAttributes redirect) {
        periods.findById(id).ifPresent(p -> {
            try { payroll.deletePeriod(p); redirect.addFlashAttribute("success", "Deleted period " + p.getLabel() + "."); }
            catch (IllegalStateException ex) { redirect.addFlashAttribute("error", ex.getMessage()); }
        });
        return "redirect:/HR/PayrollPeriod";
    }

    @PostMapping("/PayrollPeriod/{id}/lock")
    public String lock(@PathVariable Long id, @RequestParam(defaultValue = "paysheet") String back, RedirectAttributes redirect) {
        periods.findById(id).ifPresent(p -> {
            payroll.lock(p, currentUser());
            redirect.addFlashAttribute("success", "Payroll for " + p.getLabel() + " is locked. Payslips are final; loan balances were updated.");
        });
        return back(back, id);
    }

    @PostMapping("/PayrollPeriod/{id}/unlock")
    public String unlock(@PathVariable Long id, @RequestParam(defaultValue = "paysheet") String back, RedirectAttributes redirect) {
        periods.findById(id).ifPresent(p -> {
            try {
                payroll.unlock(p);
                redirect.addFlashAttribute("success", "Payroll for " + p.getLabel() + " is open again. Loan installments were returned to their balances.");
            } catch (IllegalStateException e) { redirect.addFlashAttribute("error", e.getMessage()); }
        });
        return back(back, id);
    }

    private static String back(String where, Long id) {
        return "periods".equals(where) ? "redirect:/HR/PayrollPeriod" : "redirect:/HR/PaySheets?period=" + id;
    }

    // ================================================================ Time sheets

    public record Day(String iso, String dow, int dom, String month, boolean sunday, boolean working, String holiday, String holidayType) {}
    public record Cell(String reg, String ot, String late) {}
    public record TsRow(Employee employee, List<Cell> cells, String nd, String remarks, boolean groupStart, String group) {}

    @Transactional
    @GetMapping("/TimeSheets")
    public String timesheet(@RequestParam(required = false) Long period, @RequestParam(required = false) String group, Model model) {
        payroll.syncFromOrgChart();
        Optional<PayrollPeriod> sel = period == null ? payroll.defaultPeriod() : payroll.period(period);
        model.addAttribute("allPeriods", periods.findAllByOrderByStartDateDesc());
        model.addAttribute("activePage", "timesheets");
        if (sel.isEmpty()) { model.addAttribute("period", null); return "hr-timesheet"; }
        PayrollPeriod p = sel.get();

        Map<LocalDate, Holiday> hol = new HashMap<>();
        for (Holiday h : payroll.holidaysIn(p)) hol.put(h.getDate(), h);
        List<Day> days = new ArrayList<>();
        for (LocalDate d = p.getStartDate(); !d.isAfter(p.getEndDate()); d = d.plusDays(1)) {
            Holiday h = hol.get(d);
            boolean sun = d.getDayOfWeek() == DayOfWeek.SUNDAY;
            days.add(new Day(d.toString(), d.format(DateTimeFormatter.ofPattern("EEE")), d.getDayOfMonth(),
                    d.format(DateTimeFormatter.ofPattern("MMM")), sun, !sun && h == null,
                    h == null ? null : h.getName(), h == null ? null : (h.isRegular() ? "Regular" : "Special")));
        }

        List<TimeEntry> periodEntries = entries.findByPeriodId(p.getId());
        List<TimesheetRow> periodRows = rows.findByPeriodId(p.getId());
        Map<String, TimeEntry> cellMap = new HashMap<>();
        for (TimeEntry t : periodEntries) cellMap.put(t.getEmployeeId() + "|" + t.getWorkDate(), t);
        Map<Long, TimesheetRow> rowMap = new HashMap<>();
        for (TimesheetRow r : periodRows) rowMap.put(r.getEmployeeId(), r);

        List<Employee> list = payroll.employeesFor(periodEntries, periodRows, adjustments.findByPeriodIdOrderByIdAsc(p.getId()));
        List<String> groupsShown = new ArrayList<>();
        for (Employee e : list) { String g = groupName(e); if (!groupsShown.contains(g)) groupsShown.add(g); }
        String filter = blank(group) ? null : group;
        List<TsRow> tsRows = new ArrayList<>();
        String lastGroup = null;
        for (Employee e : list) {
            String g = groupName(e);
            if (filter != null && !filter.equals(g)) continue;
            List<Cell> cells = new ArrayList<>();
            for (Day d : days) {
                TimeEntry t = cellMap.get(e.getId() + "|" + d.iso());
                cells.add(t == null ? new Cell("", "", "")
                        : new Cell(num(t.getRegHours()), num(t.getOtHours()), t.getLateMinutes() == null ? "" : String.valueOf(t.getLateMinutes())));
            }
            TimesheetRow r = rowMap.get(e.getId());
            tsRows.add(new TsRow(e, cells, r == null ? "" : num(r.getNdHours()), r == null || r.getRemarks() == null ? "" : r.getRemarks(),
                    !g.equals(lastGroup), g));
            lastGroup = g;
        }

        var s = payroll.settings();
        model.addAttribute("period", p);
        model.addAttribute("days", days);
        model.addAttribute("rows", tsRows);
        model.addAttribute("groups", groupsShown);
        model.addAttribute("group", filter);
        model.addAttribute("workingDays", days.stream().filter(Day::working).count());
        model.addAttribute("hoursPerDay", s.getHoursPerDay());
        model.addAttribute("graceMinutes", s.getLateGraceMinutes());
        model.addAttribute("colspan", days.size() * 3 + 8);
        return "hr-timesheet";
    }

    @PostMapping(value = "/TimeSheets/{id}/save", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<Map<String, Object>> saveTimesheet(@PathVariable Long id, @RequestBody PayrollService.TimesheetSave body) {
        PayrollPeriod p = periods.findById(id).orElse(null);
        if (p == null) return ResponseEntity.status(404).body(Map.of("ok", false, "message", "Period not found."));
        try {
            int n = payroll.saveTimesheet(p, body);
            return ResponseEntity.ok(Map.of("ok", true, "changed", n));
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(409).body(Map.of("ok", false, "message", ex.getMessage()));
        }
    }

    private static String groupName(Employee e) { return blank(e.getPayGroup()) ? "No group" : e.getPayGroup().trim(); }

    static String num(BigDecimal v) { return v == null ? "" : v.stripTrailingZeros().toPlainString(); }

    // ================================================================ Pay sheets

    public record Totals(BigDecimal basic, BigDecimal ot, BigDecimal sunday, BigDecimal holiday, BigDecimal nd, BigDecimal other,
                         BigDecimal gross, BigDecimal lates, BigDecimal absent, BigDecimal loans, BigDecimal gov,
                         BigDecimal others, BigDecimal deductions, BigDecimal net, int count) {}

    public record GroupBlock(String name, List<PayLine> lines, Totals totals) {}

    @Transactional
    @GetMapping("/PaySheets")
    public String paysheet(@RequestParam(required = false) Long period, @RequestParam(required = false) Long open, Model model) throws Exception {
        payroll.syncFromOrgChart();
        Optional<PayrollPeriod> sel = period == null ? payroll.defaultPeriod() : payroll.period(period);
        model.addAttribute("allPeriods", periods.findAllByOrderByStartDateDesc());
        model.addAttribute("activePage", "paysheets");
        if (sel.isEmpty()) { model.addAttribute("period", null); return "hr-paysheet"; }
        PayrollPeriod p = sel.get();
        List<PayLine> lines = payroll.lines(p);

        Map<String, List<PayLine>> byGroup = new LinkedHashMap<>();
        for (PayLine l : lines) byGroup.computeIfAbsent(blank(l.payGroup) ? "No group" : l.payGroup, k -> new ArrayList<>()).add(l);
        List<GroupBlock> blocks = new ArrayList<>();
        for (var en : byGroup.entrySet()) blocks.add(new GroupBlock(en.getKey(), en.getValue(), totals(en.getValue())));

        Map<String, Object> data = new LinkedHashMap<>();
        for (PayLine l : lines) data.put(String.valueOf(l.employeeId), l);
        List<Map<String, Object>> kinds = new ArrayList<>();
        for (PayItem k : PayItem.values()) kinds.add(Map.of("key", k.name(), "label", k.getLabel(), "earning", k.isEarning()));

        model.addAttribute("period", p);
        model.addAttribute("blocks", blocks);
        model.addAttribute("grand", totals(lines));
        model.addAttribute("linesJson", json.writeValueAsString(data).replace("</", "<\\/"));
        model.addAttribute("kindsJson", json.writeValueAsString(kinds));
        model.addAttribute("payItems", PayItem.values());
        model.addAttribute("openId", open);
        model.addAttribute("warnCount", lines.stream().filter(l -> !l.warnings.isEmpty()).count());
        model.addAttribute("settings", payroll.settings());
        return "hr-paysheet";
    }

    static Totals totals(List<PayLine> lines) {
        BigDecimal basic = BigDecimal.ZERO, ot = BigDecimal.ZERO, sun = BigDecimal.ZERO, hol = BigDecimal.ZERO, nd = BigDecimal.ZERO,
                other = BigDecimal.ZERO, gross = BigDecimal.ZERO, lates = BigDecimal.ZERO, absent = BigDecimal.ZERO,
                loans = BigDecimal.ZERO, gov = BigDecimal.ZERO, others = BigDecimal.ZERO, ded = BigDecimal.ZERO, net = BigDecimal.ZERO;
        for (PayLine l : lines) {
            basic = basic.add(l.basicPay); ot = ot.add(l.otPay); sun = sun.add(l.sunPay).add(l.sunOtPay);
            hol = hol.add(l.specialHolPay).add(l.regularHolPay); nd = nd.add(l.ndPay); other = other.add(l.otherEarnings);
            gross = gross.add(l.grossPay); lates = lates.add(l.lateAmount); absent = absent.add(l.absentAmount);
            loans = loans.add(l.deductionSum("LOAN", "CASH_ADVANCE", "SINKING"));
            gov = gov.add(l.deductionSum("SSS", "PHILHEALTH", "PAGIBIG", "WTAX"));
            others = others.add(l.deductionSum("OTHER_DEDUCTION"));
            ded = ded.add(l.totalDeductions); net = net.add(l.netPay);
        }
        return new Totals(basic, ot, sun, hol, nd, other, gross, lates, absent, loans, gov, others, ded, net, lines.size());
    }

    @PostMapping("/PaySheets/{id}/adjustments")
    @Transactional
    public String addAdjustment(@PathVariable Long id, @RequestParam Long employeeId, @RequestParam String kind,
                                @RequestParam BigDecimal amount, @RequestParam(required = false) String description,
                                RedirectAttributes redirect) {
        PayrollPeriod p = periods.findById(id).orElse(null);
        if (p == null) return "redirect:/HR/PaySheets";
        PayItem k = PayItem.parse(kind);
        if (p.isLocked()) redirect.addFlashAttribute("error", "This period is locked. Unlock it to make changes.");
        else if (k == null || amount == null || amount.signum() <= 0) redirect.addFlashAttribute("error", "Choose a type and enter an amount above zero.");
        else if (!employees.existsById(employeeId)) redirect.addFlashAttribute("error", "That employee no longer exists.");
        else {
            PayAdjustment a = new PayAdjustment();
            a.setPeriodId(id); a.setEmployeeId(employeeId); a.setKind(k.name());
            a.setAmount(PayrollCalculator.money(amount)); a.setDescription(cut(description, 200));
            adjustments.save(a);
            redirect.addFlashAttribute("success", k.getLabel() + " added.");
        }
        return "redirect:/HR/PaySheets?period=" + id + "&open=" + employeeId;
    }

    @PostMapping("/PaySheets/{id}/adjustments/{aid}/delete")
    @Transactional
    public String deleteAdjustment(@PathVariable Long id, @PathVariable Long aid, RedirectAttributes redirect) {
        PayrollPeriod p = periods.findById(id).orElse(null);
        PayAdjustment a = adjustments.findById(aid).orElse(null);
        Long emp = a == null ? null : a.getEmployeeId();
        if (p == null || a == null || !a.getPeriodId().equals(id)) return "redirect:/HR/PaySheets?period=" + id;
        if (p.isLocked()) redirect.addFlashAttribute("error", "This period is locked. Unlock it to make changes.");
        else if (a.getPlanId() != null) redirect.addFlashAttribute("error", "That line comes from a loan schedule. Change it under Loans & Deductions.");
        else { adjustments.delete(a); redirect.addFlashAttribute("success", "Line removed."); }
        return "redirect:/HR/PaySheets?period=" + id + "&open=" + emp;
    }

    @Transactional
    @GetMapping("/PaySheets/{id}/payslips")
    public String payslips(@PathVariable Long id, @RequestParam(required = false) Long employee, Model model) {
        PayrollPeriod p = periods.findById(id).orElseThrow();
        List<PayLine> lines = new ArrayList<>();
        for (PayLine l : payroll.lines(p)) if (employee == null ? l.hasActivity() : l.employeeId.equals(employee)) lines.add(l);
        List<List<PayLine>> pages = new ArrayList<>();
        for (int i = 0; i < lines.size(); i += 4) pages.add(lines.subList(i, Math.min(i + 4, lines.size())));
        model.addAttribute("period", p);
        model.addAttribute("pages", pages);
        model.addAttribute("settings", payroll.settings());
        return "hr-payslips";
    }

    @Transactional
    @GetMapping("/PaySheets/{id}/signatures")
    public String signatures(@PathVariable Long id, Model model) {
        PayrollPeriod p = periods.findById(id).orElseThrow();
        List<PayLine> lines = new ArrayList<>();
        for (PayLine l : payroll.lines(p)) if (l.hasActivity()) lines.add(l);
        Map<String, List<PayLine>> byGroup = new LinkedHashMap<>();
        for (PayLine l : lines) byGroup.computeIfAbsent(blank(l.payGroup) ? "No group" : l.payGroup, k -> new ArrayList<>()).add(l);
        List<GroupBlock> blocks = new ArrayList<>();
        for (var en : byGroup.entrySet()) blocks.add(new GroupBlock(en.getKey(), en.getValue(), totals(en.getValue())));
        model.addAttribute("period", p);
        model.addAttribute("blocks", blocks);
        model.addAttribute("grand", totals(lines));
        model.addAttribute("settings", payroll.settings());
        return "hr-signatures";
    }

    @Transactional
    @GetMapping("/PaySheets/{id}/export")
    public ResponseEntity<byte[]> export(@PathVariable Long id) throws Exception {
        PayrollPeriod p = periods.findById(id).orElseThrow();
        byte[] bytes = exporter.export(p, payroll.lines(p), payroll.settings());
        String name = "Payroll_" + p.getStartDate() + "_to_" + p.getEndDate() + ".xlsx";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + name + "\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(bytes);
    }

    // ================================================================ Pay rules

    @Transactional
    @GetMapping("/PayrollSettings")
    public String settings(Model model) {
        model.addAttribute("settings", payroll.settings());
        model.addAttribute("activePage", "paysheets");
        return "hr-settings";
    }

    @PostMapping("/PayrollSettings")
    @Transactional
    public String saveSettings(@RequestParam BigDecimal otMultiplier, @RequestParam BigDecimal sundayMultiplier,
                               @RequestParam BigDecimal sundayOtMultiplier, @RequestParam BigDecimal specialHolidayPremium,
                               @RequestParam BigDecimal regularHolidayPremium, @RequestParam BigDecimal nightDiffRatePercent,
                               @RequestParam BigDecimal lateMultiplier, @RequestParam Integer lateGraceMinutes,
                               @RequestParam Integer hoursPerDay, @RequestParam Integer workDaysPerYear,
                               @RequestParam(required = false) String companyName,
                               @RequestParam(required = false) String shiftStart, @RequestParam(required = false) String shiftEnd,
                               @RequestParam(required = false) String breakStart, @RequestParam(required = false) String breakEnd,
                               @RequestParam(required = false) Integer otBlockMinutes,
                               @RequestParam(defaultValue = "false") boolean otFromLogs,
                               @RequestParam(defaultValue = "false") boolean withholdTax,
                               @RequestParam(defaultValue = "false") boolean autoContributions, RedirectAttributes redirect) {
        PayrollSettings s = payroll.settings();
        s.setOtMultiplier(nonNeg(otMultiplier)); s.setSundayMultiplier(nonNeg(sundayMultiplier));
        s.setSundayOtMultiplier(nonNeg(sundayOtMultiplier));
        s.setSpecialHolidayPremium(nonNeg(specialHolidayPremium).divide(BigDecimal.valueOf(100), 3, java.math.RoundingMode.HALF_UP));
        s.setRegularHolidayPremium(nonNeg(regularHolidayPremium).divide(BigDecimal.valueOf(100), 3, java.math.RoundingMode.HALF_UP));
        s.setNightDiffRate(nonNeg(nightDiffRatePercent).divide(BigDecimal.valueOf(100), 3, java.math.RoundingMode.HALF_UP));
        s.setLateMultiplier(nonNeg(lateMultiplier));
        s.setLateGraceMinutes(Math.max(0, Math.min(120, lateGraceMinutes)));
        s.setHoursPerDay(Math.max(1, Math.min(24, hoursPerDay)));
        s.setWorkDaysPerYear(Math.max(200, Math.min(366, workDaysPerYear)));
        if (!blank(companyName)) s.setCompanyName(cut(companyName, 160));
        java.time.LocalTime ss = time(shiftStart), se = time(shiftEnd), bs = time(breakStart), be = time(breakEnd);
        if (ss != null && se != null && se.isAfter(ss)) { s.setShiftStart(ss); s.setShiftEnd(se); }
        if (bs != null && be != null && !be.isBefore(bs)) { s.setBreakStart(bs); s.setBreakEnd(be); }
        if (otBlockMinutes != null) s.setOtBlockMinutes(Math.max(1, Math.min(120, otBlockMinutes)));
        s.setOtFromLogs(otFromLogs);
        s.setWithholdTax(withholdTax);
        s.setAutoContributions(autoContributions);
        settingsRepo.save(s);
        redirect.addFlashAttribute("success", "Pay rules saved. Open pay sheets use them right away; locked ones keep their figures.");
        return "redirect:/HR/PayrollSettings";
    }

    private static java.time.LocalTime time(String v) {
        try { return blank(v) ? null : java.time.LocalTime.parse(v.trim()); } catch (Exception e) { return null; }
    }

    private static BigDecimal nonNeg(BigDecimal v) { return v == null || v.signum() < 0 ? BigDecimal.ZERO : v; }

    // ================================================================ Holidays

    @Transactional
    @GetMapping("/Holidays")
    public String holidays(@RequestParam(required = false) Integer year, Model model) {
        int y = year == null ? LocalDate.now().getYear() : year;
        model.addAttribute("year", y);
        model.addAttribute("holidays", holidays.findByDateBetweenOrderByDateAsc(LocalDate.of(y, 1, 1), LocalDate.of(y, 12, 31)));
        model.addAttribute("activePage", "holidays");
        return "hr-holidays";
    }

    @PostMapping("/Holidays")
    @Transactional
    public String addHoliday(@RequestParam String date, @RequestParam String name, @RequestParam(defaultValue = "REGULAR") String type,
                             RedirectAttributes redirect) {
        LocalDate d = parseDate(date);
        int y = d == null ? LocalDate.now().getYear() : d.getYear();
        if (d == null || blank(name)) redirect.addFlashAttribute("error", "Enter the date and the holiday name.");
        else if (holidays.existsByDate(d)) redirect.addFlashAttribute("error", "There is already a holiday on " + d + ".");
        else {
            Holiday h = new Holiday();
            h.setDate(d); h.setName(cut(name, 160)); h.setType("SPECIAL".equalsIgnoreCase(type) ? "SPECIAL" : "REGULAR");
            holidays.save(h);
            redirect.addFlashAttribute("success", "Added " + h.getName() + ".");
        }
        return "redirect:/HR/Holidays?year=" + y;
    }

    @PostMapping("/Holidays/{id}/delete")
    @Transactional
    public String deleteHoliday(@PathVariable Long id, RedirectAttributes redirect) {
        int y = LocalDate.now().getYear();
        Holiday h = holidays.findById(id).orElse(null);
        if (h != null) { y = h.getDate().getYear(); holidays.delete(h); redirect.addFlashAttribute("success", "Removed " + h.getName() + "."); }
        return "redirect:/HR/Holidays?year=" + y;
    }

    // ================================================================ Loans & deductions

    public record PlanRow(DeductionPlan plan, Employee employee, String kindLabel, BigDecimal paid, int installments) {}

    @Transactional
    @GetMapping("/Loans")
    public String loans(Model model) {
        payroll.syncFromOrgChart();
        Map<Long, Employee> emp = new HashMap<>();
        for (Employee e : employees.findAll()) emp.put(e.getId(), e);
        List<PlanRow> list = new ArrayList<>();
        Map<Long, List<PayAdjustment>> posted = new HashMap<>();
        for (PayAdjustment a : adjustments.findByPlanIdIsNotNull()) posted.computeIfAbsent(a.getPlanId(), k -> new ArrayList<>()).add(a);
        for (DeductionPlan d : plans.findAllByOrderByActiveDescStartDateDesc()) {
            BigDecimal paid = BigDecimal.ZERO; int n = 0;
            for (PayAdjustment a : posted.getOrDefault(d.getId(), List.of())) { paid = paid.add(a.getAmount()); n++; }
            PayItem k = PayItem.parse(d.getKind());
            list.add(new PlanRow(d, emp.get(d.getEmployeeId()), k == null ? d.getKind() : k.getLabel(), paid, n));
        }
        List<PayItem> kinds = new ArrayList<>();
        for (PayItem k : PayItem.values()) if (k.isPlanKind()) kinds.add(k);
        model.addAttribute("plans", list);
        model.addAttribute("employees", payroll.sorted(employees.findByActiveTrue()));
        model.addAttribute("kinds", kinds);
        model.addAttribute("today", LocalDate.now().toString());
        model.addAttribute("activePage", "loans");
        return "hr-loans";
    }

    @PostMapping("/Loans")
    @Transactional
    public String saveLoan(@RequestParam(required = false) Long id, @RequestParam Long employeeId, @RequestParam String kind,
                           @RequestParam(required = false) String description, @RequestParam(required = false) BigDecimal totalAmount,
                           @RequestParam BigDecimal perCutoff, @RequestParam String startDate, RedirectAttributes redirect) {
        PayItem k = PayItem.parse(kind);
        LocalDate start = parseDate(startDate);
        if (k == null || !k.isPlanKind() || perCutoff == null || perCutoff.signum() <= 0 || start == null || !employees.existsById(employeeId)) {
            redirect.addFlashAttribute("error", "Choose the employee and type, and enter the amount per cut-off and the start date.");
            return "redirect:/HR/Loans";
        }
        DeductionPlan d = id == null ? new DeductionPlan() : plans.findById(id).orElse(null);
        if (d == null) return "redirect:/HR/Loans";
        BigDecimal total = totalAmount == null || totalAmount.signum() <= 0 ? null : PayrollCalculator.money(totalAmount);
        BigDecimal paid = BigDecimal.ZERO;
        if (d.getId() != null) for (PayAdjustment a : adjustments.findByPlanIdOrderByPeriodIdAsc(d.getId())) paid = paid.add(a.getAmount());
        if (total != null && total.compareTo(paid) < 0) {
            redirect.addFlashAttribute("error", "The total can't be less than the ₱" + paid.toPlainString() + " already deducted.");
            return "redirect:/HR/Loans";
        }
        d.setEmployeeId(employeeId); d.setKind(k.name()); d.setDescription(cut(description, 200));
        d.setTotalAmount(total); d.setPerCutoff(PayrollCalculator.money(perCutoff)); d.setStartDate(start);
        d.setBalance(total == null ? null : total.subtract(paid));
        d.setActive(total == null || d.getBalance().signum() > 0);
        plans.save(d);
        redirect.addFlashAttribute("success", (id == null ? "Added " : "Saved ") + k.getLabel().toLowerCase() + " schedule.");
        return "redirect:/HR/Loans";
    }

    @PostMapping("/Loans/{id}/toggle")
    @Transactional
    public String toggleLoan(@PathVariable Long id, RedirectAttributes redirect) {
        plans.findById(id).ifPresent(d -> {
            if (!d.isActive() && !d.isOpenEnded() && (d.getBalance() == null || d.getBalance().signum() <= 0)) {
                redirect.addFlashAttribute("error", "That schedule is fully paid.");
                return;
            }
            d.setActive(!d.isActive());
            plans.save(d);
            redirect.addFlashAttribute("success", d.isActive() ? "Deductions resumed." : "Deductions stopped. Nothing more will be taken until you resume it.");
        });
        return "redirect:/HR/Loans";
    }

    @PostMapping("/Loans/{id}/delete")
    @Transactional
    public String deleteLoan(@PathVariable Long id, RedirectAttributes redirect) {
        plans.findById(id).ifPresent(d -> {
            if (adjustments.existsByPlanId(id)) redirect.addFlashAttribute("error", "Installments were already deducted from this schedule. Stop it instead of deleting it.");
            else { plans.delete(d); redirect.addFlashAttribute("success", "Schedule deleted."); }
        });
        return "redirect:/HR/Loans";
    }
}
