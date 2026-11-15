package com.asmsi.admin_system.accounting;

import com.asmsi.admin_system.inventory.PurchaseOrderRepository;
import com.asmsi.admin_system.inventory.SupplierRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

/** Fills the model for a payment voucher page or printout (used by Accounting and by Approvals). */
@Component
public class VoucherSheet {
    private final PaymentVoucherRepository vouchers;
    private final VoucherLineRepository voucherLines;
    private final BillRepository bills;
    private final PurchaseOrderRepository orders;
    private final SupplierRepository suppliers;
    private final BankAccountRepository banks;
    private final JournalEntryRepository entries;
    private final JournalLineRepository journalLines;
    private final AccountRepository accounts;
    private final Ledger ledger;

    public VoucherSheet(PaymentVoucherRepository vouchers, VoucherLineRepository voucherLines, BillRepository bills, PurchaseOrderRepository orders,
                        SupplierRepository suppliers, BankAccountRepository banks, JournalEntryRepository entries, JournalLineRepository journalLines,
                        AccountRepository accounts, Ledger ledger) {
        this.vouchers = vouchers; this.voucherLines = voucherLines; this.bills = bills; this.orders = orders; this.suppliers = suppliers;
        this.banks = banks; this.entries = entries; this.journalLines = journalLines; this.accounts = accounts; this.ledger = ledger;
    }

    @Transactional(readOnly = true)
    public void fill(Long id, Model model) {
        PaymentVoucher v = vouchers.findById(id).orElseThrow(() -> new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND));
        List<VoucherLine> ls = voucherLines.findByVoucherId(id);
        Map<Long, Bill> bm = new HashMap<>();
        for (Bill b : bills.findAllById(ls.stream().map(l -> l.billId).toList())) bm.put(b.id, b);
        Map<Long, String> poNumbers = new HashMap<>();
        for (Bill b : bm.values()) if (b.poId != null) orders.findById(b.poId).ifPresent(p -> poNumbers.put(b.id, p.number));
        model.addAttribute("v", v);
        model.addAttribute("lines", ls);
        model.addAttribute("billMap", bm);
        model.addAttribute("poNumbers", poNumbers);
        model.addAttribute("supplier", suppliers.findById(v.supplierId).orElse(null));
        model.addAttribute("bank", v.bankId == null ? null : banks.findById(v.bankId).orElse(null));
        model.addAttribute("words", VoucherService.inWords(v.net));
        // the entry as posted, or as it will be posted on release
        if (v.journalId != null) {
            List<JournalEntry> es = entries.findBySourceAndSourceIdOrderByIdAsc("PAYMENT", id);
            JournalEntry main = es.isEmpty() ? null : es.get(0);
            model.addAttribute("journal", main);
            model.addAttribute("jlines", main == null ? List.of() : journalLines.findByJournalIdOrderBySortOrderAscIdAsc(main.id));
            model.addAttribute("preview", false);
        }
        else {
            List<JournalLine> preview = new ArrayList<>();
            Map<String, java.math.BigDecimal> byAp = new LinkedHashMap<>();
            for (VoucherLine l : ls) { Bill b = bm.get(l.billId); byAp.merge(b == null ? Acct.AP_SUPPLIERS : b.apCode(), l.gross, java.math.BigDecimal::add); }
            byAp.forEach((code, amt) -> preview.add(JournalLine.dr(ledger.id(code), amt, "Accounts payable")));
            if (v.bankId != null) banks.findById(v.bankId).ifPresent(b -> preview.add(JournalLine.cr(b.accountId, v.net, b.name)));
            if (v.ewt.signum() > 0) preview.add(JournalLine.cr(ledger.id(Acct.EWT_PAYABLE), v.ewt, "Withholding tax"));
            model.addAttribute("journal", null);
            model.addAttribute("jlines", preview);
            model.addAttribute("preview", true);
        }
        Map<Long, Account> am = new HashMap<>(); accounts.findAll().forEach(a -> am.put(a.id, a));
        model.addAttribute("accountMap", am);
    }

}
