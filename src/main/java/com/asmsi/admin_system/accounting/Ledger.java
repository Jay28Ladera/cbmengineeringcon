package com.asmsi.admin_system.accounting;

import com.asmsi.admin_system.inventory.InventoryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * The general ledger: posts balanced journal entries, reverses them, and reads balances.
 * Every money event in the portal posts through here, so the books always balance.
 */
@Service
public class Ledger {

    private final JournalEntryRepository entries;
    private final JournalLineRepository lines;
    private final AccountRepository accounts;
    private final InventoryService numbers;

    public Ledger(JournalEntryRepository entries, JournalLineRepository lines, AccountRepository accounts, InventoryService numbers) {
        this.entries = entries; this.lines = lines; this.accounts = accounts; this.numbers = numbers;
    }

    public Long id(String code) {
        return accounts.findByCode(code).orElseThrow(() -> new IllegalStateException("Account " + code + " is missing from the chart of accounts.")).id;
    }

    public static BigDecimal r2(BigDecimal v) { return v == null ? BigDecimal.ZERO : v.setScale(2, RoundingMode.HALF_UP); }

    /** Posts an entry. Lines with zero amounts are dropped; debits must equal credits. */
    @Transactional
    public JournalEntry post(LocalDate date, String memo, String source, Long sourceId, String sourceRef, List<JournalLine> in, String by) {
        List<JournalLine> keep = new ArrayList<>();
        BigDecimal dr = BigDecimal.ZERO, cr = BigDecimal.ZERO;
        for (JournalLine l : in) {
            l.debit = r2(l.debit); l.credit = r2(l.credit);
            // a negative debit is a credit and vice versa; a line with both is netted
            if (l.debit.signum() < 0) { l.credit = l.credit.add(l.debit.negate()); l.debit = BigDecimal.ZERO; }
            if (l.credit.signum() < 0) { l.debit = l.debit.add(l.credit.negate()); l.credit = BigDecimal.ZERO; }
            if (l.debit.signum() > 0 && l.credit.signum() > 0) {
                BigDecimal n = l.debit.subtract(l.credit);
                l.debit = n.signum() > 0 ? n : BigDecimal.ZERO; l.credit = n.signum() < 0 ? n.negate() : BigDecimal.ZERO;
            }
            if (l.debit.signum() == 0 && l.credit.signum() == 0) continue;
            if (l.accountId == null) throw new IllegalArgumentException("Every line needs an account.");
            dr = dr.add(l.debit); cr = cr.add(l.credit);
            keep.add(l);
        }
        if (keep.size() < 2) throw new IllegalArgumentException("A journal entry needs at least one debit and one credit.");
        if (dr.compareTo(cr) != 0) throw new IllegalArgumentException("Debits (₱" + dr + ") and credits (₱" + cr + ") must be equal.");
        JournalEntry e = new JournalEntry();
        e.number = numbers.nextNumber("acc_journal", "JV");
        e.entryDate = date == null ? LocalDate.now() : date;
        e.memo = memo == null || memo.isBlank() ? source : (memo.length() > 500 ? memo.substring(0, 500) : memo);
        e.source = source; e.sourceId = sourceId; e.sourceRef = sourceRef; e.createdBy = by;
        entries.save(e);
        int n = 0;
        for (JournalLine l : keep) { l.journalId = e.id; l.sortOrder = n++; lines.save(l); }
        return e;
    }

    /** Voids an entry by posting its mirror image on the given date; both stay on record. */
    @Transactional
    public JournalEntry reverse(JournalEntry e, LocalDate date, String reason, String by) {
        if (!e.isPosted()) throw new IllegalStateException("Entry " + e.number + " is already void.");
        List<JournalLine> mirror = new ArrayList<>();
        for (JournalLine l : lines.findByJournalIdOrderBySortOrderAscIdAsc(e.id)) {
            JournalLine m = new JournalLine();
            m.accountId = l.accountId; m.debit = l.credit; m.credit = l.debit; m.memo = l.memo; m.jobOrderId = l.jobOrderId; m.supplierId = l.supplierId;
            mirror.add(m);
        }
        JournalEntry r = post(date == null ? LocalDate.now() : date, "Reversal of " + e.number + (reason == null || reason.isBlank() ? "" : ": " + reason),
                "REVERSAL", e.id, e.number, mirror, by);
        r.reversesId = e.id;
        entries.save(r);
        e.status = "VOID"; e.reversedById = r.id; e.voidReason = reason;
        entries.save(e);
        return r;
    }

    /** Reverses the posted entry of a document, if there is one (used when a bill, payment or issue is voided). */
    @Transactional
    public void reverseSource(String source, Long sourceId, String reason, String by) {
        entries.findFirstBySourceAndSourceIdAndStatusOrderByIdDesc(source, sourceId, "POSTED").ifPresent(e -> reverse(e, LocalDate.now(), reason, by));
    }

    // ------------------------------------------------------------------ balances

    public record Balance(Account account, BigDecimal debit, BigDecimal credit) {
        /** Signed balance in the account's normal direction (positive = normal). */
        public BigDecimal getBalance() { return account.isDebitNormal() ? debit.subtract(credit) : credit.subtract(debit); }
        /** Trial balance columns. */
        public BigDecimal getDebitBalance() { BigDecimal n = debit.subtract(credit); return n.signum() > 0 ? n : BigDecimal.ZERO; }
        public BigDecimal getCreditBalance() { BigDecimal n = credit.subtract(debit); return n.signum() > 0 ? n : BigDecimal.ZERO; }
    }

    /** Every account with its totals as of a date, in code order (accounts without activity included). */
    @Transactional(readOnly = true)
    public List<Balance> balances(LocalDate asOf) {
        return toBalances(lines.totalsAsOf(asOf));
    }

    @Transactional(readOnly = true)
    public List<Balance> activity(LocalDate from, LocalDate to) {
        return toBalances(lines.totalsBetween(from, to));
    }

    private List<Balance> toBalances(List<Object[]> rows) {
        Map<Long, BigDecimal[]> t = new HashMap<>();
        for (Object[] r : rows) t.put((Long) r[0], new BigDecimal[]{(BigDecimal) r[1], (BigDecimal) r[2]});
        List<Balance> out = new ArrayList<>();
        for (Account a : accounts.findAllByOrderByCodeAsc()) {
            BigDecimal[] v = t.getOrDefault(a.id, new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            out.add(new Balance(a, v[0], v[1]));
        }
        return out;
    }

    /** Balance of one account (normal direction) as of a date. */
    public BigDecimal balanceOf(Long accountId, LocalDate asOf) {
        Account a = accounts.findById(accountId).orElseThrow();
        BigDecimal net = lines.netBefore(accountId, asOf.plusDays(1));
        net = net == null ? BigDecimal.ZERO : net;
        return a.isDebitNormal() ? net : net.negate();
    }

    public record LedgerRow(JournalLine line, JournalEntry entry, BigDecimal running) {}

    /** One account's movements between two dates with a running balance (normal direction), and the opening balance. */
    @Transactional(readOnly = true)
    public Map.Entry<BigDecimal, List<LedgerRow>> accountLedger(Long accountId, LocalDate from, LocalDate to) {
        Account a = accounts.findById(accountId).orElseThrow();
        BigDecimal net = lines.netBefore(accountId, from);
        BigDecimal open = net == null ? BigDecimal.ZERO : (a.isDebitNormal() ? net : net.negate());
        BigDecimal run = open;
        List<LedgerRow> rows = new ArrayList<>();
        for (Object[] r : lines.ledger(accountId, from, to)) {
            JournalLine l = (JournalLine) r[0];
            BigDecimal delta = a.isDebitNormal() ? l.debit.subtract(l.credit) : l.credit.subtract(l.debit);
            run = run.add(delta);
            rows.add(new LedgerRow(l, (JournalEntry) r[1], run));
        }
        return Map.entry(open, rows);
    }

    public static LocalDateTime now() { return LocalDateTime.now(); }
}
