package com.asmsi.admin_system.accounting;

import com.asmsi.admin_system.inventory.*;
import com.asmsi.admin_system.repository.PayrollPeriodRepository;
import com.asmsi.admin_system.service.AuthUtil;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;

import static com.asmsi.admin_system.accounting.F.*;

/** Accounting › Dashboard, statements and closing; Reports › project and inventory reports. */
@Controller
public class FinanceController {

    private final FinanceService finance;
    private final Ledger ledger;
    private final BankAccountRepository banks;
    private final BillRepository bills;
    private final ClientBillingRepository clientBillings;
    private final PaymentVoucherRepository vouchers;
    private final PayrollPostingRepository postings;
    private final PayrollPeriodRepository periods;
    private final RemittanceService remit;
    private final YearCloseRepository closes;
    private final MaterialRepository materials;
    private final StockMovementRepository movements;
    private final Lookups lookups;
    private final AuthUtil auth;

    public FinanceController(FinanceService finance, Ledger ledger, BankAccountRepository banks, BillRepository bills, ClientBillingRepository clientBillings,
                             PaymentVoucherRepository vouchers, PayrollPostingRepository postings, PayrollPeriodRepository periods, RemittanceService remit,
                             YearCloseRepository closes, MaterialRepository materials, StockMovementRepository movements, Lookups lookups, AuthUtil auth) {
        this.finance = finance; this.ledger = ledger; this.banks = banks; this.bills = bills; this.clientBillings = clientBillings; this.vouchers = vouchers;
        this.postings = postings; this.periods = periods; this.remit = remit; this.closes = closes; this.materials = materials; this.movements = movements;
        this.lookups = lookups; this.auth = auth;
    }

    private String me() { var u = auth.getCurrentUser(); return u == null ? "system" : u.getName(); }

    // ================================================================== dashboard

    public record Cash(BankAccount bank, BigDecimal balance) {}

    @GetMapping("/Accounting/Dashboard")
    @Transactional(readOnly = true)
    public String dashboard(Model model) {
        LocalDate today = LocalDate.now();
        List<Cash> cash = new ArrayList<>();
        BigDecimal cashTotal = BigDecimal.ZERO;
        for (BankAccount b : banks.findByActiveTrueOrderByKindAscNameAsc()) { BigDecimal v = ledger.balanceOf(b.accountId, today); cash.add(new Cash(b, v)); cashTotal = cashTotal.add(v); }
        BigDecimal ar = ledger.balanceOf(ledger.id(Acct.AR_CLIENTS), today), ret = ledger.balanceOf(ledger.id(Acct.RETENTION_RECEIVABLE), today);
        BigDecimal ap = ledger.balanceOf(ledger.id(Acct.AP_SUPPLIERS), today).add(ledger.balanceOf(ledger.id(Acct.AP_SUBCONTRACTORS), today));
        List<Bill> openBills = bills.findByStatusInOrderByDueDateAscIdAsc(PayablesService.OPEN);
        List<Bill> dueSoon = openBills.stream().filter(b -> !b.dueDate.isAfter(today.plusDays(7))).limit(8).toList();
        BigDecimal dueSoonTotal = openBills.stream().filter(b -> !b.dueDate.isAfter(today.plusDays(7))).map(Bill::getBalance).reduce(BigDecimal.ZERO, BigDecimal::add);
        List<ClientBilling> overdue = clientBillings.findByStatusInOrderByDueDateAscIdAsc(List.of("ISSUED", "PARTIAL")).stream().filter(ClientBilling::isOverdue).toList();
        YearMonth m = YearMonth.now();
        var month = finance.incomeStatement(m.atDay(1), m.atEndOfMonth());
        var ytd = finance.incomeStatement(LocalDate.of(today.getYear(), 1, 1), today);
        List<FinanceService.JobProfit> jobs = finance.jobs(LocalDate.of(2000, 1, 1), today).stream()
                .filter(j -> j.revenue().signum() != 0 || j.getCost().signum() != 0).toList();
        List<FinanceService.JobProfit> watch = jobs.stream().filter(j -> !"OK".equals(j.getFlag())).toList();
        int unposted = 0;
        for (var p : periods.findAllByOrderByStartDateDesc()) if (p.isLocked() && postings.findFirstByPeriodIdAndStatusNot(p.getId(), "VOID").isEmpty()) unposted++;
        BigDecimal owed = remit.owed().values().stream().filter(v -> v.signum() > 0).reduce(BigDecimal.ZERO, BigDecimal::add);
        model.addAttribute("cash", cash); model.addAttribute("cashTotal", cashTotal);
        model.addAttribute("ar", ar); model.addAttribute("retention", ret); model.addAttribute("ap", ap);
        model.addAttribute("dueSoon", dueSoon); model.addAttribute("dueSoonTotal", dueSoonTotal);
        model.addAttribute("overdue", overdue);
        model.addAttribute("overdueTotal", overdue.stream().map(ClientBilling::getBalance).reduce(BigDecimal.ZERO, BigDecimal::add));
        model.addAttribute("month", month); model.addAttribute("ytd", ytd); model.addAttribute("monthLabel", m);
        model.addAttribute("jobs", jobs.stream().sorted(Comparator.comparing(FinanceService.JobProfit::getGrossProfit).reversed()).limit(6).toList());
        model.addAttribute("watch", watch);
        model.addAttribute("forApproval", vouchers.findByStatusInOrderByIdDesc(List.of("FOR_APPROVAL")).size() + clientBillings.findByStatusOrderByIdAsc("FOR_APPROVAL").size());
        model.addAttribute("toRelease", vouchers.findByStatusInOrderByIdDesc(List.of("APPROVED")).size());
        model.addAttribute("unpostedPayroll", unposted);
        model.addAttribute("taxOwed", owed);
        model.addAttribute("jobMap", lookups.jobMap()); model.addAttribute("supplierMap", lookups.supplierMap());
        model.addAttribute("activePage", "acc-dashboard");
        return "acc-dashboard";
    }

    // ================================================================== statements

    private static LocalDate[] period(String from, String to, String preset) {
        LocalDate today = LocalDate.now();
        if (preset != null) switch (preset) {
            case "month" -> { return new LocalDate[]{today.withDayOfMonth(1), YearMonth.now().atEndOfMonth()}; }
            case "lastmonth" -> { YearMonth p = YearMonth.now().minusMonths(1); return new LocalDate[]{p.atDay(1), p.atEndOfMonth()}; }
            case "quarter" -> { int q = (today.getMonthValue() - 1) / 3; LocalDate s = LocalDate.of(today.getYear(), q * 3 + 1, 1); return new LocalDate[]{s, s.plusMonths(3).minusDays(1)}; }
            case "year" -> { return new LocalDate[]{LocalDate.of(today.getYear(), 1, 1), LocalDate.of(today.getYear(), 12, 31)}; }
            case "lastyear" -> { return new LocalDate[]{LocalDate.of(today.getYear() - 1, 1, 1), LocalDate.of(today.getYear() - 1, 12, 31)}; }
            default -> { }
        }
        LocalDate t = Optional.ofNullable(date(to)).orElse(YearMonth.now().atEndOfMonth());
        LocalDate f = Optional.ofNullable(date(from)).orElse(t.withDayOfMonth(1));
        if (f.isAfter(t)) f = t.withDayOfMonth(1);
        return new LocalDate[]{f, t};
    }

    @GetMapping("/Accounting/IncomeStatement")
    @Transactional(readOnly = true)
    public String incomeStatement(@RequestParam(required = false) String from, @RequestParam(required = false) String to,
                                  @RequestParam(required = false) String preset, Model model) {
        LocalDate[] p = period(from, to, preset);
        model.addAttribute("s", finance.incomeStatement(p[0], p[1]));
        model.addAttribute("activePage", "acc-income");
        return "acc-income-statement";
    }

    @GetMapping("/Accounting/BalanceSheet")
    @Transactional(readOnly = true)
    public String balanceSheet(@RequestParam(required = false) String asOf, Model model) {
        model.addAttribute("s", finance.balanceSheet(Optional.ofNullable(date(asOf)).orElse(LocalDate.now())));
        model.addAttribute("activePage", "acc-balance-sheet");
        return "acc-balance-sheet";
    }

    @GetMapping("/Accounting/CashFlow")
    @Transactional(readOnly = true)
    public String cashFlow(@RequestParam(required = false) String from, @RequestParam(required = false) String to,
                           @RequestParam(required = false) String preset, Model model) {
        LocalDate[] p = period(from, to, preset);
        model.addAttribute("s", finance.cash(p[0], p[1]));
        model.addAttribute("activePage", "acc-cash-flow");
        return "acc-cash-flow";
    }

    // ================================================================== closing

    @GetMapping("/Accounting/Closing")
    @Transactional(readOnly = true)
    public String closing(Model model) {
        model.addAttribute("lock", finance.lock());
        model.addAttribute("closes", closes.findAllByOrderByYearDesc());
        model.addAttribute("lastMonthEnd", YearMonth.now().minusMonths(1).atEndOfMonth());
        model.addAttribute("lastYear", LocalDate.now().getYear() - 1);
        model.addAttribute("activePage", "acc-closing");
        return "acc-closing";
    }

    @PostMapping("/Accounting/Closing/lock")
    public String lock(@RequestParam(required = false) String through, RedirectAttributes ra) {
        LocalDate d = date(through);
        finance.lockThrough(d, me());
        ra.addFlashAttribute("success", d == null ? "All periods are open again." : "Books closed through " + d + ". Nothing can be posted on or before that date.");
        return "redirect:/Accounting/Closing";
    }

    @PostMapping("/Accounting/Closing/year")
    public String closeYear(@RequestParam(required = false) String year, RedirectAttributes ra) {
        try {
            YearClose c = finance.closeYear(Integer.parseInt(year.trim()), me());
            ra.addFlashAttribute("success", c.year + " closed: net " + (c.netIncome.signum() >= 0 ? "income" : "loss") + " of ₱" + String.format("%,.2f", c.netIncome.abs()) + " moved to retained earnings.");
        } catch (NumberFormatException | NullPointerException e) { ra.addFlashAttribute("error", "Enter the year to close.");
        } catch (IllegalArgumentException | IllegalStateException e) { ra.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/Accounting/Closing";
    }

    @PostMapping("/Accounting/Closing/year/{id}/reopen")
    public String reopen(@PathVariable Long id, RedirectAttributes ra) {
        closes.findById(id).ifPresent(c -> {
            try { finance.reopenYear(c, me()); ra.addFlashAttribute("success", c.year + " reopened; its closing entry was undone."); }
            catch (IllegalArgumentException | IllegalStateException e) { ra.addFlashAttribute("error", e.getMessage()); }
        });
        return "redirect:/Accounting/Closing";
    }

    // ================================================================== project reports

    @GetMapping("/Reports/ProjectProfitability")
    @Transactional(readOnly = true)
    public String profitability(Model model) {
        List<FinanceService.JobProfit> jobs = finance.jobs(LocalDate.of(2000, 1, 1), LocalDate.of(2999, 12, 31));
        BigDecimal rev = BigDecimal.ZERO, cost = BigDecimal.ZERO, contract = BigDecimal.ZERO, committed = BigDecimal.ZERO;
        for (var j : jobs) { rev = rev.add(j.revenue()); cost = cost.add(j.getCost()); contract = contract.add(FinanceService.z(j.contract())); committed = committed.add(j.committed()); }
        model.addAttribute("jobs", jobs);
        model.addAttribute("rev", rev); model.addAttribute("cost", cost); model.addAttribute("contract", contract); model.addAttribute("committed", committed);
        model.addAttribute("margin", FinanceService.pct(rev.subtract(cost), rev));
        model.addAttribute("activePage", "report-project-profitability");
        return "rpt-profitability";
    }

    @GetMapping("/Reports/ProjectProfitability/{jobId}")
    @Transactional(readOnly = true)
    public String jobProfit(@PathVariable Long jobId, Model model) {
        model.addAttribute("j", finance.job(jobId));
        model.addAttribute("months", finance.monthly(LocalDate.of(2000, 1, 1), LocalDate.of(2999, 12, 31)).stream().filter(r -> r.jobId().equals(jobId)).toList());
        model.addAttribute("activePage", "report-project-profitability");
        return "rpt-job-profit";
    }

    @GetMapping("/Reports/ProjectExpenseBreakdown")
    @Transactional(readOnly = true)
    public String breakdown(@RequestParam(required = false) String from, @RequestParam(required = false) String to, Model model) {
        LocalDate t = Optional.ofNullable(date(to)).orElse(LocalDate.now());
        LocalDate f = Optional.ofNullable(date(from)).orElse(LocalDate.of(t.getYear(), 1, 1));
        List<FinanceService.JobProfit> jobs = finance.jobs(f, t).stream().filter(j -> j.getCost().signum() != 0).toList();
        BigDecimal[] tot = new BigDecimal[7]; Arrays.fill(tot, BigDecimal.ZERO);
        for (var j : jobs) {
            tot[0] = tot[0].add(j.materials()); tot[1] = tot[1].add(j.labor()); tot[2] = tot[2].add(j.subcon()); tot[3] = tot[3].add(j.equipment());
            tot[4] = tot[4].add(j.otherDirect()); tot[5] = tot[5].add(j.siteExpenses()); tot[6] = tot[6].add(j.getCost());
        }
        model.addAttribute("jobs", jobs); model.addAttribute("t", tot); model.addAttribute("from", f); model.addAttribute("to", t);
        model.addAttribute("activePage", "report-project-expenses");
        return "rpt-expense-breakdown";
    }

    @GetMapping("/Reports/ProjectMonthlySummary")
    @Transactional(readOnly = true)
    public String monthly(@RequestParam(required = false) String year, Model model) {
        int y; try { y = Integer.parseInt(year); } catch (Exception e) { y = LocalDate.now().getYear(); }
        var rows = finance.monthly(LocalDate.of(y, 1, 1), LocalDate.of(y, 12, 31));
        Map<YearMonth, BigDecimal[]> byMonth = new TreeMap<>();
        for (var r : rows) {
            BigDecimal[] v = byMonth.computeIfAbsent(r.month(), k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            v[0] = v[0].add(r.revenue()); v[1] = v[1].add(r.cost());
        }
        model.addAttribute("rows", rows); model.addAttribute("byMonth", byMonth); model.addAttribute("year", y);
        model.addAttribute("jobMap", lookups.jobMap());
        model.addAttribute("activePage", "report-project-monthly");
        return "rpt-project-monthly";
    }

    // ================================================================== inventory reports

    @GetMapping("/Reports/InventorySummary")
    @Transactional(readOnly = true)
    public String inventorySummary(Model model) {
        List<Material> ms = materials.findAllByOrderByNameAsc();
        BigDecimal value = ms.stream().map(Material::getStockValue).reduce(BigDecimal.ZERO, BigDecimal::add);
        model.addAttribute("materials", ms); model.addAttribute("value", value);
        model.addAttribute("ledgerValue", ledger.balanceOf(ledger.id(Acct.INVENTORY), LocalDate.now()));
        model.addAttribute("low", ms.stream().filter(Material::isLow).count());
        model.addAttribute("categoryMap", lookups.categoryMap());
        model.addAttribute("activePage", "report-inventory-summary");
        return "rpt-inventory-summary";
    }

    public record MoveRow(Material material, BigDecimal opening, BigDecimal in, BigDecimal out, BigDecimal closing, BigDecimal inValue, BigDecimal outValue) {}

    @GetMapping("/Reports/InventoryMovement")
    @Transactional(readOnly = true)
    public String inventoryMovement(@RequestParam(required = false) String from, @RequestParam(required = false) String to, Model model) {
        LocalDate t = Optional.ofNullable(date(to)).orElse(LocalDate.now());
        LocalDate f = Optional.ofNullable(date(from)).orElse(t.withDayOfMonth(1));
        Map<Long, BigDecimal[]> m = new HashMap<>();
        for (StockMovement s : movements.findAll()) {
            BigDecimal[] v = m.computeIfAbsent(s.materialId, k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO});
            if (s.movedOn.isBefore(f)) v[0] = v[0].add(s.qty);
            else if (!s.movedOn.isAfter(t)) {
                if (s.qty.signum() > 0) { v[1] = v[1].add(s.qty); v[3] = v[3].add(s.getAmount()); }
                else { v[2] = v[2].add(s.qty.negate()); v[4] = v[4].add(s.getAmount().negate()); }
            }
        }
        List<MoveRow> rows = new ArrayList<>();
        for (Material mt : materials.findAllByOrderByNameAsc()) {
            BigDecimal[] v = m.get(mt.id);
            if (v == null) continue;
            if (v[1].signum() == 0 && v[2].signum() == 0 && v[0].signum() == 0) continue;
            rows.add(new MoveRow(mt, v[0], v[1], v[2], v[0].add(v[1]).subtract(v[2]), v[3], v[4]));
        }
        model.addAttribute("rows", rows); model.addAttribute("from", f); model.addAttribute("to", t);
        model.addAttribute("activePage", "report-inventory-movement");
        return "rpt-inventory-movement";
    }
}
