package com.asmsi.admin_system.accounting;

import com.asmsi.admin_system.inventory.Lookups;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static com.asmsi.admin_system.accounting.F.date;

/** Reports › Billing Monitoring (billed vs collected per job) and Payment Listing (client collections for a period). */
@Controller
public class BillingReportsController {
    private final ClientBillingService service;
    private final JobContractRepository contracts;
    private final ClientCollectionRepository collections;
    private final BankAccountRepository banks;
    private final Lookups lookups;
    @org.springframework.beans.factory.annotation.Autowired private AccountRepository accounts;
    @org.springframework.beans.factory.annotation.Autowired private Ledger ledger;
    @org.springframework.beans.factory.annotation.Autowired private SubconBillingRepository subconBillings;
    @org.springframework.beans.factory.annotation.Autowired private SubconContractRepository subconContracts;

    public BillingReportsController(ClientBillingService service, JobContractRepository contracts, ClientCollectionRepository collections,
                                    BankAccountRepository banks, Lookups lookups) {
        this.service = service; this.contracts = contracts; this.collections = collections; this.banks = banks; this.lookups = lookups;
    }

    private static LocalDate[] range(String from, String to) {
        LocalDate t = Optional.ofNullable(date(to)).orElse(LocalDate.now());
        LocalDate f = Optional.ofNullable(date(from)).orElse(t.withDayOfMonth(1));
        return new LocalDate[]{f, t};
    }

    public record ExpRow(Ledger.LedgerRow row, Account account) {}

    @GetMapping("/Reports/AdminExpenseListing")
    @Transactional(readOnly = true)
    public String adminExpenses(@RequestParam(required = false) String from, @RequestParam(required = false) String to, Model model) {
        LocalDate[] r = range(from, to);
        List<ExpRow> rows = new ArrayList<>();
        Map<Account, BigDecimal> byAcc = new LinkedHashMap<>();
        BigDecimal total = BigDecimal.ZERO;
        for (Account a : accounts.findAllByOrderByCodeAsc()) {
            if (!a.code.startsWith("6")) continue;
            for (Ledger.LedgerRow lr : ledger.accountLedger(a.id, r[0], r[1]).getValue()) {
                BigDecimal v = lr.line().debit.subtract(lr.line().credit);
                rows.add(new ExpRow(lr, a)); byAcc.merge(a, v, BigDecimal::add); total = total.add(v);
            }
        }
        rows.sort(Comparator.comparing((ExpRow x) -> x.row().entry().entryDate).thenComparing(x -> x.row().entry().id));
        model.addAttribute("rows", rows); model.addAttribute("byAcc", byAcc); model.addAttribute("total", total);
        model.addAttribute("from", r[0]); model.addAttribute("to", r[1]);
        model.addAttribute("jobMap", lookups.jobMap()); model.addAttribute("supplierMap", lookups.supplierMap());
        model.addAttribute("activePage", "report-admin-expenses");
        return "rpt-admin-expenses";
    }

    public record LaborRow(Lookups.JobRef job, BigDecimal amount) {}

    @GetMapping("/Reports/ProjectSalaryCost")
    @Transactional(readOnly = true)
    public String salaryCost(@RequestParam(required = false) String from, @RequestParam(required = false) String to, Model model) {
        LocalDate t = Optional.ofNullable(date(to)).orElse(LocalDate.now());
        LocalDate f = Optional.ofNullable(date(from)).orElse(t.withDayOfYear(1));
        var jm = lookups.jobMap();
        Map<Long, BigDecimal> byJob = new LinkedHashMap<>();
        BigDecimal office = BigDecimal.ZERO, total = BigDecimal.ZERO;
        for (Ledger.LedgerRow lr : ledger.accountLedger(ledger.id(Acct.DIRECT_LABOR), f, t).getValue()) {
            BigDecimal v = lr.line().debit.subtract(lr.line().credit);
            byJob.merge(lr.line().jobOrderId == null ? -1L : lr.line().jobOrderId, v, BigDecimal::add); total = total.add(v);
        }
        for (Ledger.LedgerRow lr : ledger.accountLedger(ledger.id(Acct.SALARIES_OFFICE), f, t).getValue()) office = office.add(lr.line().debit.subtract(lr.line().credit));
        List<LaborRow> rows = new ArrayList<>();
        byJob.forEach((k, v) -> rows.add(new LaborRow(jm.get(k), v)));
        rows.sort(Comparator.comparing((LaborRow x) -> x.amount()).reversed());
        model.addAttribute("rows", rows); model.addAttribute("total", total); model.addAttribute("office", office);
        model.addAttribute("from", f); model.addAttribute("to", t);
        model.addAttribute("activePage", "report-project-salary-cost");
        return "rpt-salary-cost";
    }

    @GetMapping("/Reports/SubconBillingListing")
    @Transactional(readOnly = true)
    public String subconListing(@RequestParam(required = false) String from, @RequestParam(required = false) String to, Model model) {
        LocalDate[] r = range(from, to);
        List<SubconBilling> rows = subconBillings.findTop200ByOrderByIdDesc().stream()
                .filter(b -> !b.billingDate.isBefore(r[0]) && !b.billingDate.isAfter(r[1]) && !"DRAFT".equals(b.status))
                .sorted(Comparator.comparing((SubconBilling b) -> b.billingDate)).toList();
        BigDecimal gross = BigDecimal.ZERO, due = BigDecimal.ZERO;
        for (SubconBilling b : rows) if (b.isPosted()) { gross = gross.add(b.gross); due = due.add(b.amountDue); }
        Map<Long, SubconContract> cm = new HashMap<>(); subconContracts.findAll().forEach(c -> cm.put(c.id, c));
        model.addAttribute("rows", rows); model.addAttribute("gross", gross); model.addAttribute("due", due);
        model.addAttribute("contractMap", cm);
        model.addAttribute("from", r[0]); model.addAttribute("to", r[1]);
        model.addAttribute("jobMap", lookups.jobMap()); model.addAttribute("supplierMap", lookups.supplierMap());
        model.addAttribute("activePage", "report-subcon-billings");
        return "rpt-subcon-billings";
    }

    public record Row(Lookups.JobRef job, JobContract c, ClientBillingService.Summary s) {}

    @GetMapping("/Reports/BillingMonitoring")
    @Transactional(readOnly = true)
    public String monitoring(Model model) {
        var jm = lookups.jobMap();
        List<Row> rows = new ArrayList<>();
        BigDecimal[] t = new BigDecimal[7];
        Arrays.fill(t, BigDecimal.ZERO);
        for (JobContract c : contracts.findAll()) {
            var j = jm.get(c.jobOrderId);
            if (j == null) continue;
            var s = service.summary(c.jobOrderId);
            rows.add(new Row(j, c, s));
            t[0] = t[0].add(s.contract()); t[1] = t[1].add(s.accomplished()); t[2] = t[2].add(s.billed()); t[3] = t[3].add(s.collected());
            t[4] = t[4].add(s.getBalance()); t[5] = t[5].add(s.getRetentionUnbilled()); t[6] = t[6].add(s.getUnbilled());
        }
        rows.sort(Comparator.comparing((Row r) -> r.job().number()));
        model.addAttribute("rows", rows);
        model.addAttribute("t", t);
        model.addAttribute("activePage", "report-billing-monitoring");
        return "rpt-billing-monitoring";
    }

    @GetMapping("/Reports/PaymentListing")
    @Transactional(readOnly = true)
    public String payments(@RequestParam(required = false) String from, @RequestParam(required = false) String to, Model model) {
        LocalDate t = Optional.ofNullable(date(to)).orElse(LocalDate.now());
        LocalDate f = Optional.ofNullable(date(from)).orElse(t.withDayOfMonth(1));
        List<ClientCollection> rows = collections.findAll().stream()
                .filter(x -> !x.receivedDate.isBefore(f) && !x.receivedDate.isAfter(t))
                .sorted(Comparator.comparing((ClientCollection x) -> x.receivedDate).thenComparing(x -> x.id)).toList();
        BigDecimal applied = BigDecimal.ZERO, cwt = BigDecimal.ZERO, cash = BigDecimal.ZERO;
        for (ClientCollection x : rows) if (x.isPosted()) { applied = applied.add(x.applied); cwt = cwt.add(x.cwt); cash = cash.add(x.cash); }
        Map<Long, BankAccount> bm = new HashMap<>(); banks.findAll().forEach(b -> bm.put(b.id, b));
        model.addAttribute("rows", rows); model.addAttribute("from", f); model.addAttribute("to", t);
        model.addAttribute("applied", applied); model.addAttribute("cwt", cwt); model.addAttribute("cash", cash);
        model.addAttribute("jobMap", lookups.jobMap()); model.addAttribute("bankMap", bm);
        model.addAttribute("activePage", "report-payments");
        return "rpt-payment-listing";
    }
}
