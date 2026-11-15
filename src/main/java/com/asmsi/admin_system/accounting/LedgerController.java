package com.asmsi.admin_system.accounting;

import com.asmsi.admin_system.inventory.Lookups;
import com.asmsi.admin_system.inventory.Material;
import com.asmsi.admin_system.inventory.MaterialRepository;
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
import java.util.*;

import static com.asmsi.admin_system.accounting.F.*;

/** Accounting › Chart of accounts, account ledgers, journal, banks &amp; cash, trial balance, opening balances. */
@Controller
@RequestMapping("/Accounting")
public class LedgerController {

    static final List<String> TYPES = List.of("ASSET", "LIABILITY", "EQUITY", "INCOME", "EXPENSE");

    private final AccountRepository accounts;
    private final JournalEntryRepository entries;
    private final JournalLineRepository lines;
    private final BankAccountRepository banks;
    private final MaterialRepository materials;
    private final Ledger ledger;
    private final Lookups lookups;
    private final AuthUtil auth;

    public LedgerController(AccountRepository accounts, JournalEntryRepository entries, JournalLineRepository lines, BankAccountRepository banks,
                            MaterialRepository materials, Ledger ledger, Lookups lookups, AuthUtil auth) {
        this.accounts = accounts; this.entries = entries; this.lines = lines; this.banks = banks; this.materials = materials;
        this.ledger = ledger; this.lookups = lookups; this.auth = auth;
    }

    private String me() { var u = auth.getCurrentUser(); return u == null ? "system" : u.getName(); }
    private Map<Long, Account> accountMap() { Map<Long, Account> m = new HashMap<>(); accounts.findAll().forEach(a -> m.put(a.id, a)); return m; }

    // ================================================================== chart of accounts

    @GetMapping("/Accounts")
    @Transactional(readOnly = true)
    public String accounts(Model model) {
        Map<Long, Ledger.Balance> bal = new HashMap<>();
        for (Ledger.Balance b : ledger.balances(LocalDate.now())) bal.put(b.account().id, b);
        Map<String, List<Account>> byType = new LinkedHashMap<>();
        for (String t : TYPES) byType.put(t, new ArrayList<>());
        for (Account a : accounts.findAllByOrderByCodeAsc()) byType.computeIfAbsent(a.type, k -> new ArrayList<>()).add(a);
        model.addAttribute("byType", byType);
        model.addAttribute("bal", bal);
        model.addAttribute("types", TYPES);
        model.addAttribute("activePage", "acc-accounts");
        return "acc-accounts";
    }

    @PostMapping("/Accounts")
    public String saveAccount(@RequestParam(required = false) Long id, @RequestParam(required = false) String code, @RequestParam(required = false) String name,
                              @RequestParam(required = false) String type, @RequestParam(required = false) String groupName,
                              @RequestParam(required = false) String description, @RequestParam(defaultValue = "false") boolean active, RedirectAttributes ra) {
        Account a = id == null ? new Account() : accounts.findById(id).orElse(null);
        if (a == null) return "redirect:/Accounting/Accounts";
        String c = cut(code, 12), n = cut(name, 120);
        if (c == null || n == null) { ra.addFlashAttribute("error", "Code and name are required."); return "redirect:/Accounting/Accounts"; }
        if (!c.matches("[0-9A-Za-z.-]+")) { ra.addFlashAttribute("error", "Use digits (and dots or dashes) for the account code."); return "redirect:/Accounting/Accounts"; }
        Account clash = accounts.findByCode(c).orElse(null);
        if (clash != null && !clash.id.equals(a.id)) { ra.addFlashAttribute("error", "Code " + c + " is already " + clash.name + "."); return "redirect:/Accounting/Accounts"; }
        if (a.systemAccount && !c.equals(a.code)) { ra.addFlashAttribute("error", a.code + " is used by the system; its code can't change (the name can)."); return "redirect:/Accounting/Accounts"; }
        a.code = c; a.name = n;
        if (!a.systemAccount && TYPES.contains(type)) a.type = type;
        if (a.type == null) a.type = "EXPENSE";
        a.groupName = cut(groupName, 60); a.description = cut(description, 300);
        a.active = a.systemAccount || active || id == null;
        accounts.save(a);
        ra.addFlashAttribute("success", "Account " + a.code + " · " + a.name + " saved.");
        return "redirect:/Accounting/Accounts";
    }

    @GetMapping("/Accounts/{id}")
    @Transactional(readOnly = true)
    public String accountLedger(@PathVariable Long id, @RequestParam(required = false) String from, @RequestParam(required = false) String to, Model model) {
        Account a = accounts.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        LocalDate t = Optional.ofNullable(date(to)).orElse(LocalDate.now());
        LocalDate f = Optional.ofNullable(date(from)).orElse(t.withDayOfMonth(1).minusMonths(2));
        var l = ledger.accountLedger(id, f, t);
        BigDecimal dr = BigDecimal.ZERO, cr = BigDecimal.ZERO;
        for (var r : l.getValue()) { dr = dr.add(r.line().debit); cr = cr.add(r.line().credit); }
        model.addAttribute("account", a);
        model.addAttribute("from", f); model.addAttribute("to", t);
        model.addAttribute("opening", l.getKey());
        model.addAttribute("rows", l.getValue());
        model.addAttribute("closing", l.getValue().isEmpty() ? l.getKey() : l.getValue().get(l.getValue().size() - 1).running());
        model.addAttribute("totalDr", dr); model.addAttribute("totalCr", cr);
        model.addAttribute("jobMap", lookups.jobMap());
        model.addAttribute("supplierMap", lookups.supplierMap());
        model.addAttribute("activePage", "acc-accounts");
        return "acc-ledger";
    }

    // ================================================================== journal

    @GetMapping("/Journal")
    @Transactional(readOnly = true)
    public String journal(@RequestParam(required = false) String source, Model model) {
        List<JournalEntry> es = entries.findTop300ByOrderByEntryDateDescIdDesc();
        if (!blank(source)) es = es.stream().filter(e -> source.equals(e.source)).toList();
        Map<Long, BigDecimal> totals = new HashMap<>();
        if (!es.isEmpty()) for (JournalLine l : lines.findByJournalIdIn(es.stream().map(e -> e.id).toList())) totals.merge(l.journalId, l.debit, BigDecimal::add);
        model.addAttribute("entries", es);
        model.addAttribute("totals", totals);
        model.addAttribute("source", source);
        model.addAttribute("sources", List.of("MANUAL", "BILLING", "COLLECTION", "BILL", "PAYMENT", "SUBCON", "PETTY", "ADVANCE", "PAYROLL", "REMITTANCE", "RECEIPT", "ISSUE", "ADJUSTMENT", "OPENING"));
        model.addAttribute("activePage", "acc-journal");
        return "acc-journal";
    }

    @GetMapping("/Journal/new")
    @Transactional(readOnly = true)
    public String newEntry(Model model) {
        model.addAttribute("accountList", accounts.findByActiveTrueOrderByCodeAsc());
        model.addAttribute("jobs", lookups.jobOrders());
        model.addAttribute("today", LocalDate.now());
        model.addAttribute("activePage", "acc-journal");
        return "acc-journal-form";
    }

    @PostMapping("/Journal")
    public String postEntry(@RequestParam(required = false) String entryDate, @RequestParam(required = false) String memo,
                            @RequestParam(name = "j_account", required = false) List<String> acc, @RequestParam(name = "j_memo", required = false) List<String> lm,
                            @RequestParam(name = "j_debit", required = false) List<String> dr, @RequestParam(name = "j_credit", required = false) List<String> cr,
                            @RequestParam(name = "j_job", required = false) List<String> job, RedirectAttributes ra) {
        List<JournalLine> in = new ArrayList<>();
        for (int i = 0; i < size(acc, dr, cr); i++) {
            Long a = id(at(acc, i)); BigDecimal d = num(at(dr, i)), c = num(at(cr, i));
            if (a == null || ((d == null || d.signum() == 0) && (c == null || c.signum() == 0))) continue;
            JournalLine l = new JournalLine(); l.accountId = a; l.debit = d == null ? BigDecimal.ZERO : d; l.credit = c == null ? BigDecimal.ZERO : c;
            l.memo = cut(at(lm, i), 300); l.jobOrderId = id(at(job, i));
            in.add(l);
        }
        if (blank(memo)) { ra.addFlashAttribute("error", "Give the entry a description (what is it for?)."); return "redirect:/Accounting/Journal/new"; }
        try {
            JournalEntry e = ledger.post(Optional.ofNullable(date(entryDate)).orElse(LocalDate.now()), cut(memo, 500), "MANUAL", null, null, in, me());
            ra.addFlashAttribute("success", "Journal entry " + e.number + " posted.");
            return "redirect:/Accounting/Journal/" + e.id;
        } catch (IllegalArgumentException | IllegalStateException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
            return "redirect:/Accounting/Journal/new";
        }
    }

    @GetMapping("/Journal/{id}")
    @Transactional(readOnly = true)
    public String entry(@PathVariable Long id, Model model) {
        JournalEntry e = entries.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        model.addAttribute("entry", e);
        model.addAttribute("jlines", lines.findByJournalIdOrderBySortOrderAscIdAsc(id));
        model.addAttribute("accountMap", accountMap());
        model.addAttribute("jobMap", lookups.jobMap());
        model.addAttribute("supplierMap", lookups.supplierMap());
        model.addAttribute("reverses", e.reversesId == null ? null : entries.findById(e.reversesId).orElse(null));
        model.addAttribute("reversedBy", e.reversedById == null ? null : entries.findById(e.reversedById).orElse(null));
        model.addAttribute("sourceLink", sourceLink(e));
        model.addAttribute("activePage", "acc-journal");
        return "acc-journal-entry";
    }

    static String sourceLink(JournalEntry e) {
        if (e.sourceId == null) return null;
        return switch (e.source) {
            case "BILL" -> "/Accounting/Payables/" + e.sourceId;
            case "BILLING" -> "/Accounting/Billing/" + e.sourceId;
            case "COLLECTION" -> "/Accounting/Collections/" + e.sourceId;
            case "SUBCON" -> "/Accounting/SubconBillings/" + e.sourceId;
            case "ADVANCE", "LIQUIDATION" -> "/Accounting/PettyCash/advance/" + e.sourceId;
            case "PETTY", "REPLENISH" -> "/Accounting/PettyCash";
            case "REMITTANCE" -> "/Accounting/Remittances";
            case "PAYMENT" -> "/Accounting/Payments/" + e.sourceId;
            case "RECEIPT" -> "/Inventory/StockReceiving/" + e.sourceId;
            case "ISSUE" -> "/Inventory/StockIssuance/" + e.sourceId;
            default -> null;
        };
    }

    @PostMapping("/Journal/{id}/void")
    public String voidEntry(@PathVariable Long id, @RequestParam(required = false) String reason, RedirectAttributes ra) {
        JournalEntry e = entries.findById(id).orElse(null);
        if (e == null) return "redirect:/Accounting/Journal";
        if (!"MANUAL".equals(e.source) && !"OPENING".equals(e.source)) {
            ra.addFlashAttribute("error", "This entry was posted by a " + e.source.toLowerCase() + "; void that document instead so both stay in step.");
            return "redirect:/Accounting/Journal/" + id;
        }
        try {
            JournalEntry r = ledger.reverse(e, LocalDate.now(), blank(reason) ? "Voided" : reason.trim(), me());
            ra.addFlashAttribute("success", e.number + " voided by reversing entry " + r.number + ".");
        } catch (IllegalStateException ex) { ra.addFlashAttribute("error", ex.getMessage()); }
        return "redirect:/Accounting/Journal/" + id;
    }

    // ================================================================== banks & cash

    public record BankRow(BankAccount bank, Account account, BigDecimal balance) {}

    @GetMapping("/Banks")
    @Transactional(readOnly = true)
    public String banks(Model model) {
        Map<Long, Account> am = accountMap();
        List<BankRow> rows = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (BankAccount b : banks.findAllByOrderByKindAscNameAsc()) {
            BigDecimal bal = ledger.balanceOf(b.accountId, LocalDate.now());
            rows.add(new BankRow(b, am.get(b.accountId), bal));
            if (b.active) total = total.add(bal);
        }
        model.addAttribute("rows", rows);
        model.addAttribute("total", total);
        model.addAttribute("activePage", "acc-banks");
        return "acc-banks";
    }

    @PostMapping("/Banks")
    @Transactional
    public String saveBank(@RequestParam(required = false) Long id, @RequestParam(required = false) String name, @RequestParam(required = false) String kind,
                           @RequestParam(required = false) String bankName, @RequestParam(required = false) String accountNo,
                           @RequestParam(required = false) String custodian, @RequestParam(required = false) String fundLimit,
                           @RequestParam(defaultValue = "false") boolean active, RedirectAttributes ra) {
        BankAccount b = id == null ? new BankAccount() : banks.findById(id).orElse(null);
        if (b == null) return "redirect:/Accounting/Banks";
        String n = cut(name, 120);
        if (n == null) { ra.addFlashAttribute("error", "Give the account a name, e.g. BDO Current – Ops."); return "redirect:/Accounting/Banks"; }
        b.name = n; b.custodian = cut(custodian, 120); b.fundLimit = num(fundLimit); b.bankName = cut(bankName, 80); b.accountNo = cut(accountNo, 40); b.active = active || id == null;
        if (id == null) {
            b.kind = kind != null && List.of("BANK", "CASH", "PETTY").contains(kind) ? kind : "BANK";
            // each bank, cash box or petty cash fund gets its own ledger account under Cash
            String base = "BANK".equals(b.kind) ? "103" : ("PETTY".equals(b.kind) ? "102" : "101");
            String code = null;
            for (int i = 1; i <= 9; i++) { String c = base + i; if (!accounts.existsByCode(c)) { code = c; break; } }
            if (code == null) { ra.addFlashAttribute("error", "No free account code left under " + base + "x; add the ledger account in Chart of accounts."); return "redirect:/Accounting/Banks"; }
            Account a = new Account();
            a.code = code; a.type = "ASSET"; a.groupName = "Cash";
            a.name = "BANK".equals(b.kind) ? "Cash in Bank – " + n : n;
            a.description = b.bankName == null ? null : b.bankName + (b.accountNo == null ? "" : " " + b.accountNo);
            accounts.save(a);
            b.accountId = a.id;
        } else {
            accounts.findById(b.accountId).ifPresent(a -> { if (!a.systemAccount) { a.name = "BANK".equals(b.kind) ? "Cash in Bank – " + n : n; accounts.save(a); } });
        }
        banks.save(b);
        ra.addFlashAttribute("success", b.name + " saved.");
        return "redirect:/Accounting/Banks";
    }

    /** Moves money between two of the company's own accounts (deposit, withdrawal, petty cash replenishment). */
    @PostMapping("/Banks/transfer")
    public String transfer(@RequestParam(required = false) String fromId, @RequestParam(required = false) String toId, @RequestParam(required = false) String amount,
                           @RequestParam(required = false) String date, @RequestParam(required = false) String memo, RedirectAttributes ra) {
        BankAccount f = banks.findById(Optional.ofNullable(id(fromId)).orElse(-1L)).orElse(null), t = banks.findById(Optional.ofNullable(id(toId)).orElse(-1L)).orElse(null);
        BigDecimal amt = num(amount);
        if (f == null || t == null || f.id.equals(t.id)) { ra.addFlashAttribute("error", "Choose two different accounts."); return "redirect:/Accounting/Banks"; }
        if (amt == null || amt.signum() <= 0) { ra.addFlashAttribute("error", "Enter the amount moved."); return "redirect:/Accounting/Banks"; }
        String m = blank(memo) ? "Transfer " + f.name + " → " + t.name : memo.trim();
        JournalEntry e = ledger.post(Optional.ofNullable(date(date)).orElse(LocalDate.now()), cut(m, 500), "MANUAL", null, "TRANSFER",
                List.of(JournalLine.dr(t.accountId, amt, t.name), JournalLine.cr(f.accountId, amt, f.name)), me());
        ra.addFlashAttribute("success", "₱" + String.format("%,.2f", amt) + " moved from " + f.name + " to " + t.name + " (" + e.number + ").");
        return "redirect:/Accounting/Banks";
    }

    // ================================================================== trial balance

    @GetMapping("/TrialBalance")
    @Transactional(readOnly = true)
    public String trialBalance(@RequestParam(required = false) String asOf, Model model) {
        LocalDate d = Optional.ofNullable(date(asOf)).orElse(LocalDate.now());
        List<Ledger.Balance> rows = new ArrayList<>(ledger.balances(d).stream()
                .filter(b -> b.debit().compareTo(b.credit()) != 0).toList());
        rows.sort(Comparator.comparing(b -> b.account().code));
        BigDecimal dr = BigDecimal.ZERO, cr = BigDecimal.ZERO;
        for (Ledger.Balance b : rows) { dr = dr.add(b.getDebitBalance()); cr = cr.add(b.getCreditBalance()); }
        model.addAttribute("rows", rows);
        model.addAttribute("asOf", d);
        model.addAttribute("totalDr", dr); model.addAttribute("totalCr", cr);
        model.addAttribute("balanced", dr.compareTo(cr) == 0);
        model.addAttribute("activePage", "acc-trial-balance");
        return "acc-trial-balance";
    }

    // ================================================================== opening balances

    @GetMapping("/Opening")
    @Transactional(readOnly = true)
    public String opening(Model model) {
        BigDecimal stock = BigDecimal.ZERO;
        for (Material m : materials.findAll()) stock = stock.add(m.getStockValue());
        Long inv = ledger.id(Acct.INVENTORY);
        BigDecimal gl = ledger.balanceOf(inv, LocalDate.now());
        model.addAttribute("stockValue", stock);
        model.addAttribute("glInventory", gl);
        model.addAttribute("diff", stock.subtract(gl));
        Map<Long, Account> am = accountMap();
        List<BankRow> rows = new ArrayList<>();
        for (BankAccount b : banks.findByActiveTrueOrderByKindAscNameAsc()) rows.add(new BankRow(b, am.get(b.accountId), ledger.balanceOf(b.accountId, LocalDate.now())));
        model.addAttribute("banks", rows);
        model.addAttribute("openingEntries", entries.findTop300ByOrderByEntryDateDescIdDesc().stream().filter(e -> "OPENING".equals(e.source)).toList());
        model.addAttribute("activePage", "acc-opening");
        return "acc-opening";
    }

    @PostMapping("/Opening/inventory")
    public String openingInventory(@RequestParam(required = false) String date, RedirectAttributes ra) {
        BigDecimal stock = BigDecimal.ZERO;
        for (Material m : materials.findAll()) stock = stock.add(m.getStockValue());
        Long inv = ledger.id(Acct.INVENTORY), obe = ledger.id(Acct.OPENING_BALANCE_EQUITY);
        BigDecimal diff = Ledger.r2(stock.subtract(ledger.balanceOf(inv, LocalDate.now())));
        if (diff.signum() == 0) { ra.addFlashAttribute("success", "Inventory in the ledger already matches the stock on hand."); return "redirect:/Accounting/Opening"; }
        List<JournalLine> ls = diff.signum() > 0
                ? List.of(JournalLine.dr(inv, diff, "Stock on hand before accounting"), JournalLine.cr(obe, diff, "Opening balance"))
                : List.of(JournalLine.dr(obe, diff.negate(), "Opening balance"), JournalLine.cr(inv, diff.negate(), "Stock on hand before accounting"));
        JournalEntry e = ledger.post(Optional.ofNullable(date(date)).orElse(LocalDate.now()), "Opening inventory: stock on hand brought into the ledger", "OPENING", null, "INVENTORY", ls, me());
        ra.addFlashAttribute("success", "Inventory brought into the ledger: ₱" + String.format("%,.2f", diff) + " (" + e.number + ").");
        return "redirect:/Accounting/Opening";
    }

    @PostMapping("/Opening/cash")
    public String openingCash(@RequestParam(required = false) String date, @RequestParam Map<String, String> form, RedirectAttributes ra) {
        Long obe = ledger.id(Acct.OPENING_BALANCE_EQUITY);
        List<JournalLine> ls = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (BankAccount b : banks.findByActiveTrueOrderByKindAscNameAsc()) {
            BigDecimal amt = num(form.get("amt_" + b.id));
            if (amt == null || amt.signum() == 0) continue;
            ls.add(amt.signum() > 0 ? JournalLine.dr(b.accountId, amt, b.name) : JournalLine.cr(b.accountId, amt.negate(), b.name));
            total = total.add(amt);
        }
        if (ls.isEmpty()) { ra.addFlashAttribute("error", "Enter at least one starting balance."); return "redirect:/Accounting/Opening"; }
        ls.add(total.signum() > 0 ? JournalLine.cr(obe, total, "Opening balance") : JournalLine.dr(obe, total.negate(), "Opening balance"));
        JournalEntry e = ledger.post(Optional.ofNullable(date(date)).orElse(LocalDate.now()), "Opening cash and bank balances", "OPENING", null, "CASH", ls, me());
        ra.addFlashAttribute("success", "Starting cash and bank balances posted (" + e.number + ").");
        return "redirect:/Accounting/Opening";
    }
}
