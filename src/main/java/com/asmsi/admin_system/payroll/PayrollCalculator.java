package com.asmsi.admin_system.payroll;

import com.asmsi.admin_system.entity.*;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Gross-to-net computation, following the formulas of the Excel PAYLIST sheet:
 * <pre>
 *   rate/hr          = rate/day ÷ 8
 *   basic (daily)    = rate/day × days worked (regular hours ÷ 8, Sundays excluded)
 *   basic (monthly)  = monthly rate ÷ 2, less absences × rate/day
 *   overtime         = rate/hr × 1.25 × OT hours
 *   Sunday           = rate/day × 1.3 × Sunday days;  Sunday OT = rate/hr × 1.3 × hours
 *   special holiday  = rate/day × 30% per day worked; regular holiday = rate/day × 100% extra
 *   night diff       = rate/hr × 10% × ND hours
 *   lates            = minutes × (rate/hr ÷ 60) × 1.3
 * </pre>
 * All multipliers come from {@link PayrollSettings}.
 */
@Component
public class PayrollCalculator {

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    public record Input(PayrollPeriod period, List<Employee> employees, List<TimeEntry> entries,
                        List<TimesheetRow> rows, List<PayAdjustment> adjustments, List<Holiday> holidays,
                        List<DeductionPlan> plans, PayrollSettings settings) {}

    public List<PayLine> compute(Input in) {
        PayrollSettings s = in.settings();
        PayrollPeriod p = in.period();
        BigDecimal hpd = BigDecimal.valueOf(s.getHoursPerDay() == null || s.getHoursPerDay() <= 0 ? 8 : s.getHoursPerDay());
        int grace = s.getLateGraceMinutes() == null ? 0 : Math.max(0, s.getLateGraceMinutes());

        Map<LocalDate, Holiday> holidays = new HashMap<>();
        for (Holiday h : in.holidays()) holidays.put(h.getDate(), h);
        List<LocalDate> workingDates = workingDates(p, holidays.keySet());

        Map<Long, List<TimeEntry>> entriesBy = new HashMap<>();
        for (TimeEntry t : in.entries()) entriesBy.computeIfAbsent(t.getEmployeeId(), k -> new ArrayList<>()).add(t);
        Map<Long, TimesheetRow> rowBy = new HashMap<>();
        for (TimesheetRow r : in.rows()) rowBy.put(r.getEmployeeId(), r);
        Map<Long, List<PayAdjustment>> adjBy = new HashMap<>();
        for (PayAdjustment a : in.adjustments()) adjBy.computeIfAbsent(a.getEmployeeId(), k -> new ArrayList<>()).add(a);
        Map<Long, List<DeductionPlan>> plansBy = new HashMap<>();
        Set<Long> postedPlans = new HashSet<>();
        for (PayAdjustment a : in.adjustments()) if (a.getPlanId() != null) postedPlans.add(a.getPlanId());
        if (!p.isLocked()) {
            for (DeductionPlan d : in.plans()) {
                if (!d.isActive() || d.getStartDate().isAfter(p.getEndDate()) || postedPlans.contains(d.getId())) continue;
                if (!d.isOpenEnded() && (d.getBalance() == null || d.getBalance().signum() <= 0)) continue;
                plansBy.computeIfAbsent(d.getEmployeeId(), k -> new ArrayList<>()).add(d);
            }
        }

        String month = p.getEndDate().format(DateTimeFormatter.ofPattern("MMMM yyyy"));
        List<PayLine> lines = new ArrayList<>();
        for (Employee e : in.employees()) {
            PayLine l = new PayLine();
            l.employeeId = e.getId();
            l.employeeNo = e.getEmployeeNo();
            l.name = e.getFullName();
            l.position = e.getPosition();
            l.payGroup = e.getPayGroup();
            l.statusCode = e.getStatusCode();
            l.rateType = e.isMonthly() ? "MONTHLY" : "DAILY";
            l.monthlyRate = e.getMonthlyRate();

            BigDecimal day = dailyRate(e, s);
            BigDecimal hr = day.divide(hpd, 6, RoundingMode.HALF_UP);
            l.dailyRate = money(day);
            l.hourlyRate = money(hr);
            if (day.signum() == 0) l.warnings.add("No rate set");

            BigDecimal regHours = ZERO, otHours = ZERO, sunHours = ZERO, sunOt = ZERO, specHours = ZERO, regHolHours = ZERO;
            int lateMin = 0;
            Map<LocalDate, BigDecimal> regByDate = new HashMap<>();
            for (TimeEntry t : entriesBy.getOrDefault(e.getId(), List.of())) {
                LocalDate d = t.getWorkDate();
                BigDecimal reg = nz(t.getRegHours()), ot = nz(t.getOtHours());
                boolean sunday = d.getDayOfWeek() == DayOfWeek.SUNDAY;
                if (sunday) { sunHours = sunHours.add(reg); sunOt = sunOt.add(ot); }
                else { regHours = regHours.add(reg); otHours = otHours.add(ot); regByDate.put(d, reg); }
                Holiday h = holidays.get(d);
                if (h != null) { if (h.isRegular()) regHolHours = regHolHours.add(reg); else specHours = specHours.add(reg); }
                if (t.getLateMinutes() != null) lateMin += Math.max(0, t.getLateMinutes() - grace);
            }

            l.regDays = regHours.divide(hpd, 2, RoundingMode.HALF_UP);
            BigDecimal absent = ZERO;
            for (LocalDate d : workingDates) {
                BigDecimal worked = regByDate.getOrDefault(d, ZERO);
                if (worked.compareTo(hpd) < 0) absent = absent.add(hpd.subtract(worked));
            }
            l.absentDays = absent.divide(hpd, 2, RoundingMode.HALF_UP);

            if (e.isMonthly()) {
                l.basicPay = money(nz(e.getMonthlyRate()).divide(BigDecimal.valueOf(2), 6, RoundingMode.HALF_UP));
                l.absentAmount = money(day.multiply(l.absentDays));
            } else {
                l.basicPay = money(day.multiply(regHours).divide(hpd, 6, RoundingMode.HALF_UP));
            }
            l.otHours = otHours;
            l.otPay = money(hr.multiply(s.getOtMultiplier()).multiply(otHours));
            l.sunDays = sunHours.divide(hpd, 2, RoundingMode.HALF_UP);
            l.sunPay = money(day.multiply(s.getSundayMultiplier()).multiply(sunHours).divide(hpd, 6, RoundingMode.HALF_UP));
            l.sunOtHours = sunOt;
            l.sunOtPay = money(hr.multiply(s.getSundayOtMultiplier()).multiply(sunOt));
            l.specialHolDays = specHours.divide(hpd, 2, RoundingMode.HALF_UP);
            l.specialHolPay = money(day.multiply(s.getSpecialHolidayPremium()).multiply(specHours).divide(hpd, 6, RoundingMode.HALF_UP));
            l.regularHolDays = regHolHours.divide(hpd, 2, RoundingMode.HALF_UP);
            l.regularHolPay = money(day.multiply(s.getRegularHolidayPremium()).multiply(regHolHours).divide(hpd, 6, RoundingMode.HALF_UP));

            TimesheetRow row = rowBy.get(e.getId());
            if (row != null) {
                l.ndHours = nz(row.getNdHours());
                l.remarks = row.getRemarks();
            }
            l.ndPay = money(hr.multiply(s.getNightDiffRate()).multiply(l.ndHours));

            l.lateMinutes = lateMin;
            BigDecimal perMinute = hr.divide(BigDecimal.valueOf(60), 8, RoundingMode.HALF_UP);
            l.lateAmount = money(perMinute.multiply(s.getLateMultiplier()).multiply(BigDecimal.valueOf(lateMin)));

            // Extra lines entered on the pay sheet
            Set<String> manualKinds = new HashSet<>();
            for (PayAdjustment a : adjBy.getOrDefault(e.getId(), List.of())) {
                PayItem k = PayItem.parse(a.getKind());
                if (k == null) continue;
                PayLine.Item item = new PayLine.Item(k.name(), k.getLabel(), a.getDescription(), money(a.getAmount()), a.getId(), a.getPlanId() != null);
                (k.isEarning() ? l.earnings : l.deductions).add(item);
                manualKinds.add(k.name());
            }
            // Standard government contributions, unless entered by hand for this period
            boolean cutoffMatches = "BOTH".equalsIgnoreCase(e.getContributionCutoff())
                    || ("FIRST".equalsIgnoreCase(e.getContributionCutoff()) == p.isFirstCutoff());
            if (cutoffMatches) {
                addGov(l, PayItem.SSS, e.getSssAmount(), manualKinds, month);
                addGov(l, PayItem.PHILHEALTH, e.getPhilhealthAmount(), manualKinds, month);
                addGov(l, PayItem.PAGIBIG, e.getPagibigAmount(), manualKinds, month);
            }

            for (PayLine.Item i : l.earnings) l.otherEarnings = l.otherEarnings.add(i.amount);
            l.grossPay = l.basicPay.add(l.otPay).add(l.sunPay).add(l.sunOtPay).add(l.specialHolPay)
                    .add(l.regularHolPay).add(l.ndPay).add(l.otherEarnings);

            // Loan / advance schedules not yet posted; skipped in a cut-off with no pay
            if (l.grossPay.signum() > 0) {
                for (DeductionPlan d : plansBy.getOrDefault(e.getId(), List.of())) {
                    PayItem k = PayItem.parse(d.getKind());
                    if (k == null) k = PayItem.OTHER_DEDUCTION;
                    BigDecimal amt = d.isOpenEnded() ? d.getPerCutoff() : d.getPerCutoff().min(d.getBalance());
                    String desc = d.getDescription();
                    if (!d.isOpenEnded()) desc = (desc == null || desc.isBlank() ? "" : desc + " · ") + "bal. " + money(d.getBalance().subtract(amt)).toPlainString();
                    PayLine.Item item = new PayLine.Item(k.name(), k.getLabel(), desc, money(amt), null, true);
                    item.planId = d.getId();
                    l.deductions.add(item);
                }
            }
            BigDecimal ded = l.lateAmount.add(l.absentAmount);
            for (PayLine.Item i : l.deductions) ded = ded.add(i.amount);
            l.totalDeductions = ded;
            l.netPay = l.grossPay.subtract(ded);
            if (l.netPay.signum() < 0) l.warnings.add("Deductions exceed pay");
            lines.add(l);
        }
        return lines;
    }

    private static void addGov(PayLine l, PayItem kind, BigDecimal amount, Set<String> manual, String month) {
        if (manual.contains(kind.name()) || amount == null || amount.signum() <= 0) return;
        l.deductions.add(new PayLine.Item(kind.name(), kind.getLabel(), month, money(amount), null, true));
    }

    /** Daily rate; for monthly-paid staff without one, monthly × 12 ÷ working days per year. */
    public static BigDecimal dailyRate(Employee e, PayrollSettings s) {
        if (e.getDailyRate() != null && e.getDailyRate().signum() > 0) return e.getDailyRate();
        if (e.isMonthly() && e.getMonthlyRate() != null && e.getMonthlyRate().signum() > 0) {
            int days = s.getWorkDaysPerYear() == null || s.getWorkDaysPerYear() <= 0 ? 313 : s.getWorkDaysPerYear();
            return e.getMonthlyRate().multiply(BigDecimal.valueOf(12)).divide(BigDecimal.valueOf(days), 6, RoundingMode.HALF_UP);
        }
        return ZERO;
    }

    /** Monday–Saturday dates in the period that are not holidays: the days an absence counts on. */
    public static List<LocalDate> workingDates(PayrollPeriod p, Set<LocalDate> holidays) {
        List<LocalDate> out = new ArrayList<>();
        for (LocalDate d = p.getStartDate(); !d.isAfter(p.getEndDate()); d = d.plusDays(1)) {
            if (d.getDayOfWeek() != DayOfWeek.SUNDAY && !holidays.contains(d)) out.add(d);
        }
        return out;
    }

    static BigDecimal nz(BigDecimal v) { return v == null ? ZERO : v; }

    public static BigDecimal money(BigDecimal v) { return (v == null ? ZERO : v).setScale(2, RoundingMode.HALF_UP); }
}
