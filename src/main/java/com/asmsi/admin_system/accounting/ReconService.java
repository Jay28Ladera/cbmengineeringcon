package com.asmsi.admin_system.accounting;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static com.asmsi.admin_system.accounting.ClientBillingService.*;

/**
 * Bank reconciliation: tick the ledger lines that appear on the bank statement (or import the statement and let them match);
 * opening + cleared = the statement's ending balance. What is left unticked is outstanding cheques and deposits in transit.
 */
@Service
public class ReconService {

    private final BankReconRepository recons;
    private final StatementLineRepository stmt;
    private final JournalLineRepository lines;
    private final BankAccountRepository banks;
    private final Ledger ledger;

    public ReconService(BankReconRepository recons, StatementLineRepository stmt, JournalLineRepository lines, BankAccountRepository banks, Ledger ledger) {
        this.recons = recons; this.stmt = stmt; this.lines = lines; this.banks = banks; this.ledger = ledger;
    }

    public record Row(JournalLine line, JournalEntry entry, boolean cleared) {
        public BigDecimal getAmount() { return line.debit.subtract(line.credit); }
    }
    public record View(BankRecon recon, BankAccount bank, List<Row> rows, BigDecimal cleared, BigDecimal clearedBalance, BigDecimal difference,
                       BigDecimal book, BigDecimal outstandingCheques, BigDecimal depositsInTransit, int outstandingCount, int transitCount) {
        public boolean isBalanced() { return difference.signum() == 0; }
        /** Statement + deposits in transit − outstanding cheques, which must equal the book balance. */
        public BigDecimal getAdjustedBank() { return recon.statementBalance.add(depositsInTransit).subtract(outstandingCheques); }
    }

    @Transactional
    public BankRecon start(Long bankId, LocalDate date, BigDecimal statementBalance, String by) {
        BankAccount b = banks.findById(bankId == null ? -1L : bankId).orElseThrow(() -> new IllegalArgumentException("Choose the bank account."));
        Optional<BankRecon> draft = recons.findFirstByBankIdAndStatus(b.id, "DRAFT");
        if (draft.isPresent()) return draft.get();
        if (date == null) throw new IllegalArgumentException("Enter the statement's ending date.");
        if (statementBalance == null) throw new IllegalArgumentException("Enter the statement's ending balance.");
        BankRecon last = recons.findFirstByBankIdAndStatusOrderByStatementDateDescIdDesc(b.id, "DONE").orElse(null);
        if (last != null && !date.isAfter(last.statementDate)) throw new IllegalArgumentException("The last reconciliation was for " + last.statementDate + ". Choose a later statement date.");
        BankRecon r = new BankRecon();
        r.bankId = b.id; r.statementDate = date; r.statementBalance = r2(statementBalance);
        r.openingBalance = last == null ? BigDecimal.ZERO : last.statementBalance; r.createdBy = by;
        return recons.save(r);
    }

    @Transactional(readOnly = true)
    public View view(BankRecon r) {
        BankAccount b = banks.findById(r.bankId).orElseThrow();
        List<Row> rows = new ArrayList<>();
        BigDecimal cleared = BigDecimal.ZERO, out = BigDecimal.ZERO, transit = BigDecimal.ZERO;
        int oc = 0, tc = 0;
        for (Object[] x : lines.forRecon(b.accountId, r.statementDate, r.id)) {
            JournalLine l = (JournalLine) x[0]; JournalEntry e = (JournalEntry) x[1];
            boolean c = r.id.equals(l.reconId);
            rows.add(new Row(l, e, c));
            BigDecimal amt = l.debit.subtract(l.credit);
            if (c) cleared = cleared.add(amt);
            else if (amt.signum() < 0) { out = out.add(amt.negate()); oc++; }
            else if (amt.signum() > 0) { transit = transit.add(amt); tc++; }
        }
        BigDecimal cb = r.openingBalance.add(cleared);
        return new View(r, b, rows, cleared, cb, r.statementBalance.subtract(cb), ledger.balanceOf(b.accountId, r.statementDate), out, transit, oc, tc);
    }

    @Transactional
    public void tick(BankRecon r, Set<Long> ticked, BigDecimal statementBalance) {
        if (r.isDone()) throw new IllegalStateException("This reconciliation is finished. Reopen it to change it.");
        if (statementBalance != null) { r.statementBalance = r2(statementBalance); recons.save(r); }
        BankAccount b = banks.findById(r.bankId).orElseThrow();
        for (Object[] x : lines.forRecon(b.accountId, r.statementDate, r.id)) {
            JournalLine l = (JournalLine) x[0];
            Long want = ticked.contains(l.id) ? r.id : null;
            if (!Objects.equals(want, l.reconId)) { l.reconId = want; lines.save(l); }
        }
    }

    /** Bank charges or interest seen on the statement but not yet in the books: posted and cleared at once. */
    @Transactional
    public void adjust(BankRecon r, String kind, LocalDate date, BigDecimal amount, String memo, String by) {
        if (r.isDone()) throw new IllegalStateException("This reconciliation is finished.");
        if (amount == null || amount.signum() <= 0) throw new IllegalArgumentException("Enter the amount.");
        BankAccount b = banks.findById(r.bankId).orElseThrow();
        LocalDate d = date == null || date.isAfter(r.statementDate) ? r.statementDate : date;
        BigDecimal a = r2(amount);
        boolean interest = "INTEREST".equals(kind);
        String m = memo == null || memo.isBlank() ? (interest ? "Interest earned" : "Bank service charge") : memo.trim();
        JournalLine bankLine = interest ? JournalLine.dr(b.accountId, a, m) : JournalLine.cr(b.accountId, a, m);
        JournalLine other = interest ? JournalLine.cr(ledger.id(Acct.OTHER_INCOME), a, m) : JournalLine.dr(ledger.id("6130"), a, m);
        JournalEntry e = ledger.post(d, m + " · " + b.name + " statement " + r.statementDate, "BANKADJ", r.id, "RECON", List.of(bankLine, other), by);
        for (JournalLine l : lines.findByJournalIdOrderBySortOrderAscIdAsc(e.id)) if (l.accountId.equals(b.accountId)) { l.reconId = r.id; lines.save(l); }
    }

    @Transactional
    public void complete(BankRecon r, String by) {
        View v = view(r);
        if (!v.isBalanced()) throw new IllegalStateException("Still ₱" + peso(v.difference().abs()) + " off. Tick the items on the statement, or add the bank charges or interest.");
        r.status = "DONE"; r.completedBy = by; r.completedAt = LocalDateTime.now();
        recons.save(r);
    }

    @Transactional
    public void reopen(BankRecon r) {
        if (!r.isDone()) return;
        BankRecon last = recons.findFirstByBankIdAndStatusOrderByStatementDateDescIdDesc(r.bankId, "DONE").orElse(null);
        if (last == null || !last.id.equals(r.id)) throw new IllegalStateException("Only the latest reconciliation can be reopened.");
        if (recons.findFirstByBankIdAndStatus(r.bankId, "DRAFT").isPresent()) throw new IllegalStateException("Finish or delete the reconciliation in progress first.");
        r.status = "DRAFT"; r.completedAt = null; r.completedBy = null;
        recons.save(r);
    }

    @Transactional
    public void delete(BankRecon r) {
        if (r.isDone()) throw new IllegalStateException("Reopen it first.");
        for (JournalLine l : lines.findByReconId(r.id)) { l.reconId = null; lines.save(l); }
        stmt.deleteAll(stmt.findByReconIdOrderByTxnDateAscIdAsc(r.id));
        recons.delete(r);
    }

    // ================================================================== statement import

    public record ImportResult(int lines, int matched) {}

    /** Reads a CSV exported from online banking and ticks the ledger lines it finds (same amount, close date). */
    @Transactional
    public ImportResult importCsv(BankRecon r, InputStream in) throws IOException {
        if (r.isDone()) throw new IllegalStateException("This reconciliation is finished.");
        List<List<String>> rows = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String s;
            while ((s = br.readLine()) != null) { if (s.startsWith("﻿")) s = s.substring(1); if (!s.isBlank()) rows.add(csv(s)); }
        }
        int head = -1; Map<String, Integer> col = new HashMap<>();
        for (int i = 0; i < Math.min(rows.size(), 15) && head < 0; i++) {
            Map<String, Integer> c = columns(rows.get(i));
            if (c.containsKey("date") && (c.containsKey("amount") || c.containsKey("debit") || c.containsKey("credit"))) { head = i; col = c; }
        }
        if (head < 0) throw new IllegalArgumentException("Couldn't find the columns. The file needs a header row with a date column and either an amount column or debit/withdrawal and credit/deposit columns. Save the bank's Excel file as CSV first.");
        stmt.deleteAll(stmt.findByReconIdOrderByTxnDateAscIdAsc(r.id));
        List<StatementLine> got = new ArrayList<>();
        for (int i = head + 1; i < rows.size(); i++) {
            List<String> row = rows.get(i);
            LocalDate d = parseDate(get(row, col.get("date")));
            if (d == null) continue;
            BigDecimal amt;
            if (col.containsKey("amount") && !col.containsKey("debit")) amt = money(get(row, col.get("amount")));
            else {
                BigDecimal dr = money(get(row, col.get("debit"))), cr = money(get(row, col.get("credit")));
                amt = (cr == null ? BigDecimal.ZERO : cr.abs()).subtract(dr == null ? BigDecimal.ZERO : dr.abs());
            }
            if (amt == null || amt.signum() == 0) continue;
            StatementLine sl = new StatementLine();
            sl.reconId = r.id; sl.txnDate = d; sl.amount = r2(amt);
            sl.description = cut(get(row, col.get("description")), 300); sl.reference = cut(get(row, col.get("reference")), 60);
            got.add(stmt.save(sl));
        }
        return new ImportResult(got.size(), match(r));
    }

    /** Matches unmatched statement lines to uncleared ledger lines: same amount, ledger date from 7 days before to 60 days before/after the bank date. */
    @Transactional
    public int match(BankRecon r) {
        BankAccount b = banks.findById(r.bankId).orElseThrow();
        List<Object[]> cand = lines.forRecon(b.accountId, r.statementDate, r.id);
        Set<Long> used = new HashSet<>();
        List<StatementLine> sls = stmt.findByReconIdOrderByTxnDateAscIdAsc(r.id);
        for (StatementLine s : sls) if (s.matchedLineId != null) used.add(s.matchedLineId);
        int n = 0;
        for (StatementLine s : sls) {
            if (s.matchedLineId != null || s.ignored) continue;
            JournalLine best = null; long bestGap = Long.MAX_VALUE;
            for (Object[] x : cand) {
                JournalLine l = (JournalLine) x[0]; JournalEntry e = (JournalEntry) x[1];
                if (used.contains(l.id) || (l.reconId != null && !l.reconId.equals(r.id))) continue;
                if (l.debit.subtract(l.credit).compareTo(s.amount) != 0) continue;
                long gap = ChronoUnit.DAYS.between(e.entryDate, s.txnDate);
                if (gap < -7 || gap > 60) continue;
                boolean ref = s.reference != null && !s.reference.isBlank() && e.memo != null && e.memo.contains(s.reference.trim());
                long score = ref ? -1 : Math.abs(gap);
                if (score < bestGap) { best = l; bestGap = score; }
            }
            if (best != null) {
                s.matchedLineId = best.id; stmt.save(s); used.add(best.id); n++;
                if (best.reconId == null) { best.reconId = r.id; lines.save(best); }
            }
        }
        return n;
    }

    @Transactional
    public void ignore(StatementLine s, boolean ignore) { s.ignored = ignore; stmt.save(s); }

    // ------------------------------------------------------------------ parsing helpers

    static List<String> csv(String line) {
        List<String> out = new ArrayList<>(); StringBuilder b = new StringBuilder(); boolean q = false;
        char sep = line.indexOf('\t') >= 0 && line.indexOf(',') < 0 ? '\t' : (line.indexOf(';') >= 0 && line.indexOf(',') < 0 ? ';' : ',');
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') { if (q && i + 1 < line.length() && line.charAt(i + 1) == '"') { b.append('"'); i++; } else q = !q; }
            else if (c == sep && !q) { out.add(b.toString().trim()); b.setLength(0); }
            else b.append(c);
        }
        out.add(b.toString().trim());
        return out;
    }

    static Map<String, Integer> columns(List<String> header) {
        Map<String, Integer> m = new HashMap<>();
        for (int i = 0; i < header.size(); i++) {
            String h = header.get(i).toLowerCase(Locale.ROOT).replaceAll("[^a-z ]", " ").trim();
            if (h.isEmpty()) continue;
            if (!m.containsKey("date") && (h.equals("date") || h.contains("posting date") || h.contains("transaction date") || h.contains("txn date") || h.contains("trans date") || h.contains("value date") || h.equals("posted")))
                m.put("date", i);
            else if (!m.containsKey("debit") && (h.contains("debit") || h.contains("withdraw") || h.equals("dr") || h.contains("amount out") || h.contains("money out")))
                m.put("debit", i);
            else if (!m.containsKey("credit") && (h.contains("credit") || h.contains("deposit") || h.equals("cr") || h.contains("amount in") || h.contains("money in")))
                m.put("credit", i);
            else if (!m.containsKey("amount") && h.equals("amount")) m.put("amount", i);
            else if (!m.containsKey("description") && (h.contains("description") || h.contains("particular") || h.contains("detail") || h.contains("narrat") || h.contains("remark") || h.contains("transaction")))
                m.put("description", i);
            else if (!m.containsKey("reference") && (h.contains("ref") || h.contains("check") || h.contains("cheque")))
                m.put("reference", i);
        }
        return m;
    }

    private static String get(List<String> row, Integer i) { return i == null || i >= row.size() ? null : row.get(i); }

    private static final List<DateTimeFormatter> DATES = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE, DateTimeFormatter.ofPattern("M/d/yyyy"), DateTimeFormatter.ofPattern("M/d/yy"),
            DateTimeFormatter.ofPattern("d-MMM-yyyy", Locale.ENGLISH), DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH), DateTimeFormatter.ofPattern("MMM d yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("d/M/yyyy"), DateTimeFormatter.ofPattern("yyyy/M/d"), DateTimeFormatter.ofPattern("M-d-yyyy"));

    static LocalDate parseDate(String s) {
        if (s == null || s.isBlank()) return null;
        String t = s.trim();
        if (t.length() > 10 && t.matches("\\d{4}-\\d{2}-\\d{2}[ T].*")) t = t.substring(0, 10);
        for (DateTimeFormatter f : DATES) { try { return LocalDate.parse(t, f); } catch (Exception ignored) { } }
        return null;
    }

    static BigDecimal money(String s) {
        if (s == null) return null;
        String t = s.trim().replace(",", "").replace("₱", "").replace("PHP", "").replace("Php", "").trim();
        if (t.isEmpty() || t.equals("-")) return null;
        boolean neg = t.startsWith("(") && t.endsWith(")");
        if (neg) t = t.substring(1, t.length() - 1);
        boolean dr = t.toUpperCase().endsWith("DR"), cr = t.toUpperCase().endsWith("CR");
        if (dr || cr) t = t.substring(0, t.length() - 2).trim();
        try { BigDecimal v = new BigDecimal(t); if (neg || dr) v = v.abs().negate(); return v; } catch (NumberFormatException e) { return null; }
    }
}
