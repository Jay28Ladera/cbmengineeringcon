package com.asmsi.admin_system.payroll;

import com.asmsi.admin_system.entity.*;
import com.asmsi.admin_system.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/** Employees, periods, time sheets and payroll runs for the HR module. */
@Service
public class PayrollService {

    /** Payroll groups in the order the Excel MASTER sheet lists them. */
    public static final List<String> DEFAULT_GROUPS = List.of(
            "Cebu Office", "Cebu Site", "Cebu Store", "Manila Office", "Manila Site");

    private final EmployeeRepository employees;
    private final OrganizationNodeRepository nodes;
    private final PayrollPeriodRepository periods;
    private final TimeEntryRepository entries;
    private final TimesheetRowRepository rows;
    private final PayAdjustmentRepository adjustments;
    private final HolidayRepository holidays;
    private final DeductionPlanRepository plans;
    private final PayrollSettingsRepository settingsRepo;
    private final PayrollResultRepository results;
    private final PayrollCalculator calculator;
    private final ObjectMapper json;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.asmsi.admin_system.accounting.PayrollPostingRepository payrollPostings;

    public PayrollService(EmployeeRepository employees, OrganizationNodeRepository nodes, PayrollPeriodRepository periods,
                          TimeEntryRepository entries, TimesheetRowRepository rows, PayAdjustmentRepository adjustments,
                          HolidayRepository holidays, DeductionPlanRepository plans, PayrollSettingsRepository settingsRepo,
                          PayrollResultRepository results, PayrollCalculator calculator, ObjectMapper json,
                          org.springframework.jdbc.core.JdbcTemplate jdbc) {
        this.employees = employees; this.nodes = nodes; this.periods = periods; this.entries = entries; this.rows = rows;
        this.adjustments = adjustments; this.holidays = holidays; this.plans = plans; this.settingsRepo = settingsRepo;
        this.results = results; this.calculator = calculator; this.json = json; this.jdbc = jdbc;
    }

    // ---------------------------------------------------------------- settings

    @Transactional
    public PayrollSettings settings() {
        return settingsRepo.findById(1L).orElseGet(() -> settingsRepo.save(new PayrollSettings()));
    }

    // ---------------------------------------------------------------- employees

    /**
     * Brings HR records in step with the organizational chart: every named person on the chart has a
     * record whose name, position, department and employee no. follow their box. Someone added in HR
     * first is linked (not duplicated) when they later appear on the chart with the same employee no. or name.
     * Resigned people become inactive; a deleted or emptied box leaves the record in HR, unlinked.
     */
    @Transactional
    public void syncFromOrgChart() {
        List<OrganizationNode> all = nodes.findAll();
        Map<Long, OrganizationNode> byId = new HashMap<>();
        for (OrganizationNode n : all) byId.put(n.getId(), n);
        List<Employee> unlinked = new ArrayList<>();
        Map<Long, Employee> byNode = new HashMap<>();
        for (Employee e : employees.findAll()) {
            if (e.getOrgNodeId() == null) { unlinked.add(e); continue; }
            OrganizationNode n = byId.get(e.getOrgNodeId());
            if (n == null || blank(n.getPersonName())) { e.setOrgNodeId(null); employees.save(e); unlinked.add(e); }
            else byNode.put(n.getId(), e);
        }
        for (OrganizationNode n : all) {
            if (blank(n.getPersonName())) continue;
            Employee e = byNode.get(n.getId());
            if (e == null) e = claim(unlinked, n);
            boolean isNew = e == null;
            if (isNew) {
                e = new Employee();
                e.setPayGroup(guessGroup(n.getSiteAssignment()));
                e.setEmploymentStatus("Regular");
                e.setStatusCode("R");
            }
            boolean changed = isNew || e.getOrgNodeId() == null
                    || !Objects.equals(e.getFullName(), n.getPersonName().trim())
                    || !Objects.equals(e.getPosition(), n.getTitle())
                    || !Objects.equals(e.getDepartment(), n.getDepartment())
                    || (!blank(n.getEmployeeId()) && !Objects.equals(e.getEmployeeNo(), n.getEmployeeId()))
                    || (n.getDateHired() != null && !n.getDateHired().equals(e.getDateHired()))
                    || (n.isResigned() && e.isActive());
            if (!changed) continue;
            e.setOrgNodeId(n.getId());
            e.setFullName(n.getPersonName().trim());
            e.setPosition(n.getTitle());
            e.setDepartment(n.getDepartment());
            if (!blank(n.getEmployeeId())) e.setEmployeeNo(n.getEmployeeId());
            if (n.getDateHired() != null) e.setDateHired(n.getDateHired());
            if (n.isResigned()) e.setActive(false);
            employees.save(e);
        }
    }

    private Employee claim(List<Employee> unlinked, OrganizationNode n) {
        Employee match = null;
        if (!blank(n.getEmployeeId())) {
            for (Employee e : unlinked) if (n.getEmployeeId().equalsIgnoreCase(trim(e.getEmployeeNo()))) { match = e; break; }
        }
        if (match == null) {
            String name = normal(n.getPersonName());
            for (Employee e : unlinked) if (normal(e.getFullName()).equals(name)) { match = e; break; }
        }
        if (match != null) unlinked.remove(match);
        return match;
    }

    private static String guessGroup(String site) {
        if (blank(site)) return null;
        String s = site.toLowerCase();
        for (String g : DEFAULT_GROUPS) if (s.contains(g.toLowerCase())) return g;
        return null;
    }

    /** Employees sorted as on the pay sheet: by group, then daily before monthly, then name. */
    public List<Employee> sorted(Collection<Employee> list) {
        List<Employee> out = new ArrayList<>(list);
        out.sort(Comparator.comparingInt((Employee e) -> groupRank(e.getPayGroup()))
                .thenComparing(e -> e.getPayGroup() == null ? "~" : e.getPayGroup().toLowerCase())
                .thenComparing(e -> e.isMonthly() ? 1 : 0)
                .thenComparing(e -> e.getFullName() == null ? "" : e.getFullName().toLowerCase()));
        return out;
    }

    public static int groupRank(String group) {
        if (group == null || group.isBlank()) return 1000;
        int i = DEFAULT_GROUPS.indexOf(group.trim());
        return i < 0 ? 500 : i;
    }

    /** Groups in use plus the defaults, in pay-sheet order. */
    public List<String> groups() {
        TreeSet<String> set = new TreeSet<>(Comparator.comparingInt(PayrollService::groupRank).thenComparing((String g) -> g.toLowerCase()));
        set.addAll(DEFAULT_GROUPS);
        for (Employee e : employees.findAll()) if (!blank(e.getPayGroup())) set.add(e.getPayGroup().trim());
        return new ArrayList<>(set);
    }

    // ---------------------------------------------------------------- periods

    /** The cut-off after the latest one (26–10 / 11–25), or the current one if there are none yet. */
    public LocalDate[] suggestNextPeriod() {
        Optional<PayrollPeriod> last = periods.findFirstByOrderByEndDateDesc();
        LocalDate start;
        if (last.isPresent()) start = last.get().getEndDate().plusDays(1);
        else {
            LocalDate t = LocalDate.now();
            if (t.getDayOfMonth() >= 26) start = t.withDayOfMonth(26);
            else if (t.getDayOfMonth() <= 10) start = t.minusMonths(1).withDayOfMonth(26);
            else start = t.withDayOfMonth(11);
        }
        LocalDate end;
        if (start.getDayOfMonth() == 26) end = start.plusMonths(1).withDayOfMonth(10);
        else if (start.getDayOfMonth() == 11) end = start.withDayOfMonth(25);
        else end = start.plusDays(14);
        return new LocalDate[]{start, end};
    }

    public Optional<PayrollPeriod> period(Long id) { return id == null ? Optional.empty() : periods.findById(id); }

    /** Period to open by default: the latest open one, otherwise the latest. */
    public Optional<PayrollPeriod> defaultPeriod() {
        List<PayrollPeriod> all = periods.findAllByOrderByStartDateDesc();
        for (PayrollPeriod p : all) if (!p.isLocked()) return Optional.of(p);
        return all.stream().findFirst();
    }

    @Transactional
    public void deletePeriod(PayrollPeriod p) {
        if (p.isLocked()) throw new IllegalStateException("Unlock the period before deleting it.");
        entries.deleteByPeriod(p.getId());
        rows.deleteByPeriod(p.getId());
        adjustments.deleteByPeriod(p.getId());
        results.deleteByPeriod(p.getId());
        periods.delete(p);
    }

    // ---------------------------------------------------------------- time sheet

    public record CellInput(Long e, String d, BigDecimal reg, BigDecimal ot, Integer late) {}
    public record RowInput(Long e, BigDecimal nd, String remarks) {}
    public record TimesheetSave(List<CellInput> cells, List<RowInput> rows) {}

    /**
     * Saves time sheet cells. Only cells that actually changed are written, in one batched
     * statement (a full cut-off for 150 people is ~2,000 cells; one query each would take
     * seconds when the database is in another region).
     */
    @Transactional
    public int saveTimesheet(PayrollPeriod p, TimesheetSave data) {
        if (p.isLocked()) throw new IllegalStateException("This period is locked.");
        Map<String, TimeEntry> existing = new HashMap<>();
        for (TimeEntry t : entries.findByPeriodId(p.getId())) existing.put(t.getEmployeeId() + "|" + t.getWorkDate(), t);
        Set<Long> valid = new HashSet<>();
        for (Employee e : employees.findAll()) valid.add(e.getId());
        int changed = 0;
        List<Object[]> upserts = new ArrayList<>();
        List<Object[]> deletes = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        if (data.cells() != null) for (CellInput c : data.cells()) {
            if (c.e() == null || !valid.contains(c.e()) || c.d() == null) continue;
            LocalDate d;
            try { d = LocalDate.parse(c.d()); } catch (Exception ex) { continue; }
            if (d.isBefore(p.getStartDate()) || d.isAfter(p.getEndDate())) continue;
            String key = c.e() + "|" + d;
            if (!seen.add(key)) continue;
            BigDecimal reg = zeroToNull(clamp(c.reg(), 24)), ot = zeroToNull(clamp(c.ot(), 24));
            Integer late = c.late() == null ? null : Math.max(0, Math.min(c.late(), 1440));
            if (late != null && late == 0) late = null;
            TimeEntry t = existing.get(key);
            if (reg == null && ot == null && late == null) {
                if (t != null) { deletes.add(new Object[]{t.getId()}); changed++; }
                continue;
            }
            if (t != null && same(t.getRegHours(), reg) && same(t.getOtHours(), ot) && Objects.equals(t.getLateMinutes(), late)) continue;
            upserts.add(new Object[]{p.getId(), c.e(), java.sql.Date.valueOf(d), reg, ot, late});
            changed++;
        }
        if (!deletes.isEmpty()) jdbc.batchUpdate("delete from hr_time_entry where id = ?", deletes);
        if (!upserts.isEmpty()) jdbc.batchUpdate(
                "insert into hr_time_entry (period_id, employee_id, work_date, reg_hours, ot_hours, late_minutes) values (?, ?, ?, ?, ?, ?) " +
                "on conflict (period_id, employee_id, work_date) do update set reg_hours = excluded.reg_hours, " +
                "ot_hours = excluded.ot_hours, late_minutes = excluded.late_minutes", upserts);
        if (data.rows() != null) {
            Map<Long, TimesheetRow> rowBy = new HashMap<>();
            for (TimesheetRow r : rows.findByPeriodId(p.getId())) rowBy.put(r.getEmployeeId(), r);
            for (RowInput r : data.rows()) {
                if (r.e() == null || !valid.contains(r.e())) continue;
                BigDecimal nd = clamp(r.nd(), 400);
                String remarks = blank(r.remarks()) ? null : r.remarks().trim();
                if (remarks != null && remarks.length() > 600) remarks = remarks.substring(0, 600);
                TimesheetRow row = rowBy.get(r.e());
                if ((nd == null || nd.signum() == 0) && remarks == null) { if (row != null) { rows.delete(row); changed++; } continue; }
                if (row != null && same(row.getNdHours(), nd) && Objects.equals(row.getRemarks(), remarks)) continue;
                if (row == null) { row = new TimesheetRow(); row.setPeriodId(p.getId()); row.setEmployeeId(r.e()); }
                row.setNdHours(nd); row.setRemarks(remarks);
                rows.save(row);
                changed++;
            }
        }
        return changed;
    }

    private static BigDecimal zeroToNull(BigDecimal v) { return v == null || v.signum() == 0 ? null : v; }
    private static boolean same(BigDecimal a, BigDecimal b) { return a == null ? b == null : b != null && a.compareTo(b) == 0; }

    private static BigDecimal clamp(BigDecimal v, int max) {
        if (v == null) return null;
        if (v.signum() < 0) return BigDecimal.ZERO;
        return v.min(BigDecimal.valueOf(max));
    }

    // ---------------------------------------------------------------- payroll

    /** Employees that belong on the period's pay sheet: everyone active plus anyone with entries in it. */
    public List<Employee> employeesFor(PayrollPeriod p) {
        return employeesFor(entries.findByPeriodId(p.getId()), rows.findByPeriodId(p.getId()),
                adjustments.findByPeriodIdOrderByIdAsc(p.getId()));
    }

    /** Same, with the period's records already loaded (saves three queries). */
    public List<Employee> employeesFor(List<TimeEntry> periodEntries, List<TimesheetRow> periodRows, List<PayAdjustment> periodAdj) {
        Set<Long> withData = new HashSet<>();
        for (TimeEntry t : periodEntries) withData.add(t.getEmployeeId());
        for (PayAdjustment a : periodAdj) withData.add(a.getEmployeeId());
        for (TimesheetRow r : periodRows) withData.add(r.getEmployeeId());
        List<Employee> list = new ArrayList<>();
        for (Employee e : employees.findAll()) if (e.isActive() || withData.contains(e.getId())) list.add(e);
        return sorted(list);
    }

    public List<Holiday> holidaysIn(PayrollPeriod p) {
        return holidays.findByDateBetweenOrderByDateAsc(p.getStartDate(), p.getEndDate());
    }

    /** Pay lines for a period: frozen results when locked, a live computation while open. */
    @Transactional(readOnly = true)
    public List<PayLine> lines(PayrollPeriod p) {
        if (p.isLocked()) {
            List<PayrollResult> saved = results.findByPeriodId(p.getId());
            if (!saved.isEmpty()) {
                Map<Long, PayLine> byEmp = new HashMap<>();
                for (PayrollResult r : saved) {
                    try { byEmp.put(r.getEmployeeId(), json.readValue(r.getLineJson(), PayLine.class)); }
                    catch (Exception ex) { /* skip unreadable line */ }
                }
                List<PayLine> out = new ArrayList<>();
                Map<Long, Employee> all = new HashMap<>();
                for (Employee e : employees.findAllById(byEmp.keySet())) all.put(e.getId(), e);
                for (Employee e : sorted(all.values())) out.add(byEmp.get(e.getId()));
                for (Map.Entry<Long, PayLine> en : byEmp.entrySet()) if (!all.containsKey(en.getKey())) out.add(en.getValue());
                return out;
            }
        }
        return compute(p);
    }

    private List<PayLine> compute(PayrollPeriod p) {
        List<TimeEntry> e = entries.findByPeriodId(p.getId());
        List<TimesheetRow> r = rows.findByPeriodId(p.getId());
        List<PayAdjustment> a = adjustments.findByPeriodIdOrderByIdAsc(p.getId());
        return calculator.compute(new PayrollCalculator.Input(p, employeesFor(e, r, a), e, r, a, holidaysIn(p),
                plans.findByActiveTrue(), settings()));
    }

    /**
     * Finalizes a period: posts this cut-off's loan / advance installments (reducing their balances)
     * and saves every pay line so the payslips no longer change.
     */
    @Transactional
    public void lock(PayrollPeriod p, String by) {
        if (p.isLocked()) return;
        // Post the installments the open pay sheet shows (none for anyone without pay this cut-off)
        Map<Long, DeductionPlan> planById = new HashMap<>();
        for (DeductionPlan d : plans.findByActiveTrue()) planById.put(d.getId(), d);
        for (PayLine l : compute(p)) {
            for (PayLine.Item it : l.deductions) {
                if (it.planId == null || it.adjustmentId != null) continue;
                DeductionPlan d = planById.get(it.planId);
                if (d == null) continue;
                PayAdjustment a = new PayAdjustment();
                a.setPeriodId(p.getId()); a.setEmployeeId(d.getEmployeeId()); a.setKind(d.getKind());
                a.setAmount(it.amount); a.setPlanId(d.getId());
                String desc = blank(d.getDescription()) ? "" : d.getDescription();
                if (!d.isOpenEnded()) {
                    d.setBalance(PayrollCalculator.money(d.getBalance().subtract(it.amount)));
                    desc = (desc.isEmpty() ? "" : desc + " · ") + "bal. " + d.getBalance().toPlainString();
                    if (d.getBalance().signum() <= 0) d.setActive(false);
                    plans.save(d);
                }
                a.setDescription(desc.isEmpty() ? null : desc);
                adjustments.save(a);
            }
        }
        adjustments.flush();
        results.deleteByPeriod(p.getId());
        p.setStatus("LOCKED");
        List<Object[]> rowsOut = new ArrayList<>();
        for (PayLine l : compute(p)) {
            try { rowsOut.add(new Object[]{p.getId(), l.employeeId, l.netPay, json.writeValueAsString(l)}); }
            catch (Exception ex) { throw new IllegalStateException(ex); }
        }
        if (!rowsOut.isEmpty()) jdbc.batchUpdate("insert into hr_payroll_result (period_id, employee_id, net_pay, line_json) values (?, ?, ?, ?)", rowsOut);
        p.setLockedAt(LocalDateTime.now());
        p.setLockedBy(by);
        periods.save(p);
    }

    /** Reopens a period: removes the saved pay lines and returns posted installments to their balances. */
    @Transactional
    public void unlock(PayrollPeriod p) {
        if (!p.isLocked()) return;
        if (payrollPostings != null && payrollPostings.findFirstByPeriodIdAndStatusNot(p.getId(), "VOID").isPresent())
            throw new IllegalStateException("This payroll is posted in Accounting. Unpost it there (Accounting › Payroll) before unlocking.");
        for (PayAdjustment a : adjustments.findByPeriodIdAndPlanIdIsNotNull(p.getId())) {
            plans.findById(a.getPlanId()).ifPresent(d -> {
                if (!d.isOpenEnded()) {
                    d.setBalance(PayrollCalculator.money(PayrollCalculator.nz(d.getBalance()).add(a.getAmount())));
                    if (d.getBalance().signum() > 0) d.setActive(true);
                    plans.save(d);
                }
            });
            adjustments.delete(a);
        }
        results.deleteByPeriod(p.getId());
        p.setStatus("OPEN");
        p.setLockedAt(null);
        p.setLockedBy(null);
        periods.save(p);
    }

    // ---------------------------------------------------------------- helpers

    static boolean blank(String s) { return s == null || s.isBlank(); }
    static String trim(String s) { return s == null ? "" : s.trim(); }
    static String normal(String s) { return s == null ? "" : s.trim().replaceAll("\\s+", " ").toLowerCase(); }
}
