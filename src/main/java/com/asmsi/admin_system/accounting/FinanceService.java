package com.asmsi.admin_system.accounting;

import com.asmsi.admin_system.entity.JobOrder;
import com.asmsi.admin_system.entity.Quotation;
import com.asmsi.admin_system.inventory.PurchaseOrder;
import com.asmsi.admin_system.inventory.PurchaseOrderLine;
import com.asmsi.admin_system.inventory.PurchaseOrderLineRepository;
import com.asmsi.admin_system.inventory.PurchaseOrderRepository;
import com.asmsi.admin_system.repository.JobOrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.*;

import static com.asmsi.admin_system.accounting.ClientBillingService.*;

/** Financial statements, job profitability, cash summary and year-end closing, all read from the ledger. */
@Service
public class FinanceService {

    private final Ledger ledger;
    private final AccountRepository accounts;
    private final JournalLineRepository lines;
    private final JournalEntryRepository entries;
    private final BankAccountRepository banks;
    private final JobOrderRepository jobs;
    private final JobContractRepository contracts;
    private final ClientBillingService billing;
    private final PurchaseOrderRepository orders;
    private final PurchaseOrderLineRepository orderLines;
    private final SubconContractRepository subcontracts;
    private final SubconService subcon;
    private final PeriodLockRepository locks;
    private final YearCloseRepository closes;

    public FinanceService(Ledger ledger, AccountRepository accounts, JournalLineRepository lines, JournalEntryRepository entries, BankAccountRepository banks,
                          JobOrderRepository jobs, JobContractRepository contracts, ClientBillingService billing, PurchaseOrderRepository orders,
                          PurchaseOrderLineRepository orderLines, SubconContractRepository subcontracts, SubconService subcon,
                          PeriodLockRepository locks, YearCloseRepository closes) {
        this.ledger = ledger; this.accounts = accounts; this.lines = lines; this.entries = entries; this.banks = banks; this.jobs = jobs;
        this.contracts = contracts; this.billing = billing; this.orders = orders; this.orderLines = orderLines; this.subcontracts = subcontracts;
        this.subcon = subcon; this.locks = locks; this.closes = closes;
    }

    static BigDecimal z(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }
    static BigDecimal pct(BigDecimal part, BigDecimal whole) {
        return whole == null || whole.signum() == 0 ? null : part.multiply(HUNDRED).divide(whole, 1, RoundingMode.HALF_UP);
    }

    // ================================================================== income statement

    public record Line(Account account, BigDecimal amount, BigDecimal prior) {
        public BigDecimal getChange() { return amount.subtract(prior); }
    }
    public record Section(String title, List<Line> lines, BigDecimal total, BigDecimal prior) {}
    public record IncomeStatement(LocalDate from, LocalDate to, LocalDate priorFrom, LocalDate priorTo, Section revenue, Section direct, Section opex,
                                  Section other, BigDecimal grossProfit, BigDecimal grossPrior, BigDecimal operating, BigDecimal operatingPrior,
                                  BigDecimal net, BigDecimal netPrior) {
        public BigDecimal getGrossMargin() { return pct(grossProfit, revenue.total()); }
        public BigDecimal getNetMargin() { return pct(net, revenue.total()); }
    }

    @Transactional(readOnly = true)
    public IncomeStatement incomeStatement(LocalDate from, LocalDate to) {
        long days = java.time.temporal.ChronoUnit.DAYS.between(from, to) + 1;
        LocalDate pt = from.minusDays(1), pf;
        // compare like with like: whole months against the previous months, otherwise the same number of days
        if (from.getDayOfMonth() == 1 && to.equals(YearMonth.from(to).atEndOfMonth())) {
            long months = java.time.temporal.ChronoUnit.MONTHS.between(YearMonth.from(from), YearMonth.from(to)) + 1;
            pf = from.minusMonths(months);
        } else pf = pt.minusDays(days - 1);
        Map<Long, BigDecimal> cur = net(ledger.operating(from, to)), pri = net(ledger.operating(pf, pt));
        List<Line> rev = new ArrayList<>(), dir = new ArrayList<>(), op = new ArrayList<>(), oth = new ArrayList<>();
        for (Account a : accounts.findAllByOrderByCodeAsc()) {
            if (!"INCOME".equals(a.type) && !"EXPENSE".equals(a.type)) continue;
            BigDecimal c = z(cur.get(a.id)), p = z(pri.get(a.id));
            if (c.signum() == 0 && p.signum() == 0) continue;
            Line l = new Line(a, c, p);
            if ("INCOME".equals(a.type)) (Acct.OTHER_INCOME.equals(a.code) ? oth : rev).add(l);
            else (a.code.startsWith("5") ? dir : op).add(l);
        }
        Section r = section("Revenue", rev), d = section("Cost of contracts", dir), o = section("Operating expenses", op), x = section("Other income", oth);
        BigDecimal gp = r.total().subtract(d.total()), gpp = r.prior().subtract(d.prior());
        BigDecimal oi = gp.subtract(o.total()), oip = gpp.subtract(o.prior());
        return new IncomeStatement(from, to, pf, pt, r, d, o, x, gp, gpp, oi, oip, oi.add(x.total()), oip.add(x.prior()));
    }

    /** Signed in each account's normal direction (income: credit − debit; expense: debit − credit). */
    private static Map<Long, BigDecimal> net(List<Ledger.Balance> bs) {
        Map<Long, BigDecimal> m = new HashMap<>();
        for (Ledger.Balance b : bs) m.put(b.account().id, b.getBalance());
        return m;
    }

    private static Section section(String title, List<Line> ls) {
        BigDecimal t = BigDecimal.ZERO, p = BigDecimal.ZERO;
        for (Line l : ls) { t = t.add(l.amount()); p = p.add(l.prior()); }
        return new Section(title, ls, t, p);
    }

    // ================================================================== balance sheet

    public record Group(String name, List<Ledger.Balance> rows, BigDecimal total) {}
    public record BalanceSheet(LocalDate asOf, List<Group> assets, BigDecimal totalAssets, List<Group> liabilities, BigDecimal totalLiabilities,
                               List<Group> equity, BigDecimal earnings, BigDecimal totalEquity) {
        public boolean isBalanced() { return totalAssets.compareTo(totalLiabilities.add(totalEquity)) == 0; }
    }

    @Transactional(readOnly = true)
    public BalanceSheet balanceSheet(LocalDate asOf) {
        List<Ledger.Balance> all = ledger.balances(asOf);
        BigDecimal earnings = BigDecimal.ZERO;
        for (Ledger.Balance b : all) {
            if ("INCOME".equals(b.account().type)) earnings = earnings.add(b.getBalance());
            if ("EXPENSE".equals(b.account().type)) earnings = earnings.subtract(b.getBalance());
        }
        List<Group> as = groups(all, "ASSET"), li = groups(all, "LIABILITY"), eq = groups(all, "EQUITY");
        BigDecimal ta = total(as), tl = total(li), te = total(eq).add(earnings);
        return new BalanceSheet(asOf, as, ta, li, tl, eq, earnings, te);
    }

    private static List<Group> groups(List<Ledger.Balance> all, String type) {
        Map<String, List<Ledger.Balance>> m = new LinkedHashMap<>();
        for (Ledger.Balance b : all) {
            if (!type.equals(b.account().type) || b.getBalance().signum() == 0) continue;
            m.computeIfAbsent(b.account().groupName == null ? "Other" : b.account().groupName, k -> new ArrayList<>()).add(b);
        }
        List<Group> out = new ArrayList<>();
        m.forEach((k, v) -> out.add(new Group(k, v, v.stream().map(Ledger.Balance::getBalance).reduce(BigDecimal.ZERO, BigDecimal::add))));
        return out;
    }
    private static BigDecimal total(List<Group> gs) { return gs.stream().map(Group::total).reduce(BigDecimal.ZERO, BigDecimal::add); }

    // ================================================================== cash

    public record BankFlow(BankAccount bank, BigDecimal opening, BigDecimal in, BigDecimal out, BigDecimal closing) {}
    public record Flow(String label, BigDecimal in, BigDecimal out) { public BigDecimal getNet() { return in.subtract(out); } }
    public record CashSummary(LocalDate from, LocalDate to, List<BankFlow> banks, List<Flow> flows, BigDecimal opening, BigDecimal closing,
                              BigDecimal in, BigDecimal out, BigDecimal transfers) {}

    static String flowLabel(String source) {
        return switch (source) {
            case "COLLECTION" -> "Collections from clients";
            case "PAYMENT" -> "Payments to suppliers and subcontractors";
            case "PAYROLL_PAY" -> "Salaries paid";
            case "REMITTANCE" -> "Taxes and contributions remitted";
            case "PETTY", "ADVANCE", "LIQUIDATION" -> "Petty cash and cash advances";
            case "OPENING" -> "Opening balances";
            case "ASSET", "ASSET_DISPOSAL" -> "Equipment bought and sold";
            default -> "Other entries";
        };
    }

    @Transactional(readOnly = true)
    public CashSummary cash(LocalDate from, LocalDate to) {
        List<BankAccount> bs = banks.findAllByOrderByKindAscNameAsc();
        Set<Long> cashAccts = new HashSet<>();
        bs.forEach(b -> cashAccts.add(b.accountId));
        List<BankFlow> bf = new ArrayList<>();
        BigDecimal open = BigDecimal.ZERO, close = BigDecimal.ZERO;
        Map<Long, BigDecimal[]> io = new HashMap<>();
        Map<Long, BigDecimal> netByEntry = new LinkedHashMap<>();
        Map<Long, String> srcByEntry = new HashMap<>();
        for (Object[] r : lines.onAccounts(cashAccts.isEmpty() ? List.of(-1L) : cashAccts, from, to)) {
            JournalLine l = (JournalLine) r[0]; JournalEntry e = (JournalEntry) r[1];
            io.computeIfAbsent(l.accountId, k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            io.get(l.accountId)[0] = io.get(l.accountId)[0].add(l.debit);
            io.get(l.accountId)[1] = io.get(l.accountId)[1].add(l.credit);
            netByEntry.merge(e.id, l.debit.subtract(l.credit), BigDecimal::add);
            String src = e.source;
            if ("REVERSAL".equals(src)) src = entries.findById(e.reversesId == null ? -1L : e.reversesId).map(x -> x.source).orElse("MANUAL");
            srcByEntry.put(e.id, src);
        }
        for (BankAccount b : bs) {
            BigDecimal o = ledger.balanceOf(b.accountId, from.minusDays(1));
            BigDecimal[] v = io.getOrDefault(b.accountId, new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            BigDecimal c = o.add(v[0]).subtract(v[1]);
            if (!b.active && o.signum() == 0 && c.signum() == 0) continue;
            bf.add(new BankFlow(b, o, v[0], v[1], c));
            open = open.add(o); close = close.add(c);
        }
        // money moved between CBM's own accounts nets to zero in an entry: shown apart, not as income or spending
        Map<String, BigDecimal[]> by = new LinkedHashMap<>();
        BigDecimal in = BigDecimal.ZERO, out = BigDecimal.ZERO, transfers = BigDecimal.ZERO;
        for (var e : netByEntry.entrySet()) {
            BigDecimal n = e.getValue();
            if (n.signum() == 0) { transfers = transfers.add(BigDecimal.ONE); continue; }
            String label = flowLabel(srcByEntry.get(e.getKey()));
            by.computeIfAbsent(label, k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            if (n.signum() > 0) { by.get(label)[0] = by.get(label)[0].add(n); in = in.add(n); }
            else { by.get(label)[1] = by.get(label)[1].add(n.negate()); out = out.add(n.negate()); }
        }
        List<Flow> fl = new ArrayList<>();
        by.forEach((k, v) -> fl.add(new Flow(k, v[0], v[1])));
        fl.sort(Comparator.comparing((Flow f) -> f.in().add(f.out())).reversed());
        return new CashSummary(from, to, bf, fl, open, close, in, out, transfers);
    }

    // ================================================================== job profitability

    public record BudgetRow(String label, BigDecimal budget, BigDecimal actual) {
        public BigDecimal getRemaining() { return budget == null ? null : budget.subtract(actual); }
        public BigDecimal getUsedPct() { return budget == null ? null : pct(actual, budget); }
        public boolean isOver() { return budget != null && actual.compareTo(budget) > 0; }
    }

    /** One job's figures (amounts net of VAT). */
    public record JobProfit(Long jobId, String number, String label, String status, BigDecimal contract, BigDecimal pctComplete,
                            BigDecimal revenue, BigDecimal materials, BigDecimal labor, BigDecimal subcon, BigDecimal equipment,
                            BigDecimal otherDirect, BigDecimal siteExpenses, BigDecimal budget, BigDecimal budgetMaterials, BigDecimal budgetLabor,
                            BigDecimal budgetSubcon, BigDecimal budgetOther, BigDecimal committed) {
        public BigDecimal getCost() { return materials.add(labor).add(subcon).add(equipment).add(otherDirect).add(siteExpenses); }
        public BigDecimal getGrossProfit() { return revenue.subtract(getCost()); }
        public BigDecimal getMargin() { return pct(getGrossProfit(), revenue); }
        public BigDecimal getBudgetMargin() { return contract == null || budget == null ? null : pct(contract.subtract(budget), contract); }
        public BigDecimal getCostPctOfBudget() { return budget == null ? null : pct(getCost(), budget); }
        /** Cost to finish if the rest of the work costs like the work done so far. */
        public BigDecimal getEstimateAtCompletion() {
            if (pctComplete == null || pctComplete.signum() <= 0) return budget;
            BigDecimal byRate = getCost().multiply(HUNDRED).divide(pctComplete, 2, RoundingMode.HALF_UP);
            return pctComplete.compareTo(HUNDRED) >= 0 ? getCost() : byRate;
        }
        public BigDecimal getProjectedProfit() { return contract == null || getEstimateAtCompletion() == null ? null : contract.subtract(getEstimateAtCompletion()); }
        public BigDecimal getProjectedMargin() { return getProjectedProfit() == null ? null : pct(getProjectedProfit(), contract); }
        public List<BudgetRow> getBudgetRows() {
            return List.of(new BudgetRow("Materials", budgetMaterials, materials), new BudgetRow("Labor", budgetLabor, labor),
                    new BudgetRow("Subcontract", budgetSubcon, subcon),
                    new BudgetRow("Equipment, other direct and site", budgetOther, equipment.add(otherDirect).add(siteExpenses)));
        }
        /** OVER = cost running ahead of progress; LOSS = projected to lose money. */
        public String getFlag() {
            BigDecimal pm = getProjectedMargin();
            if (pm != null && pm.signum() < 0) return "LOSS";
            BigDecimal cp = getCostPctOfBudget();
            if (cp != null && pctComplete != null && cp.subtract(pctComplete).compareTo(BigDecimal.TEN) > 0) return "OVER";
            if (budget != null && getCost().compareTo(budget) > 0) return "OVER";
            return "OK";
        }
    }

    @Transactional(readOnly = true)
    public List<JobProfit> jobs(LocalDate from, LocalDate to) {
        Map<Long, Map<String, BigDecimal>> m = jobCodes(lines.byJob(from, to));
        List<JobProfit> out = new ArrayList<>();
        for (JobOrder j : jobs.findAll()) {
            if ("CANCELLED".equalsIgnoreCase(String.valueOf(j.getStatus())) && !m.containsKey(j.getId())) continue;
            out.add(profit(j, m.getOrDefault(j.getId(), Map.of())));
        }
        out.sort(Comparator.comparing(JobProfit::number, Comparator.nullsLast(Comparator.naturalOrder())));
        return out;
    }

    @Transactional(readOnly = true)
    public JobProfit job(Long jobId) {
        JobOrder j = jobs.findById(jobId).orElseThrow();
        return profit(j, jobCodes(lines.byJob(LocalDate.of(2000, 1, 1), LocalDate.of(2999, 12, 31))).getOrDefault(jobId, Map.of()));
    }

    private Map<Long, Map<String, BigDecimal>> jobCodes(List<Object[]> rows) {
        Map<Long, Account> am = new HashMap<>();
        accounts.findAll().forEach(a -> am.put(a.id, a));
        Map<Long, Map<String, BigDecimal>> m = new HashMap<>();
        for (Object[] r : rows) {
            Account a = am.get((Long) r[1]);
            if (a == null || (!"INCOME".equals(a.type) && !"EXPENSE".equals(a.type))) continue;
            BigDecimal dr = (BigDecimal) r[2], cr = (BigDecimal) r[3];
            BigDecimal v = "INCOME".equals(a.type) ? cr.subtract(dr) : dr.subtract(cr);
            m.computeIfAbsent((Long) r[0], k -> new HashMap<>()).merge(bucket(a), v, BigDecimal::add);
        }
        return m;
    }

    static String bucket(Account a) {
        if ("INCOME".equals(a.type)) return "revenue";
        return switch (a.code) {
            case Acct.MATERIALS_USED -> "materials"; case Acct.DIRECT_LABOR -> "labor"; case Acct.SUBCONTRACT_COSTS -> "subcon";
            case "5040" -> "equipment";
            default -> a.code.startsWith("5") ? "otherDirect" : "site";
        };
    }

    private JobProfit profit(JobOrder j, Map<String, BigDecimal> v) {
        JobContract c = contracts.findById(j.getId()).orElse(null);
        Quotation q = j.getQuotation();
        BigDecimal contract = null;
        if (c != null) contract = c.isVat() ? c.contractAmount.subtract(vatIn(c.contractAmount)) : c.contractAmount;
        else if (q != null) contract = q.getNetSelling();
        BigDecimal pctDone = c == null ? null : billing.summary(j.getId()).pct();
        BigDecimal bm = null, bl = null, bs = null, bo = null, budget = null;
        if (q != null && q.getTotalCost().signum() > 0) {
            bm = q.getCostOf("MATERIAL"); bl = q.getCostOf("LABOR"); bs = q.getCostOf("SUBCON");
            bo = q.getTotalCost().subtract(bm).subtract(bl).subtract(bs); budget = q.getTotalCost();
        }
        // committed: ordered but not yet received, and subcontract work not yet billed (net of VAT)
        BigDecimal committed = BigDecimal.ZERO;
        for (PurchaseOrder po : orders.findAll()) {
            if (!j.getId().equals(po.jobOrderId) || !"ORDERED".equals(po.status) && !"PARTIAL".equals(po.status)) continue;
            for (PurchaseOrderLine l : orderLines.findByPoIdOrderBySortOrderAscIdAsc(po.id)) {
                BigDecimal open = l.qty.subtract(l.receivedQty).max(BigDecimal.ZERO).multiply(l.unitPrice);
                committed = committed.add(po.isVatInclusive() ? open.subtract(vatIn(open)) : open);
            }
        }
        for (SubconContract sc : subcontracts.findByJobOrderIdOrderByIdAsc(j.getId())) {
            if (!"ACTIVE".equals(sc.status)) continue;
            BigDecimal left = subcon.summary(sc).getUnbilled();
            committed = committed.add(sc.isVat() ? left.subtract(vatIn(left)) : left);
        }
        String label = (j.getJobOrderNumber() == null ? "JO #" + j.getId() : j.getJobOrderNumber())
                + (q != null && q.getInquiry() != null ? " · " + q.getInquiry().getProjectName() : "");
        return new JobProfit(j.getId(), j.getJobOrderNumber(), label, String.valueOf(j.getStatus()), contract, pctDone,
                z(v.get("revenue")), z(v.get("materials")), z(v.get("labor")), z(v.get("subcon")), z(v.get("equipment")), z(v.get("otherDirect")),
                z(v.get("site")), budget, bm, bl, bs, bo, r2(committed));
    }

    /** Revenue and cost per job and month. */
    public record MonthRow(YearMonth month, Long jobId, BigDecimal revenue, BigDecimal cost) {
        public BigDecimal getProfit() { return revenue.subtract(cost); }
    }

    @Transactional(readOnly = true)
    public List<MonthRow> monthly(LocalDate from, LocalDate to) {
        Map<Long, Account> am = new HashMap<>();
        accounts.findAll().forEach(a -> am.put(a.id, a));
        Map<String, BigDecimal[]> m = new TreeMap<>();
        for (Object[] r : lines.byJobMonth(from, to)) {
            Account a = am.get((Long) r[1]);
            if (a == null || (!"INCOME".equals(a.type) && !"EXPENSE".equals(a.type))) continue;
            Long job = (Long) r[0]; int y = ((Number) r[2]).intValue(), mo = ((Number) r[3]).intValue();
            BigDecimal dr = (BigDecimal) r[4], cr = (BigDecimal) r[5];
            String k = String.format("%04d-%02d|%d", y, mo, job);
            BigDecimal[] v = m.computeIfAbsent(k, x -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            if ("INCOME".equals(a.type)) v[0] = v[0].add(cr.subtract(dr)); else v[1] = v[1].add(dr.subtract(cr));
        }
        List<MonthRow> out = new ArrayList<>();
        m.forEach((k, v) -> { String[] p = k.split("\\|"); out.add(new MonthRow(YearMonth.parse(p[0]), Long.valueOf(p[1]), v[0], v[1])); });
        return out;
    }

    // ================================================================== closing

    public PeriodLock lock() { return locks.findById(1L).orElse(null); }

    @Transactional
    public void lockThrough(LocalDate date, String by) {
        PeriodLock l = locks.findById(1L).orElseGet(PeriodLock::new);
        l.lockedThrough = date; l.lockedBy = by; l.lockedAt = LocalDateTime.now();
        locks.save(l);
    }

    /** Closes a year's income and expenses into retained earnings with an entry dated December 31. */
    @Transactional
    public YearClose closeYear(int year, String by) {
        if (closes.findFirstByYearAndStatus(year, "CLOSED").isPresent()) throw new IllegalStateException(year + " is already closed.");
        if (closes.findFirstByYearAndStatus(year - 1, "CLOSED").isEmpty()
                && !ledger.operating(LocalDate.of(1900, 1, 1), LocalDate.of(year - 1, 12, 31)).stream().allMatch(b -> b.debit().compareTo(b.credit()) == 0 || !(isPl(b.account()))))
            throw new IllegalStateException("Close " + (year - 1) + " first: it has income or expenses that were not closed.");
        LocalDate end = LocalDate.of(year, 12, 31);
        if (LocalDate.now().isBefore(end)) throw new IllegalStateException(year + " is not over yet.");
        List<JournalLine> j = new ArrayList<>();
        BigDecimal net = BigDecimal.ZERO;
        for (Ledger.Balance b : ledger.operating(LocalDate.of(year, 1, 1), end)) {
            if (!isPl(b.account())) continue;
            BigDecimal d = b.debit().subtract(b.credit());
            if (d.signum() == 0) continue;
            // post the opposite to bring the account to zero for the year
            j.add(d.signum() > 0 ? JournalLine.cr(b.account().id, d, "Closed to retained earnings") : JournalLine.dr(b.account().id, d.negate(), "Closed to retained earnings"));
            net = net.subtract(d);
        }
        if (j.isEmpty()) throw new IllegalStateException("No income or expenses in " + year + ".");
        Long re = ledger.id(Acct.RETAINED_EARNINGS);
        j.add(net.signum() >= 0 ? JournalLine.cr(re, net, "Net income " + year) : JournalLine.dr(re, net.negate(), "Net loss " + year));
        JournalEntry e = ledger.post(end, "Year-end closing " + year + ": income and expenses to retained earnings", "CLOSING", null, "YEAR-END " + year, j, by);
        YearClose c = new YearClose();
        c.year = year; c.netIncome = net; c.journalId = e.id; c.closedBy = by;
        closes.save(c);
        if (lock() == null || lock().lockedThrough == null || lock().lockedThrough.isBefore(end)) lockThrough(end, by);
        return c;
    }

    @Transactional
    public void reopenYear(YearClose c, String by) {
        if (!"CLOSED".equals(c.status)) return;
        if (closes.findFirstByYearAndStatus(c.year + 1, "CLOSED").isPresent()) throw new IllegalStateException("Reopen " + (c.year + 1) + " first.");
        JournalEntry e = entries.findById(c.journalId).orElseThrow();
        List<JournalLine> mirror = new ArrayList<>();
        for (JournalLine l : lines.findByJournalIdOrderBySortOrderAscIdAsc(e.id)) {
            JournalLine m = new JournalLine(); m.accountId = l.accountId; m.debit = l.credit; m.credit = l.debit; m.memo = "Reopened"; mirror.add(m);
        }
        JournalEntry r = ledger.post(e.entryDate, "Reopening " + c.year + " (closing " + e.number + " undone)", "CLOSING", null, "REOPEN " + c.year, mirror, by);
        r.reversesId = e.id; entries.save(r);
        e.status = "VOID"; e.reversedById = r.id; entries.save(e);
        c.status = "REOPENED"; closes.save(c);
        PeriodLock l = lock();
        if (l != null && l.lockedThrough != null && !l.lockedThrough.isBefore(LocalDate.of(c.year, 1, 1))) lockThrough(LocalDate.of(c.year - 1, 12, 31), by);
    }

    static boolean isPl(Account a) { return "INCOME".equals(a.type) || "EXPENSE".equals(a.type); }
}
