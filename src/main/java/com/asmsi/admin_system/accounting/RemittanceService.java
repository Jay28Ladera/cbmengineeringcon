package com.asmsi.admin_system.accounting;

import com.asmsi.admin_system.inventory.InventoryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static com.asmsi.admin_system.accounting.ClientBillingService.*;

/** Paying BIR, SSS, PhilHealth and Pag-IBIG what was withheld or collected. VAT: output VAT less input VAT. */
@Service
public class RemittanceService {

    public record Kind(String key, String label, String agency, String code, String form) {}
    public static final List<Kind> KINDS = List.of(
            new Kind("EWT", "Expanded withholding tax", "BIR", Acct.EWT_PAYABLE, "1601-EQ / 0619-E"),
            new Kind("WTC", "Withholding tax on compensation", "BIR", Acct.WTC_PAYABLE, "1601-C"),
            new Kind("VAT", "VAT (output less input)", "BIR", Acct.OUTPUT_VAT, "2550Q"),
            new Kind("SSS", "SSS contributions", "SSS", Acct.SSS_PAYABLE, "R-5 / PRN"),
            new Kind("PHILHEALTH", "PhilHealth contributions", "PhilHealth", Acct.PHILHEALTH_PAYABLE, "RF-1 / PRN"),
            new Kind("PAGIBIG", "Pag-IBIG contributions", "Pag-IBIG", Acct.PAGIBIG_PAYABLE, "MCRF"));

    private final RemittanceRepository remittances;
    private final BankAccountRepository banks;
    private final Ledger ledger;
    private final InventoryService numbers;

    public RemittanceService(RemittanceRepository remittances, BankAccountRepository banks, Ledger ledger, InventoryService numbers) {
        this.remittances = remittances; this.banks = banks; this.ledger = ledger; this.numbers = numbers;
    }

    public static Kind kind(String key) { return KINDS.stream().filter(k -> k.key().equals(key)).findFirst().orElse(null); }

    /** What is owed now per kind (VAT: output VAT less input VAT, never below zero). */
    public Map<String, BigDecimal> owed() {
        Map<String, BigDecimal> m = new LinkedHashMap<>();
        LocalDate today = LocalDate.now();
        for (Kind k : KINDS) {
            BigDecimal v = ledger.balanceOf(ledger.id(k.code()), today);
            if ("VAT".equals(k.key())) v = v.subtract(ledger.balanceOf(ledger.id(Acct.INPUT_VAT), today));
            m.put(k.key(), v);
        }
        return m;
    }

    @Transactional
    public Remittance remit(String kindKey, BigDecimal amount, Long bankId, LocalDate date, String periodLabel, String reference, String by) {
        Kind k = kind(kindKey);
        if (k == null) throw new IllegalArgumentException("Choose what is being remitted.");
        BankAccount b = banks.findById(bankId == null ? -1L : bankId).orElseThrow(() -> new IllegalArgumentException("Choose where the payment comes from."));
        if (amount == null || amount.signum() <= 0) throw new IllegalArgumentException("Enter the amount paid.");
        Remittance r = new Remittance();
        r.number = numbers.nextNumber("acc_remittance", "RM"); r.kind = k.key(); r.bankId = b.id; r.amount = r2(amount);
        r.remitDate = date == null ? LocalDate.now() : date; r.periodLabel = cut(periodLabel, 60); r.reference = cut(reference, 60); r.createdBy = by;
        List<JournalLine> j = new ArrayList<>();
        if ("VAT".equals(k.key())) {
            // settle the output VAT with the input VAT on hand; the rest is paid
            BigDecimal input = ledger.balanceOf(ledger.id(Acct.INPUT_VAT), r.remitDate).max(BigDecimal.ZERO);
            BigDecimal output = ledger.balanceOf(ledger.id(Acct.OUTPUT_VAT), r.remitDate).max(BigDecimal.ZERO);
            r.creditUsed = input.min(output);
            if (r.amount.add(r.creditUsed).compareTo(output) > 0) throw new IllegalArgumentException("That is more than the VAT due (₱" + peso(output.subtract(r.creditUsed)) + ").");
            j.add(JournalLine.dr(ledger.id(Acct.OUTPUT_VAT), r.amount.add(r.creditUsed), "Output VAT settled"));
            if (r.creditUsed.signum() > 0) j.add(JournalLine.cr(ledger.id(Acct.INPUT_VAT), r.creditUsed, "Input VAT applied"));
        } else j.add(JournalLine.dr(ledger.id(k.code()), r.amount, k.label()));
        j.add(JournalLine.cr(b.accountId, r.amount, b.name));
        remittances.save(r);
        r.journalId = ledger.post(r.remitDate, k.agency() + " remittance · " + k.label() + (r.periodLabel != null ? " · " + r.periodLabel : "") + (r.reference != null ? " · " + r.reference : ""),
                "REMITTANCE", r.id, r.number, j, by).id;
        return remittances.save(r);
    }

    @Transactional
    public void voidRemittance(Remittance r, String reason, String by) {
        if (!"POSTED".equals(r.status)) return;
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("Give the reason.");
        ledger.reverseSource("REMITTANCE", r.id, reason.trim(), by);
        r.status = "VOID"; r.voidReason = cut(reason.trim(), 300);
        remittances.save(r);
    }
}
