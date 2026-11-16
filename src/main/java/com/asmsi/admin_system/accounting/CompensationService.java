package com.asmsi.admin_system.accounting;

import com.asmsi.admin_system.entity.Employee;
import com.asmsi.admin_system.entity.PayrollPeriod;
import com.asmsi.admin_system.payroll.PayItem;
import com.asmsi.admin_system.payroll.PayLine;
import com.asmsi.admin_system.payroll.PayrollService;
import com.asmsi.admin_system.payroll.Statutory;
import com.asmsi.admin_system.repository.EmployeeRepository;
import com.asmsi.admin_system.repository.PayrollPeriodRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;

/**
 * Compensation figures for BIR and the agencies, from locked payroll periods (dated by the end of the
 * cut-off): monthly 1601-C and SSS / PhilHealth / Pag-IBIG contributions, and the yearly alphalist
 * (1604-C) and BIR 2316 per employee.
 */
@Service
public class CompensationService {

    /** One employee's totals over some periods. */
    public static class Emp {
        public Long employeeId;
        public String name, employeeNo, position, tin, sssNo, philhealthNo, pagibigNo, address, taxStatus;
        public LocalDate birthDate, hired;
        public BigDecimal gross = Z, benefits = Z, lateAbsent = Z, taxable = Z, wtax = Z;
        public BigDecimal sssEe = Z, sssEr = Z, phEe = Z, phEr = Z, piEe = Z, piEr = Z;
        public int periods;

        public BigDecimal getGovEe() { return sssEe.add(phEe).add(piEe); }
        public BigDecimal getGovEr() { return sssEr.add(phEr).add(piEr); }
        /** Compensation actually earned: gross less lates and absences. */
        public BigDecimal getCompensation() { return gross.subtract(lateAbsent); }
        public boolean isMwe() { return "MWE".equals(taxStatus); }
        // ---- yearly (2316) figures
        /** 13th month and other benefits that are exempt (up to ₱90,000). */
        public BigDecimal getExemptBenefits() { return benefits.min(Statutory.BENEFITS_CEILING); }
        public BigDecimal getTaxableBenefits() { return benefits.subtract(getExemptBenefits()).max(Z); }
        /** Pay of a minimum wage earner, all exempt. */
        public BigDecimal getMwePay() { return isMwe() ? getCompensation().subtract(benefits).subtract(getGovEe()).max(Z) : Z; }
        public BigDecimal getNonTaxable() { return getExemptBenefits().add(getGovEe()).add(getMwePay()); }
        /** Regular taxable pay; for a minimum wage earner only benefits over the ceiling. */
        public BigDecimal getTaxableRegular() { return isMwe() ? Z : taxable; }
        public BigDecimal getTaxableYear() { return getTaxableRegular().add(getTaxableBenefits()); }
        public BigDecimal getTaxDue() { return Statutory.annualTax(getTaxableYear()); }
        /** Positive: still to withhold (December); negative: over-withheld, to refund. */
        public BigDecimal getAdjustment() { return getTaxDue().subtract(wtax); }
        public boolean isMissingTin() { return tin == null || tin.isBlank(); }
    }

    public record Summary(String label, LocalDate from, LocalDate to, List<Emp> rows, Emp total, List<PayrollPeriod> periods, List<PayrollPeriod> open) {}

    private final PayrollPeriodRepository periods;
    private final PayrollService payroll;
    private final PayrollPostingService posting;
    private final EmployeeRepository employees;

    public CompensationService(PayrollPeriodRepository periods, PayrollService payroll, PayrollPostingService posting, EmployeeRepository employees) {
        this.periods = periods; this.payroll = payroll; this.posting = posting; this.employees = employees;
    }

    public Summary month(YearMonth ym) {
        return summary(ym.getMonth().getDisplayName(java.time.format.TextStyle.FULL, Locale.ENGLISH) + " " + ym.getYear(), ym.atDay(1), ym.atEndOfMonth());
    }

    public Summary year(int year) {
        return summary(String.valueOf(year), LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31));
    }

    /** Periods whose cut-off ends within the dates; only locked ones count. */
    @Transactional(readOnly = true)
    public Summary summary(String label, LocalDate from, LocalDate to) {
        List<PayrollPeriod> in = new ArrayList<>(), open = new ArrayList<>();
        for (PayrollPeriod p : periods.findAllByOrderByStartDateDesc()) {
            if (p.getEndDate().isBefore(from) || p.getEndDate().isAfter(to)) continue;
            (p.isLocked() ? in : open).add(p);
        }
        in.sort(Comparator.comparing(PayrollPeriod::getEndDate));
        Map<Long, Emp> by = new LinkedHashMap<>();
        Map<Long, Employee> emp = new HashMap<>();
        for (Employee e : employees.findAll()) emp.put(e.getId(), e);
        for (PayrollPeriod p : in) {
            List<PayLine> lines = payroll.lines(p);
            Map<Long, BigDecimal[]> er = posting.employerShares(lines);
            for (PayLine l : lines) {
                if (!l.hasActivity()) continue;
                Emp x = by.computeIfAbsent(l.employeeId, k -> start(l, emp.get(k)));
                x.periods++;
                x.gross = x.gross.add(l.grossPay);
                x.lateAbsent = x.lateAbsent.add(l.lateAmount).add(l.absentAmount);
                BigDecimal ben = Z;
                for (PayLine.Item i : l.earnings) { PayItem k = PayItem.parse(i.kind); if (k != null && k.isBenefit()) ben = ben.add(i.amount); }
                x.benefits = x.benefits.add(ben);
                BigDecimal gov = l.deductionSum("SSS", "PHILHEALTH", "PAGIBIG");
                // Lines locked before taxable pay was recorded: work it out the same way.
                BigDecimal taxable = l.taxable != null && (l.taxable.signum() > 0 || l.amount("WTAX").signum() > 0) ? l.taxable
                        : l.grossPay.subtract(ben).subtract(l.lateAmount).subtract(l.absentAmount).subtract(gov).max(Z);
                if (x.isMwe()) taxable = Z;
                x.taxable = x.taxable.add(taxable);
                x.wtax = x.wtax.add(l.amount("WTAX"));
                x.sssEe = x.sssEe.add(l.amount("SSS")); x.phEe = x.phEe.add(l.amount("PHILHEALTH")); x.piEe = x.piEe.add(l.amount("PAGIBIG"));
                BigDecimal[] sh = er.get(l.employeeId);
                if (sh != null) { x.sssEr = x.sssEr.add(sh[0]); x.phEr = x.phEr.add(sh[1]); x.piEr = x.piEr.add(sh[2]); }
            }
        }
        List<Emp> rows = new ArrayList<>(by.values());
        rows.sort(Comparator.comparing(e -> e.name == null ? "" : e.name.toLowerCase()));
        Emp t = new Emp(); t.name = "Total";
        for (Emp e : rows) {
            t.gross = t.gross.add(e.gross); t.benefits = t.benefits.add(e.benefits); t.lateAbsent = t.lateAbsent.add(e.lateAbsent);
            t.taxable = t.taxable.add(e.taxable); t.wtax = t.wtax.add(e.wtax);
            t.sssEe = t.sssEe.add(e.sssEe); t.sssEr = t.sssEr.add(e.sssEr); t.phEe = t.phEe.add(e.phEe); t.phEr = t.phEr.add(e.phEr);
            t.piEe = t.piEe.add(e.piEe); t.piEr = t.piEr.add(e.piEr);
        }
        return new Summary(label, from, to, rows, t, in, open);
    }

    private static Emp start(PayLine l, Employee e) {
        Emp x = new Emp();
        x.employeeId = l.employeeId; x.name = l.name; x.employeeNo = l.employeeNo; x.position = l.position;
        if (e != null) {
            x.name = e.getFullName(); x.tin = e.getTin(); x.sssNo = e.getSssNo(); x.philhealthNo = e.getPhilhealthNo(); x.pagibigNo = e.getPagibigNo();
            x.address = e.getAddress(); x.birthDate = e.getBirthDate(); x.hired = e.getDateHired(); x.taxStatus = e.getTaxStatus();
        } else x.taxStatus = "REGULAR";
        return x;
    }

    // ---------------------------------------------------------------- 1601-C figures (monthly)

    public record Return1601C(BigDecimal total, BigDecimal mwePay, BigDecimal benefits, BigDecimal govEe, BigDecimal nonTaxable,
                              BigDecimal taxable, BigDecimal tax) {}

    public Return1601C r1601c(Summary s) {
        BigDecimal mwe = Z, ben = Z, gov = Z, taxable = Z;
        for (Emp e : s.rows()) { mwe = mwe.add(e.getMwePay()); ben = ben.add(e.benefits); gov = gov.add(e.getGovEe()); taxable = taxable.add(e.taxable); }
        BigDecimal total = s.total().getCompensation();
        BigDecimal nonTax = mwe.add(ben).add(gov);
        return new Return1601C(total, mwe, ben, gov, nonTax, taxable, s.total().wtax);
    }

    // ---------------------------------------------------------------- CSV

    public String contributionsCsv(Summary s) {
        StringBuilder sb = new StringBuilder("Employee,Employee no,SSS no,PhilHealth no,Pag-IBIG no,SSS EE,SSS ER (with EC),PhilHealth EE,PhilHealth ER,Pag-IBIG EE,Pag-IBIG ER\n");
        for (Emp e : s.rows()) sb.append(String.join(",", c(e.name), c(e.employeeNo), c(e.sssNo), c(e.philhealthNo), c(e.pagibigNo),
                p(e.sssEe), p(e.sssEr), p(e.phEe), p(e.phEr), p(e.piEe), p(e.piEr))).append('\n');
        return sb.toString();
    }

    /** Alphalist of employees (1604-C Schedule 1 columns, for the BIR alphalist data entry module). */
    public String alphalistCsv(Summary s) {
        StringBuilder sb = new StringBuilder("Seq,TIN,Employee name,Birth date,Address,Employment from,Employment to,Gross compensation,"
                + "13th month and other benefits (exempt),SSS/PhilHealth/Pag-IBIG (employee),Minimum wage pay (exempt),Total non-taxable,"
                + "Taxable 13th month and benefits,Taxable compensation,Total taxable,Tax due,Tax withheld (Jan-Nov+Dec),Over (under) withheld,Status\n");
        int n = 0;
        for (Emp e : s.rows()) {
            n++;
            LocalDate from = e.hired != null && e.hired.isAfter(s.from()) ? e.hired : s.from();
            sb.append(String.join(",", String.valueOf(n), c(e.tin), c(e.name), e.birthDate == null ? "" : e.birthDate.toString(), c(e.address),
                    from.toString(), s.to().toString(), p(e.getCompensation()), p(e.getExemptBenefits()), p(e.getGovEe()), p(e.getMwePay()),
                    p(e.getNonTaxable()), p(e.getTaxableBenefits()), p(e.taxable), p(e.getTaxableYear()), p(e.getTaxDue()), p(e.wtax),
                    p(e.wtax.subtract(e.getTaxDue())), e.isMwe() ? "MWE" : "")).append('\n');
        }
        return sb.toString();
    }

    private static String p(BigDecimal v) { return Statutory.m(v).toPlainString(); }
    private static String c(String v) {
        if (v == null) return "";
        return v.contains(",") || v.contains("\"") ? "\"" + v.replace("\"", "\"\"") + "\"" : v;
    }

    static final BigDecimal Z = BigDecimal.ZERO;
}
