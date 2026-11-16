package com.asmsi.admin_system.accounting;

import com.asmsi.admin_system.entity.Employee;
import com.asmsi.admin_system.entity.PayrollPeriod;
import com.asmsi.admin_system.inventory.Lookups;
import com.asmsi.admin_system.inventory.Supplier;
import com.asmsi.admin_system.inventory.SupplierRepository;
import com.asmsi.admin_system.payroll.PayLine;
import com.asmsi.admin_system.payroll.PayrollService;
import com.asmsi.admin_system.repository.EmployeeRepository;
import com.asmsi.admin_system.repository.PayrollPeriodRepository;
import com.asmsi.admin_system.service.AuthUtil;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;

import static com.asmsi.admin_system.accounting.F.*;

/** Accounting › Petty Cash (vouchers, replenishment, cash advances), Admin Expenses, Payroll posting and Remittances. */
@Controller
@RequestMapping("/Accounting")
public class CashController {

    private final PettyCashService petty;
    private final PettyCashVoucherRepository pcvs;
    private final ReplenishmentRepository replenishments;
    private final CashAdvanceRepository advances;
    private final CashAdvanceLineRepository advanceLines;
    private final BankAccountRepository banks;
    private final AccountRepository accounts;
    private final EmployeeRepository employees;
    private final ExpenseService expenses;
    private final RecurringExpenseRepository recurring;
    private final BillRepository bills;
    private final SupplierRepository suppliers;
    private final PayrollPostingService payrollPosting;
    private final PayrollPostingRepository postings;
    private final EmployeeJobRepository employeeJobs;
    private final PayrollPeriodRepository periods;
    private final PayrollService payroll;
    private final RemittanceService remit;
    private final RemittanceRepository remittances;
    private final Ledger ledger;
    private final Lookups lookups;
    private final AuthUtil auth;

    public CashController(PettyCashService petty, PettyCashVoucherRepository pcvs, ReplenishmentRepository replenishments, CashAdvanceRepository advances,
                          CashAdvanceLineRepository advanceLines, BankAccountRepository banks, AccountRepository accounts, EmployeeRepository employees,
                          ExpenseService expenses, RecurringExpenseRepository recurring, BillRepository bills, SupplierRepository suppliers,
                          PayrollPostingService payrollPosting, PayrollPostingRepository postings, EmployeeJobRepository employeeJobs,
                          PayrollPeriodRepository periods, PayrollService payroll, RemittanceService remit, RemittanceRepository remittances,
                          Ledger ledger, Lookups lookups, AuthUtil auth) {
        this.petty = petty; this.pcvs = pcvs; this.replenishments = replenishments; this.advances = advances; this.advanceLines = advanceLines;
        this.banks = banks; this.accounts = accounts; this.employees = employees; this.expenses = expenses; this.recurring = recurring; this.bills = bills;
        this.suppliers = suppliers; this.payrollPosting = payrollPosting; this.postings = postings; this.employeeJobs = employeeJobs; this.periods = periods;
        this.payroll = payroll; this.remit = remit; this.remittances = remittances; this.ledger = ledger; this.lookups = lookups; this.auth = auth;
    }

    private String me() { var u = auth.getCurrentUser(); return u == null ? "system" : u.getName(); }
    private Map<Long, Account> accountMap() { Map<Long, Account> m = new HashMap<>(); accounts.findAll().forEach(a -> m.put(a.id, a)); return m; }
    private Map<Long, BankAccount> bankMap() { Map<Long, BankAccount> m = new HashMap<>(); banks.findAll().forEach(b -> m.put(b.id, b)); return m; }
    private List<Account> expenseAccounts() { return accounts.findByActiveTrueOrderByCodeAsc().stream().filter(a -> "EXPENSE".equals(a.type)).toList(); }

    // ================================================================== petty cash

    public record FundRow(BankAccount fund, BigDecimal balance, BigDecimal toReplenish, int count) {}

    @GetMapping("/PettyCash")
    @Transactional(readOnly = true)
    public String pettyCash(Model model) {
        List<FundRow> funds = new ArrayList<>();
        for (BankAccount b : banks.findByActiveTrueOrderByKindAscNameAsc()) {
            if (!b.isPetty()) continue;
            var u = petty.unreplenished(b.id);
            funds.add(new FundRow(b, petty.balance(b), u.total(), u.vouchers().size() + u.advances().size()));
        }
        model.addAttribute("funds", funds);
        model.addAttribute("vouchers", pcvs.findTop200ByOrderByIdDesc().stream().limit(60).toList());
        model.addAttribute("advances", advances.findTop200ByOrderByIdDesc());
        model.addAttribute("replenishments", replenishments.findTop50ByOrderByIdDesc());
        model.addAttribute("sources", banks.findByActiveTrueOrderByKindAscNameAsc());
        model.addAttribute("expenseAccounts", expenseAccounts());
        model.addAttribute("employees", employees.findByActiveTrue().stream().sorted(Comparator.comparing(Employee::getFullName)).toList());
        model.addAttribute("jobs", lookups.openJobOrders());
        model.addAttribute("jobMap", lookups.jobMap());
        model.addAttribute("bankMap", bankMap());
        model.addAttribute("accountMap", accountMap());
        model.addAttribute("today", LocalDate.now());
        model.addAttribute("activePage", "petty-cash");
        return "acc-petty";
    }

    @PostMapping("/PettyCash/voucher")
    public String pcv(@RequestParam(required = false) String fundId, @RequestParam(required = false) String voucherDate, @RequestParam(required = false) String payee,
                      @RequestParam(required = false) String description, @RequestParam(required = false) String accountId, @RequestParam(required = false) String jobOrderId,
                      @RequestParam(required = false) String amount, @RequestParam(required = false) String receiptNo, @RequestParam(required = false) String receivedBy,
                      RedirectAttributes ra) {
        PettyCashVoucher v = new PettyCashVoucher();
        v.fundId = id(fundId); v.voucherDate = date(voucherDate); v.payee = cut(payee, 160); v.description = cut(description, 300);
        v.accountId = id(accountId); v.jobOrderId = id(jobOrderId); v.amount = num(amount); v.receiptNo = cut(receiptNo, 60); v.receivedBy = cut(receivedBy, 160);
        try { petty.pay(v, me()); ra.addFlashAttribute("success", v.number + " recorded: ₱" + String.format("%,.2f", v.amount) + " · " + v.description + "."); }
        catch (IllegalArgumentException | IllegalStateException e) { ra.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/Accounting/PettyCash";
    }

    @PostMapping("/PettyCash/voucher/{id}/void")
    public String voidPcv(@PathVariable Long id, @RequestParam(required = false) String reason, RedirectAttributes ra) {
        pcvs.findById(id).ifPresent(v -> {
            try { petty.voidVoucher(v, reason, me()); ra.addFlashAttribute("success", v.number + " voided."); }
            catch (IllegalArgumentException | IllegalStateException e) { ra.addFlashAttribute("error", e.getMessage()); }
        });
        return "redirect:/Accounting/PettyCash";
    }

    @GetMapping("/PettyCash/fund/{id}/replenish")
    @Transactional(readOnly = true)
    public String replenishForm(@PathVariable Long id, Model model) {
        BankAccount f = banks.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        model.addAttribute("fund", f);
        model.addAttribute("balance", petty.balance(f));
        model.addAttribute("u", petty.unreplenished(id));
        model.addAttribute("banks", banks.findByActiveTrueOrderByKindAscNameAsc().stream().filter(b -> !b.id.equals(id)).toList());
        model.addAttribute("accountMap", accountMap());
        model.addAttribute("jobMap", lookups.jobMap());
        model.addAttribute("today", LocalDate.now());
        model.addAttribute("activePage", "petty-cash");
        return "acc-petty-replenish";
    }

    @PostMapping("/PettyCash/fund/{id}/replenish")
    public String replenish(@PathVariable Long id, @RequestParam(required = false) String bankId, @RequestParam(required = false) String date,
                            @RequestParam(required = false) String chequeNo, RedirectAttributes ra) {
        try {
            Replenishment r = petty.replenish(id, id(bankId), date(date), chequeNo, me());
            ra.addFlashAttribute("success", "Fund replenished: ₱" + String.format("%,.2f", r.amount) + " (" + r.number + ").");
            return "redirect:/Accounting/PettyCash";
        } catch (IllegalArgumentException | IllegalStateException e) {
            ra.addFlashAttribute("error", e.getMessage());
            return "redirect:/Accounting/PettyCash/fund/" + id + "/replenish";
        }
    }

    @PostMapping("/PettyCash/advance")
    public String advance(@RequestParam(required = false) String employeeId, @RequestParam(required = false) String sourceId, @RequestParam(required = false) String advanceDate,
                          @RequestParam(required = false) String amount, @RequestParam(required = false) String purpose, @RequestParam(required = false) String jobOrderId,
                          @RequestParam(required = false) String dueDate, RedirectAttributes ra) {
        CashAdvance a = new CashAdvance();
        a.employeeId = id(employeeId); a.sourceId = id(sourceId); a.advanceDate = date(advanceDate); a.amount = num(amount);
        a.purpose = cut(purpose, 300); a.jobOrderId = id(jobOrderId); a.dueDate = date(dueDate);
        try { petty.advance(a, me()); ra.addFlashAttribute("success", "Cash advance " + a.number + " to " + a.employeeName + ": ₱" + String.format("%,.2f", a.amount) + "."); }
        catch (IllegalArgumentException | IllegalStateException e) { ra.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/Accounting/PettyCash";
    }

    @GetMapping("/PettyCash/advance/{id}")
    @Transactional(readOnly = true)
    public String advanceView(@PathVariable Long id, Model model) {
        CashAdvance a = advances.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        model.addAttribute("a", a);
        model.addAttribute("lines", advanceLines.findByAdvanceIdOrderByIdAsc(id));
        model.addAttribute("source", banks.findById(a.sourceId).orElse(null));
        model.addAttribute("expenseAccounts", expenseAccounts());
        model.addAttribute("jobs", lookups.openJobOrders());
        model.addAttribute("jobMap", lookups.jobMap());
        model.addAttribute("accountMap", accountMap());
        model.addAttribute("activePage", "petty-cash");
        return "acc-advance";
    }

    @PostMapping("/PettyCash/advance/{id}/liquidate")
    public String liquidate(@PathVariable Long id, @RequestParam(name = "r_desc", required = false) List<String> desc, @RequestParam(name = "r_account", required = false) List<String> acc,
                            @RequestParam(name = "r_job", required = false) List<String> job, @RequestParam(name = "r_amount", required = false) List<String> amt,
                            @RequestParam(name = "r_or", required = false) List<String> or, @RequestParam(required = false) String returned,
                            @RequestParam(required = false) String perCutoff, RedirectAttributes ra) {
        CashAdvance a = advances.findById(id).orElse(null);
        if (a == null) return "redirect:/Accounting/PettyCash";
        List<PettyCashService.Receipt> rs = new ArrayList<>();
        for (int i = 0; i < size(desc, acc, amt); i++) rs.add(new PettyCashService.Receipt(at(desc, i), id(at(acc, i)), id(at(job, i)), num(at(amt, i)), at(or, i)));
        try {
            petty.liquidate(a, rs, num(returned), num(perCutoff), me());
            ra.addFlashAttribute("success", a.number + " liquidated." + (a.toPayroll.signum() > 0 ? " ₱" + String.format("%,.2f", a.toPayroll) + " will be deducted from " + a.employeeName + "'s pay (HR › loans and deductions)." : ""));
        } catch (IllegalArgumentException | IllegalStateException e) { ra.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/Accounting/PettyCash/advance/" + id;
    }

    @PostMapping("/PettyCash/advance/{id}/void")
    public String voidAdvance(@PathVariable Long id, @RequestParam(required = false) String reason, RedirectAttributes ra) {
        advances.findById(id).ifPresent(a -> {
            try { petty.voidAdvance(a, reason, me()); ra.addFlashAttribute("success", a.number + " voided."); }
            catch (IllegalArgumentException | IllegalStateException e) { ra.addFlashAttribute("error", e.getMessage()); }
        });
        return "redirect:/Accounting/PettyCash/advance/" + id;
    }

    // ================================================================== admin expenses

    public record AccTotal(Account account, BigDecimal amount) {}

    @GetMapping("/AdminExpenses")
    @Transactional(readOnly = true)
    public String adminExpenses(@RequestParam(required = false) String month, Model model) {
        YearMonth m;
        try { m = blank(month) ? YearMonth.now() : YearMonth.parse(month); } catch (Exception e) { m = YearMonth.now(); }
        List<AccTotal> totals = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (Ledger.Balance b : ledger.activity(m.atDay(1), m.atEndOfMonth())) {
            if (!b.account().code.startsWith("6")) continue;
            BigDecimal v = b.debit().subtract(b.credit());
            if (v.signum() == 0) continue;
            totals.add(new AccTotal(b.account(), v)); total = total.add(v);
        }
        totals.sort(Comparator.comparing((AccTotal t) -> t.amount()).reversed());
        final YearMonth mm = m;
        List<Bill> monthBills = bills.findTop50ByStatusInOrderByIdDesc(List.of("OPEN", "PARTIAL", "PAID")).stream()
                .filter(b -> "EXPENSE".equals(b.kind) && YearMonth.from(b.billDate).equals(mm)).toList();
        model.addAttribute("month", m);
        model.addAttribute("prev", m.minusMonths(1)); model.addAttribute("next", m.plusMonths(1));
        model.addAttribute("totals", totals); model.addAttribute("total", total);
        model.addAttribute("monthBills", monthBills);
        model.addAttribute("recurring", recurring.findAllByOrderByActiveDescDayOfMonthAsc());
        model.addAttribute("dueNow", recurring.findAllByOrderByActiveDescDayOfMonthAsc().stream().filter(r -> expenses.dueThisMonth(r, LocalDate.now())).count());
        model.addAttribute("thisMonth", ExpenseService.month(YearMonth.now()));
        model.addAttribute("suppliers", suppliers.findByActiveTrueOrderByNameAsc());
        model.addAttribute("supplierMap", lookups.supplierMap());
        model.addAttribute("expenseAccounts", expenseAccounts());
        model.addAttribute("accountMap", accountMap());
        model.addAttribute("jobs", lookups.openJobOrders());
        model.addAttribute("activePage", "admin-expenses");
        return "acc-admin-expenses";
    }

    @PostMapping("/AdminExpenses/recurring")
    public String saveRecurring(@RequestParam(required = false) Long id, @RequestParam(required = false) String supplierId, @RequestParam(required = false) String description,
                                @RequestParam(required = false) String accountId, @RequestParam(required = false) String jobOrderId, @RequestParam(required = false) String amount,
                                @RequestParam(required = false) String vatMode, @RequestParam(required = false) String ewtRate, @RequestParam(required = false) String dayOfMonth,
                                @RequestParam(required = false) String dueDays, @RequestParam(defaultValue = "false") boolean active, RedirectAttributes ra) {
        RecurringExpense r = id == null ? new RecurringExpense() : recurring.findById(id).orElse(new RecurringExpense());
        r.supplierId = id(supplierId); r.description = cut(description, 300); r.accountId = id(accountId); r.jobOrderId = id(jobOrderId);
        r.amount = Optional.ofNullable(num(amount)).orElse(BigDecimal.ZERO);
        r.vatMode = vatMode != null && List.of("INCLUSIVE", "EXCLUSIVE", "NONE").contains(vatMode) ? vatMode : "INCLUSIVE";
        r.ewtRate = Optional.ofNullable(num(ewtRate)).orElse(BigDecimal.ZERO);
        try { r.dayOfMonth = Math.max(1, Math.min(31, Integer.parseInt(dayOfMonth.trim()))); } catch (Exception e) { r.dayOfMonth = 1; }
        try { r.dueDays = Math.max(0, Integer.parseInt(dueDays.trim())); } catch (Exception e) { r.dueDays = 15; }
        r.active = active || id == null;
        if (r.supplierId == null || r.description == null || r.accountId == null || r.amount.signum() <= 0) {
            ra.addFlashAttribute("error", "Supplier, description, account and amount are required.");
            return "redirect:/Accounting/AdminExpenses";
        }
        if (id == null) r.createdBy = me();
        recurring.save(r);
        ra.addFlashAttribute("success", "Monthly expense \"" + r.description + "\" saved.");
        return "redirect:/Accounting/AdminExpenses";
    }

    @PostMapping("/AdminExpenses/recurring/{id}/bill")
    public String billRecurring(@PathVariable Long id, @RequestParam(required = false) String amount, @RequestParam(required = false) String invoiceNo, RedirectAttributes ra) {
        RecurringExpense r = recurring.findById(id).orElse(null);
        if (r == null) return "redirect:/Accounting/AdminExpenses";
        try {
            Bill b = expenses.bill(r, YearMonth.now(), num(amount), cut(invoiceNo, 60), me());
            ra.addFlashAttribute("success", "Bill " + b.number + " created for " + r.description + ": ₱" + String.format("%,.2f", b.gross) + ", due " + b.dueDate + ".");
        } catch (IllegalArgumentException | IllegalStateException e) { ra.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/Accounting/AdminExpenses";
    }

    // ================================================================== payroll

    public record PeriodRow(PayrollPeriod period, PayrollPosting posting) {}

    @GetMapping("/Payroll")
    @Transactional(readOnly = true)
    public String payrollList(Model model) {
        List<PeriodRow> rows = new ArrayList<>();
        for (PayrollPeriod p : periods.findAllByOrderByStartDateDesc()) rows.add(new PeriodRow(p, payrollPosting.posting(p.getId()).orElse(null)));
        model.addAttribute("rows", rows);
        model.addAttribute("activePage", "acc-payroll");
        return "acc-payroll";
    }

    public record EmpLine(PayLine line, BigDecimal earned, Long jobId, BigDecimal employer) {}

    @GetMapping("/Payroll/{periodId}")
    @Transactional(readOnly = true)
    public String payrollPeriod(@PathVariable Long periodId, Model model) {
        PayrollPeriod p = periods.findById(periodId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        Map<Long, Long> defaults = new HashMap<>();
        employeeJobs.findAll().forEach(e -> defaults.put(e.employeeId, e.jobOrderId));
        List<EmpLine> ls = new ArrayList<>();
        Map<String, BigDecimal> ded = new TreeMap<>();
        BigDecimal earned = BigDecimal.ZERO, net = BigDecimal.ZERO, employer = BigDecimal.ZERO;
        List<PayLine> all = payroll.lines(p).stream().filter(l -> l.netPay.signum() != 0 || !l.deductions.isEmpty()).toList();
        Map<Long, BigDecimal[]> er = payrollPosting.employerShares(all);
        for (PayLine l : all) {
            BigDecimal e = PayrollPostingService.earned(l);
            BigDecimal[] sh = er.get(l.employeeId);
            BigDecimal t = sh[0].add(sh[1]).add(sh[2]);
            employer = employer.add(t);
            ls.add(new EmpLine(l, e, defaults.get(l.employeeId), t));
            for (PayLine.Item it : l.deductions) ded.merge(it.label, it.amount, BigDecimal::add);
            earned = earned.add(e); net = net.add(l.netPay);
        }
        PayrollPosting x = payrollPosting.posting(periodId).orElse(null);
        model.addAttribute("p", p);
        model.addAttribute("x", x);
        model.addAttribute("lines", ls);
        model.addAttribute("ded", ded);
        model.addAttribute("earned", earned); model.addAttribute("net", net); model.addAttribute("employer", employer);
        model.addAttribute("jobs", lookups.jobOrders());
        model.addAttribute("jobMap", lookups.jobMap());
        model.addAttribute("banks", banks.findByActiveTrueOrderByKindAscNameAsc());
        model.addAttribute("bankMap", bankMap());
        model.addAttribute("today", LocalDate.now());
        model.addAttribute("activePage", "acc-payroll");
        return "acc-payroll-period";
    }

    @PostMapping("/Payroll/{periodId}/post")
    public String postPayroll(@PathVariable Long periodId, @RequestParam(required = false) String date, @RequestParam Map<String, String> form, RedirectAttributes ra) {
        PayrollPeriod p = periods.findById(periodId).orElse(null);
        if (p == null) return "redirect:/Accounting/Payroll";
        Map<Long, Long> jobs = new HashMap<>();
        for (var e : form.entrySet()) if (e.getKey().startsWith("job_")) { Long emp = id(e.getKey().substring(4)); if (emp != null) jobs.put(emp, id(e.getValue())); }
        try {
            PayrollPosting x = payrollPosting.post(p, jobs, date(date), me());
            ra.addFlashAttribute("success", "Payroll " + x.periodLabel + " posted: ₱" + String.format("%,.2f", x.earned) + " pay, ₱" + String.format("%,.2f", x.toJobs) + " charged to jobs.");
        } catch (IllegalArgumentException | IllegalStateException e) { ra.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/Accounting/Payroll/" + periodId;
    }

    @PostMapping("/Payroll/{periodId}/release")
    public String releasePayroll(@PathVariable Long periodId, @RequestParam(required = false) String bankId, @RequestParam(required = false) String date,
                                 @RequestParam(required = false) String ref, RedirectAttributes ra) {
        payrollPosting.posting(periodId).ifPresent(x -> {
            try { payrollPosting.release(x, id(bankId), date(date), ref, me()); ra.addFlashAttribute("success", "Net pay of ₱" + String.format("%,.2f", x.net) + " released."); }
            catch (IllegalArgumentException | IllegalStateException e) { ra.addFlashAttribute("error", e.getMessage()); }
        });
        return "redirect:/Accounting/Payroll/" + periodId;
    }

    @PostMapping("/Payroll/{periodId}/unpost")
    public String unpostPayroll(@PathVariable Long periodId, @RequestParam(required = false) String reason, RedirectAttributes ra) {
        payrollPosting.posting(periodId).ifPresent(x -> {
            try { payrollPosting.unpost(x, reason, me()); ra.addFlashAttribute("success", "Payroll posting reversed. HR can now unlock the period."); }
            catch (IllegalArgumentException | IllegalStateException e) { ra.addFlashAttribute("error", e.getMessage()); }
        });
        return "redirect:/Accounting/Payroll/" + periodId;
    }

    // ================================================================== remittances

    @GetMapping("/Remittances")
    @Transactional(readOnly = true)
    public String remittances(Model model) {
        model.addAttribute("kinds", RemittanceService.KINDS);
        model.addAttribute("owed", remit.owed());
        model.addAttribute("rows", remittances.findTop100ByOrderByIdDesc());
        model.addAttribute("banks", banks.findByActiveTrueOrderByKindAscNameAsc());
        model.addAttribute("bankMap", bankMap());
        model.addAttribute("today", LocalDate.now());
        model.addAttribute("activePage", "remittances");
        return "acc-remittances";
    }

    @PostMapping("/Remittances")
    public String remit(@RequestParam(required = false) String kind, @RequestParam(required = false) String amount, @RequestParam(required = false) String bankId,
                        @RequestParam(required = false) String date, @RequestParam(required = false) String periodLabel, @RequestParam(required = false) String reference,
                        RedirectAttributes ra) {
        try {
            Remittance r = remit.remit(kind, num(amount), id(bankId), date(date), periodLabel, reference, me());
            ra.addFlashAttribute("success", RemittanceService.kind(r.kind).label() + " remitted: ₱" + String.format("%,.2f", r.amount) + " (" + r.number + ").");
        } catch (IllegalArgumentException | IllegalStateException e) { ra.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/Accounting/Remittances";
    }

    @PostMapping("/Remittances/{id}/void")
    public String voidRemit(@PathVariable Long id, @RequestParam(required = false) String reason, RedirectAttributes ra) {
        remittances.findById(id).ifPresent(r -> {
            try { remit.voidRemittance(r, reason, me()); ra.addFlashAttribute("success", r.number + " voided."); }
            catch (IllegalArgumentException | IllegalStateException e) { ra.addFlashAttribute("error", e.getMessage()); }
        });
        return "redirect:/Accounting/Remittances";
    }
}
