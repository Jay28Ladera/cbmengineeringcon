package com.asmsi.admin_system.accounting;

import com.asmsi.admin_system.entity.PayrollPeriod;
import com.asmsi.admin_system.payroll.PayLine;
import com.asmsi.admin_system.payroll.PayrollService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static com.asmsi.admin_system.accounting.ClientBillingService.*;

/**
 * Posts a locked payroll period: each employee's pay to direct labor of a job (or office salaries);
 * contributions, loans, advances and the sinking fund to their accounts; the net to salaries payable.
 * Releasing pays salaries payable from a bank or cash.
 */
@Service
public class PayrollPostingService {

    private final PayrollPostingRepository postings;
    private final EmployeeJobRepository employeeJobs;
    private final PayrollService payroll;
    private final BankAccountRepository banks;
    private final Ledger ledger;
    private final com.asmsi.admin_system.repository.EmployeeRepository employees;

    public PayrollPostingService(PayrollPostingRepository postings, EmployeeJobRepository employeeJobs, PayrollService payroll,
                                 BankAccountRepository banks, Ledger ledger, com.asmsi.admin_system.repository.EmployeeRepository employees) {
        this.postings = postings; this.employeeJobs = employeeJobs; this.payroll = payroll; this.banks = banks; this.ledger = ledger;
        this.employees = employees;
    }

    /** Employer shares of one pay line: {SSS incl. EC, PhilHealth, Pag-IBIG}. Older locked lines without them are worked out from the employee shares. */
    public static BigDecimal[] employerShares(PayLine l, com.asmsi.admin_system.entity.Employee e) {
        if (l.erSss != null && l.employerTotal().signum() > 0) return new BigDecimal[]{l.erSss.add(l.erEc), l.erPhilhealth, l.erPagibig};
        BigDecimal fraction = e != null && "BOTH".equalsIgnoreCase(e.getContributionCutoff()) ? new BigDecimal("0.5") : BigDecimal.ONE;
        var s = com.asmsi.admin_system.payroll.Statutory.employerFor("SSS", l.amount("SSS"), fraction);
        return new BigDecimal[]{s.er().add(s.ec()),
                com.asmsi.admin_system.payroll.Statutory.employerFor("PHILHEALTH", l.amount("PHILHEALTH"), fraction).er(),
                com.asmsi.admin_system.payroll.Statutory.employerFor("PAGIBIG", l.amount("PAGIBIG"), fraction).er()};
    }

    /** Employer shares for a whole period's lines, by employee id. */
    public Map<Long, BigDecimal[]> employerShares(List<PayLine> lines) {
        Map<Long, com.asmsi.admin_system.entity.Employee> emp = new HashMap<>();
        for (var e : employees.findAllById(lines.stream().map(x -> x.employeeId).filter(Objects::nonNull).toList())) emp.put(e.getId(), e);
        Map<Long, BigDecimal[]> out = new HashMap<>();
        for (PayLine l : lines) out.put(l.employeeId, employerShares(l, emp.get(l.employeeId)));
        return out;
    }

    /** Pay actually earned: net pay plus everything deducted for others (contributions, loans, advances…). */
    public static BigDecimal earned(PayLine l) {
        BigDecimal d = BigDecimal.ZERO;
        for (PayLine.Item it : l.deductions) d = d.add(it.amount);
        return r2(l.netPay.add(d));
    }

    static String accountFor(String kind) {
        return switch (kind) {
            case "SSS" -> Acct.SSS_PAYABLE; case "PHILHEALTH" -> Acct.PHILHEALTH_PAYABLE; case "PAGIBIG" -> Acct.PAGIBIG_PAYABLE;
            case "WTAX" -> Acct.WTC_PAYABLE;
            case "LOAN", "CASH_ADVANCE" -> Acct.ADVANCES_EMPLOYEES; case "SINKING" -> Acct.SINKING_FUND;
            default -> Acct.OTHER_INCOME;
        };
    }

    public Optional<PayrollPosting> posting(Long periodId) { return postings.findFirstByPeriodIdAndStatusNot(periodId, "VOID"); }

    @Transactional
    public PayrollPosting post(PayrollPeriod p, Map<Long, Long> jobByEmployee, LocalDate date, String by) {
        if (!p.isLocked()) throw new IllegalStateException("Lock the payroll in HR first, so the amounts are final.");
        if (posting(p.getId()).isPresent()) throw new IllegalStateException("This payroll is already posted.");
        List<PayLine> lines = payroll.lines(p).stream().filter(l -> l.netPay.signum() != 0 || !l.deductions.isEmpty()).toList();
        if (lines.isEmpty()) throw new IllegalStateException("The payroll has no pay lines.");
        List<JournalLine> j = new ArrayList<>();
        Map<String, BigDecimal> credits = new LinkedHashMap<>();
        BigDecimal earned = BigDecimal.ZERO, toJobs = BigDecimal.ZERO, net = BigDecimal.ZERO;
        Long labor = ledger.id(Acct.DIRECT_LABOR), office = ledger.id(Acct.SALARIES_OFFICE), erOffice = ledger.id(Acct.EMPLOYER_CONTRIBUTIONS);
        Map<Long, BigDecimal[]> er = employerShares(lines);
        BigDecimal erTotal = BigDecimal.ZERO;
        for (PayLine l : lines) {
            Long job = jobByEmployee.get(l.employeeId);
            BigDecimal e = earned(l);
            if (e.signum() != 0) j.add(JournalLine.dr(job != null ? labor : office, e, l.name).job(job));
            // Employer shares: part of labor cost on a job, else office expense; owed to the agencies.
            BigDecimal[] sh = er.get(l.employeeId);
            BigDecimal t = sh[0].add(sh[1]).add(sh[2]);
            if (t.signum() > 0) {
                j.add(JournalLine.dr(job != null ? labor : erOffice, t, l.name + " · employer SSS/PhilHealth/Pag-IBIG").job(job));
                credits.merge(Acct.SSS_PAYABLE + "ER", sh[0], BigDecimal::add);
                credits.merge(Acct.PHILHEALTH_PAYABLE + "ER", sh[1], BigDecimal::add);
                credits.merge(Acct.PAGIBIG_PAYABLE + "ER", sh[2], BigDecimal::add);
                erTotal = erTotal.add(t);
                if (job != null) toJobs = toJobs.add(t);
            }
            for (PayLine.Item it : l.deductions) credits.merge(accountFor(it.kind), it.amount, BigDecimal::add);
            earned = earned.add(e); net = net.add(l.netPay);
            if (job != null) toJobs = toJobs.add(e);
            employeeJobs.save(new EmployeeJob(l.employeeId, job));
        }
        credits.forEach((key, amt) -> {
            if (amt.signum() == 0) return;
            boolean employer = key.endsWith("ER");
            String code = employer ? key.substring(0, key.length() - 2) : key;
            j.add(JournalLine.cr(ledger.id(code), amt, employer ? label(code).replace("employee share", "employer share") : label(code)));
        });
        j.add(JournalLine.cr(ledger.id(Acct.SALARIES_PAYABLE), net, "Net pay"));
        PayrollPosting x = new PayrollPosting();
        x.periodId = p.getId(); x.periodLabel = p.getLabel(); x.postDate = date == null ? p.getEndDate() : date;
        x.earned = earned; x.net = r2(net); x.deductions = earned.subtract(x.net); x.toJobs = toJobs; x.postedBy = by;
        x.employerShare = r2(erTotal);
        postings.save(x);
        x.journalId = ledger.post(x.postDate, "Payroll " + x.periodLabel, "PAYROLL", x.id, x.periodLabel, j, by).id;
        return postings.save(x);
    }

    private static String label(String code) {
        return switch (code) {
            case Acct.SSS_PAYABLE -> "SSS (employee share)"; case Acct.PHILHEALTH_PAYABLE -> "PhilHealth (employee share)";
            case Acct.PAGIBIG_PAYABLE -> "Pag-IBIG (employee share)"; case Acct.WTC_PAYABLE -> "Withholding tax on compensation"; case Acct.ADVANCES_EMPLOYEES -> "Loans and cash advances deducted";
            case Acct.SINKING_FUND -> "Sinking fund"; default -> "Other deductions";
        };
    }

    @Transactional
    public void release(PayrollPosting x, Long bankId, LocalDate date, String ref, String by) {
        if (!"POSTED".equals(x.status)) throw new IllegalStateException("Only a posted, unreleased payroll can be released.");
        BankAccount b = banks.findById(bankId == null ? -1L : bankId).orElseThrow(() -> new IllegalArgumentException("Choose where the pay comes from."));
        x.releaseBankId = b.id; x.releaseDate = date == null ? LocalDate.now() : date; x.releaseRef = cut(ref, 60);
        x.releaseJournalId = ledger.post(x.releaseDate, "Payroll released · " + x.periodLabel + (x.releaseRef != null ? " · " + x.releaseRef : ""), "PAYROLL_PAY", x.id, x.periodLabel,
                List.of(JournalLine.dr(ledger.id(Acct.SALARIES_PAYABLE), x.net, "Net pay released"), JournalLine.cr(b.accountId, x.net, b.name)), by).id;
        x.status = "RELEASED"; x.releasedBy = by;
        postings.save(x);
    }

    @Transactional
    public void unpost(PayrollPosting x, String reason, String by) {
        if (!x.isActive()) return;
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("Give the reason.");
        if (x.releaseJournalId != null) ledger.reverseSource("PAYROLL_PAY", x.id, reason.trim(), by);
        ledger.reverseSource("PAYROLL", x.id, reason.trim(), by);
        x.status = "VOID"; x.voidReason = cut(reason.trim(), 300);
        postings.save(x);
    }
}
