package com.asmsi.admin_system.accounting;

import com.asmsi.admin_system.inventory.Supplier;
import com.asmsi.admin_system.inventory.SupplierRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

import static com.asmsi.admin_system.accounting.ClientBillingService.*;

/**
 * BIR figures from the books, by quarter: BIR 2307 for each supplier and subcontractor (tax withheld when paid),
 * the 1601-EQ / QAP summary by ATC, creditable tax withheld by clients (SAWT) and the VAT summary for 2550Q.
 */
@Service
public class BirService {

    private final PaymentVoucherRepository vouchers;
    private final VoucherLineRepository voucherLines;
    private final BillRepository bills;
    private final SupplierRepository suppliers;
    private final ClientCollectionRepository collections;
    private final JobContractRepository contracts;
    private final ClientBillingService billing;
    private final JournalLineRepository lines;
    private final JournalEntryRepository entries;
    private final Ledger ledger;

    public BirService(PaymentVoucherRepository vouchers, VoucherLineRepository voucherLines, BillRepository bills, SupplierRepository suppliers,
                      ClientCollectionRepository collections, JobContractRepository contracts, ClientBillingService billing, JournalLineRepository lines,
                      JournalEntryRepository entries, Ledger ledger) {
        this.vouchers = vouchers; this.voucherLines = voucherLines; this.bills = bills; this.suppliers = suppliers; this.collections = collections;
        this.contracts = contracts; this.billing = billing; this.lines = lines; this.entries = entries; this.ledger = ledger;
    }

    public record Quarter(int year, int q) {
        public LocalDate from() { return LocalDate.of(year, (q - 1) * 3 + 1, 1); }
        public LocalDate to() { return from().plusMonths(3).minusDays(1); }
        public int monthOf(LocalDate d) { return d.getMonthValue() - (q - 1) * 3 - 1; }
        public String getLabel() { return "Q" + q + " " + year; }
        public List<String> getMonths() {
            List<String> m = new ArrayList<>();
            for (int i = 0; i < 3; i++) m.add(from().plusMonths(i).getMonth().getDisplayName(java.time.format.TextStyle.SHORT, Locale.ENGLISH));
            return m;
        }
        public static Quarter of(LocalDate d) { return new Quarter(d.getYear(), (d.getMonthValue() - 1) / 3 + 1); }
    }

    /** ATC descriptions (Expanded withholding tax), as printed on BIR 2307. */
    public static final Map<String, String> ATC = Map.ofEntries(
            Map.entry("WC158", "Income payments made by top withholding agents to their local/resident supplier of goods"),
            Map.entry("WI158", "Income payments made by top withholding agents to their local/resident supplier of goods"),
            Map.entry("WC160", "Income payments made by top withholding agents to their local/resident supplier of services"),
            Map.entry("WI160", "Income payments made by top withholding agents to their local/resident supplier of services"),
            Map.entry("WC120", "Income payments to contractors"),
            Map.entry("WI120", "Income payments to contractors"),
            Map.entry("WC100", "Rentals: real/personal properties, poles, satellites and transmission facilities, billboards"),
            Map.entry("WI100", "Rentals: real/personal properties, poles, satellites and transmission facilities, billboards"),
            Map.entry("WC010", "Professional fees (juridical persons)"),
            Map.entry("WI010", "Professional fees (individuals)"),
            Map.entry("WC011", "Professional fees (juridical persons), higher rate"),
            Map.entry("WI011", "Professional fees (individuals), higher rate"));

    public static String atc(Supplier s, BigDecimal rate) {
        if (s != null && s.atc != null && !s.atc.isBlank()) return s.atc.trim().toUpperCase();
        String p = s != null && s.isIndividual() ? "WI" : "WC";
        int r = rate == null ? 0 : rate.intValue();
        return p + switch (r) {
            case 1 -> "158";
            case 2 -> s != null && s.isSubcon() ? "120" : "160";
            case 5 -> "100";
            case 10 -> "010";
            case 15 -> "011";
            default -> "160";
        };
    }

    // ================================================================== 2307 issued (tax CBM withheld from payees)

    public static class Payee {
        public final Supplier supplier; public final String atc; public final BigDecimal rate;
        public final BigDecimal[] base = {BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO};
        public final BigDecimal[] tax = {BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO};
        public final List<String> vouchers = new ArrayList<>();
        Payee(Supplier s, String atc, BigDecimal rate) { this.supplier = s; this.atc = atc; this.rate = rate; }
        public Supplier getSupplier() { return supplier; }
        public String getAtc() { return atc; }
        public String getAtcLabel() { return ATC.getOrDefault(atc, ""); }
        public BigDecimal getRate() { return rate; }
        public BigDecimal getBase(int i) { return base[i]; }
        public BigDecimal getTax(int i) { return tax[i]; }
        public BigDecimal getTotalBase() { return base[0].add(base[1]).add(base[2]); }
        public BigDecimal getTotalTax() { return tax[0].add(tax[1]).add(tax[2]); }
        public List<String> getVouchers() { return vouchers; }
    }

    /** Released vouchers in the quarter with tax withheld, by payee and ATC; the base is the amount paid net of VAT. */
    @Transactional(readOnly = true)
    public List<Payee> withheld(Quarter q) {
        Map<String, Payee> m = new LinkedHashMap<>();
        for (PaymentVoucher v : vouchers.findByStatusInOrderByIdDesc(List.of("RELEASED"))) {
            if (v.releaseDate == null || v.releaseDate.isBefore(q.from()) || v.releaseDate.isAfter(q.to())) continue;
            Supplier s = suppliers.findById(v.supplierId).orElse(null);
            for (VoucherLine l : voucherLines.findByVoucherId(v.id)) {
                if (l.ewt.signum() <= 0) continue;
                Bill b = bills.findById(l.billId).orElse(null);
                BigDecimal base = b == null || b.gross.signum() == 0 ? l.gross
                        : r2(l.gross.multiply(b.getNetOfVat()).divide(b.gross, 8, RoundingMode.HALF_UP));
                String a = atc(s, l.ewtRate);
                Payee p = m.computeIfAbsent(v.supplierId + "|" + a, k -> new Payee(s, a, l.ewtRate));
                int i = q.monthOf(v.releaseDate);
                p.base[i] = p.base[i].add(base); p.tax[i] = p.tax[i].add(l.ewt);
                if (!p.vouchers.contains(v.number)) p.vouchers.add(v.number);
            }
        }
        List<Payee> out = new ArrayList<>(m.values());
        out.sort(Comparator.comparing((Payee p) -> p.supplier == null ? "" : p.supplier.name));
        return out;
    }

    public record AtcRow(String atc, String label, BigDecimal rate, BigDecimal[] base, BigDecimal[] tax) {
        public BigDecimal getTotalBase() { return base[0].add(base[1]).add(base[2]); }
        public BigDecimal getTotalTax() { return tax[0].add(tax[1]).add(tax[2]); }
    }

    /** 1601-EQ: tax withheld by ATC and month. */
    public List<AtcRow> byAtc(List<Payee> payees) {
        Map<String, AtcRow> m = new TreeMap<>();
        for (Payee p : payees) {
            AtcRow r = m.computeIfAbsent(p.atc, k -> new AtcRow(k, ATC.getOrDefault(k, ""), p.rate,
                    new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO}, new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO}));
            for (int i = 0; i < 3; i++) { r.base()[i] = r.base()[i].add(p.base[i]); r.tax()[i] = r.tax()[i].add(p.tax[i]); }
        }
        return new ArrayList<>(m.values());
    }

    // ================================================================== tax withheld by clients (SAWT)

    public record ClientCwt(Long jobId, String client, String tin, String atc, BigDecimal rate, BigDecimal base, BigDecimal tax,
                            List<ClientCollection> collections, boolean all2307) {}

    @Transactional(readOnly = true)
    public List<ClientCwt> clientCwt(Quarter q) {
        Map<Long, List<ClientCollection>> byJob = new LinkedHashMap<>();
        for (ClientCollection x : collections.findAll()) {
            if (!x.isPosted() || x.cwt.signum() <= 0 || x.receivedDate.isBefore(q.from()) || x.receivedDate.isAfter(q.to())) continue;
            byJob.computeIfAbsent(x.jobOrderId, k -> new ArrayList<>()).add(x);
        }
        List<ClientCwt> out = new ArrayList<>();
        byJob.forEach((job, xs) -> {
            JobContract c = contracts.findById(job).orElse(null);
            BigDecimal base = BigDecimal.ZERO, tax = BigDecimal.ZERO;
            for (ClientCollection x : xs) { base = base.add(c != null && c.isVat() ? x.applied.subtract(vatIn(x.applied)) : x.applied); tax = tax.add(x.cwt); }
            BigDecimal rate = c == null ? BigDecimal.valueOf(2) : c.cwtRate;
            int r = rate.intValue();
            String a = r == 1 ? "WC158" : r == 5 ? "WC100" : "WC120";
            String name = c == null || c.billTo == null ? billing.jobLabel(job) : c.billTo.split(" · Attn")[0];
            out.add(new ClientCwt(job, name, c == null ? null : c.clientTin, a, rate, r2(base), tax, xs, xs.stream().allMatch(x -> x.form2307)));
        });
        return out;
    }

    // ================================================================== VAT (2550Q)

    public record VatMonth(String month, BigDecimal output, BigDecimal input) {
        public BigDecimal getSales() { return output.multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP); }
        public BigDecimal getPurchases() { return input.multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP); }
        public BigDecimal getPayable() { return output.subtract(input); }
    }

    /** Output and input VAT booked per month (remittances left out). */
    @Transactional(readOnly = true)
    public List<VatMonth> vat(Quarter q) {
        Long out = ledger.id(Acct.OUTPUT_VAT), in = ledger.id(Acct.INPUT_VAT);
        BigDecimal[] o = {BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO}, i = {BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO};
        for (Object[] r : lines.onAccounts(List.of(out, in), q.from(), q.to())) {
            JournalLine l = (JournalLine) r[0]; JournalEntry e = (JournalEntry) r[1];
            String src = e.source;
            if ("REVERSAL".equals(src)) src = entries.findById(e.reversesId == null ? -1L : e.reversesId).map(x -> x.source).orElse("");
            if ("REMITTANCE".equals(src)) continue;
            int m = q.monthOf(e.entryDate);
            if (l.accountId.equals(out)) o[m] = o[m].add(l.credit.subtract(l.debit));
            else i[m] = i[m].add(l.debit.subtract(l.credit));
        }
        List<VatMonth> res = new ArrayList<>();
        for (int k = 0; k < 3; k++) res.add(new VatMonth(q.getMonths().get(k), o[k], i[k]));
        return res;
    }

    // ================================================================== CSV exports

    static String csvCell(Object v) {
        String s = v == null ? "" : v.toString();
        return s.contains(",") || s.contains("\"") ? "\"" + s.replace("\"", "\"\"") + "\"" : s;
    }

    /** QAP (quarterly alphalist of payees) in the order of the BIR alphalist template. */
    public String qapCsv(Quarter q, CompanyInfo co) {
        StringBuilder b = new StringBuilder("SEQ,TIN,REGISTERED NAME,ATC,NATURE OF INCOME PAYMENT,TAX RATE,AMOUNT OF INCOME PAYMENT,AMOUNT OF TAX WITHHELD\n");
        int n = 1;
        for (Payee p : withheld(q)) b.append(String.join(",", List.of(String.valueOf(n++), csvCell(p.supplier == null ? "" : p.supplier.tin), csvCell(p.supplier == null ? "" : p.supplier.name),
                p.atc, csvCell(p.getAtcLabel()), p.rate.stripTrailingZeros().toPlainString(), p.getTotalBase().toPlainString(), p.getTotalTax().toPlainString()))).append('\n');
        return b.toString();
    }

    /** SAWT (summary alphalist of tax withheld by clients). */
    public String sawtCsv(Quarter q) {
        StringBuilder b = new StringBuilder("SEQ,TIN,REGISTERED NAME (WITHHOLDING AGENT),ATC,TAX RATE,AMOUNT OF INCOME PAYMENT,AMOUNT OF TAX WITHHELD,BIR 2307 RECEIVED\n");
        int n = 1;
        for (ClientCwt c : clientCwt(q)) b.append(String.join(",", List.of(String.valueOf(n++), csvCell(c.tin()), csvCell(c.client()), c.atc(),
                c.rate().stripTrailingZeros().toPlainString(), c.base().toPlainString(), c.tax().toPlainString(), c.all2307() ? "YES" : "NO"))).append('\n');
        return b.toString();
    }
}
