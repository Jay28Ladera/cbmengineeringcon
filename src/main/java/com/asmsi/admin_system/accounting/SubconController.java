package com.asmsi.admin_system.accounting;

import com.asmsi.admin_system.inventory.Lookups;
import com.asmsi.admin_system.inventory.Supplier;
import com.asmsi.admin_system.inventory.SupplierRepository;
import com.asmsi.admin_system.service.AuthUtil;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.*;

import static com.asmsi.admin_system.accounting.F.*;

/** Implementation › Subcontractors (directory) and Accounting › Subcon Billings (subcontracts and their billings). */
@Controller
public class SubconController {

    private final SupplierRepository suppliers;
    private final SubconContractRepository contracts;
    private final SubconBillingRepository billings;
    private final BillRepository bills;
    private final SubconService service;
    private final JournalEntryRepository entries;
    private final JournalLineRepository journalLines;
    private final AccountRepository accounts;
    private final Lookups lookups;
    private final AuthUtil auth;

    public SubconController(SupplierRepository suppliers, SubconContractRepository contracts, SubconBillingRepository billings, BillRepository bills,
                            SubconService service, JournalEntryRepository entries, JournalLineRepository journalLines, AccountRepository accounts,
                            Lookups lookups, AuthUtil auth) {
        this.suppliers = suppliers; this.contracts = contracts; this.billings = billings; this.bills = bills; this.service = service;
        this.entries = entries; this.journalLines = journalLines; this.accounts = accounts; this.lookups = lookups; this.auth = auth;
    }

    private String me() { var u = auth.getCurrentUser(); return u == null ? "system" : u.getName(); }

    // ================================================================== directory

    public record SubRow(Supplier s, int contracts, BigDecimal amount, BigDecimal billed, BigDecimal payable) {}

    @GetMapping("/Subcontractors")
    @Transactional(readOnly = true)
    public String directory(Model model) {
        List<SubRow> rows = new ArrayList<>();
        for (Supplier s : suppliers.findAllByOrderByActiveDescNameAsc()) {
            if (!s.isSubcon()) continue;
            BigDecimal amt = BigDecimal.ZERO, billed = BigDecimal.ZERO, pay = BigDecimal.ZERO; int n = 0;
            for (SubconContract c : contracts.findBySupplierIdOrderByIdDesc(s.id)) {
                if ("CANCELLED".equals(c.status)) continue;
                var sum = service.summary(c); n++;
                amt = amt.add(c.amount); billed = billed.add(sum.billed()); pay = pay.add(sum.getPayable());
            }
            rows.add(new SubRow(s, n, amt, billed, pay));
        }
        model.addAttribute("rows", rows);
        model.addAttribute("activePage", "subcontractors");
        return "subcontractors";
    }

    @PostMapping("/Subcontractors")
    public String saveSub(@RequestParam(required = false) Long id, @RequestParam String name, @RequestParam(required = false) String trade,
                          @RequestParam(required = false) String contactPerson, @RequestParam(required = false) String phone, @RequestParam(required = false) String email,
                          @RequestParam(required = false) String address, @RequestParam(required = false) String tin, @RequestParam(required = false) String vatRegistered,
                          @RequestParam(required = false) String ewtRate, @RequestParam(required = false) String accreditation, @RequestParam(required = false) String notes,
                          @RequestParam(defaultValue = "false") boolean active, RedirectAttributes ra) {
        Supplier s = id == null ? new Supplier() : suppliers.findById(id).orElse(new Supplier());
        if (blank(name)) { ra.addFlashAttribute("error", "Enter the subcontractor's name."); return "redirect:/Subcontractors"; }
        s.name = cut(name, 160); s.trade = cut(trade, 80); s.contactPerson = cut(contactPerson, 120); s.phone = cut(phone, 60); s.email = cut(email, 160);
        s.address = cut(address, 300); s.tin = cut(tin, 40); s.vatRegistered = !"NO".equals(vatRegistered);
        BigDecimal ewt = num(ewtRate); s.ewtRate = ewt == null ? new BigDecimal("2") : ewt;
        s.accreditation = cut(accreditation, 300); s.notes = cut(notes, 1000); s.active = active || id == null; s.subcon = true;
        if (s.supplies == null) s.supplies = s.trade;
        suppliers.save(s);
        ra.addFlashAttribute("success", s.name + " saved.");
        return "redirect:/Subcontractors";
    }

    // ================================================================== subcontracts

    @GetMapping("/Accounting/SubconBillings")
    @Transactional(readOnly = true)
    public String list(Model model) {
        List<Map.Entry<SubconContract, SubconService.Summary>> rows = new ArrayList<>();
        for (SubconContract c : contracts.findAllByOrderByIdDesc()) rows.add(Map.entry(c, service.summary(c)));
        model.addAttribute("rows", rows);
        model.addAttribute("recent", billings.findTop200ByOrderByIdDesc().stream().limit(30).toList());
        model.addAttribute("supplierMap", lookups.supplierMap());
        model.addAttribute("jobMap", lookups.jobMap());
        model.addAttribute("subs", suppliers.findByActiveTrueOrderByNameAsc().stream().filter(Supplier::isSubcon).toList());
        model.addAttribute("jobs", lookups.openJobOrders());
        model.addAttribute("activePage", "subcon-billings");
        return "acc-subcon";
    }

    @PostMapping("/Accounting/SubconBillings/contract")
    public String saveContract(@RequestParam(required = false) Long id, @RequestParam(required = false) String supplierId, @RequestParam(required = false) String jobOrderId,
                               @RequestParam(required = false) String scope, @RequestParam(required = false) String amount, @RequestParam(required = false) String vatMode,
                               @RequestParam(required = false) String dpPct, @RequestParam(required = false) String retentionPct, @RequestParam(required = false) String ewtRate,
                               @RequestParam(required = false) String startDate, @RequestParam(required = false) String endDate, @RequestParam(required = false) String status,
                               @RequestParam(required = false) String notes, RedirectAttributes ra) {
        SubconContract c = id == null ? new SubconContract() : contracts.findById(id).orElse(new SubconContract());
        boolean billed = c.id != null && billings.findByContractIdOrderByIdAsc(c.id).stream().anyMatch(b -> !"VOID".equals(b.status));
        if (!billed) { c.supplierId = id(supplierId); c.jobOrderId = id(jobOrderId); c.vatMode = "NONE".equals(vatMode) ? "NONE" : "VAT"; }
        c.scope = cut(scope, 600); c.amount = num(amount);
        BigDecimal dp = num(dpPct), ret = num(retentionPct), ewt = num(ewtRate);
        if (!billed || c.dpPct == null) c.dpPct = dp == null ? BigDecimal.ZERO : dp;
        c.retentionPct = ret == null ? BigDecimal.ZERO : ret;
        c.ewtRate = ewt == null ? new BigDecimal("2") : ewt;
        c.startDate = date(startDate); c.endDate = date(endDate); c.notes = cut(notes, 600);
        if (status != null && List.of("ACTIVE", "DONE", "CANCELLED").contains(status)) c.status = status;
        if (c.ewtRate == null) c.ewtRate = new BigDecimal("2");
        if (id == null && c.supplierId != null) suppliers.findById(c.supplierId).ifPresent(s -> { if (ewt == null) c.ewtRate = s.ewt(); });
        try {
            service.saveContract(c, me());
            ra.addFlashAttribute("success", "Subcontract " + c.number + " saved.");
            return "redirect:/Accounting/SubconBillings/contract/" + c.id;
        } catch (IllegalArgumentException | IllegalStateException e) {
            ra.addFlashAttribute("error", e.getMessage());
            return id == null ? "redirect:/Accounting/SubconBillings" : "redirect:/Accounting/SubconBillings/contract/" + id;
        }
    }

    @GetMapping("/Accounting/SubconBillings/contract/{id}")
    @Transactional(readOnly = true)
    public String contract(@PathVariable Long id, Model model) {
        SubconContract c = contracts.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        List<SubconBilling> bl = billings.findByContractIdOrderByIdAsc(id);
        Map<Long, Bill> bm = new HashMap<>();
        for (SubconBilling b : bl) if (b.billId != null) bills.findById(b.billId).ifPresent(x -> bm.put(b.id, x));
        model.addAttribute("c", c);
        model.addAttribute("s", service.summary(c));
        model.addAttribute("billings", bl);
        model.addAttribute("billMap", bm);
        model.addAttribute("draft", bl.stream().filter(SubconBilling::isEditable).findFirst().orElse(null));
        model.addAttribute("sub", suppliers.findById(c.supplierId).orElse(null));
        model.addAttribute("job", lookups.jobMap().get(c.jobOrderId));
        model.addAttribute("subs", suppliers.findByActiveTrueOrderByNameAsc().stream().filter(Supplier::isSubcon).toList());
        model.addAttribute("jobs", lookups.jobOrders());
        model.addAttribute("activePage", "subcon-billings");
        return "acc-subcon-contract";
    }

    @PostMapping("/Accounting/SubconBillings/contract/{id}/new")
    public String newBilling(@PathVariable Long id, @RequestParam String kind, RedirectAttributes ra) {
        SubconContract c = contracts.findById(id).orElse(null);
        if (c == null) return "redirect:/Accounting/SubconBillings";
        try {
            SubconBilling b = service.draft(c, List.of("DOWNPAYMENT", "RETENTION").contains(kind) ? kind : "PROGRESS", me());
            return "redirect:/Accounting/SubconBillings/" + b.id;
        } catch (IllegalArgumentException | IllegalStateException e) {
            ra.addFlashAttribute("error", e.getMessage());
            return "redirect:/Accounting/SubconBillings/contract/" + id;
        }
    }

    @GetMapping("/Accounting/SubconBillings/{id}")
    @Transactional(readOnly = true)
    public String billing(@PathVariable Long id, Model model) {
        SubconBilling b = billings.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        SubconContract c = contracts.findById(b.contractId).orElseThrow();
        model.addAttribute("b", b);
        model.addAttribute("c", c);
        model.addAttribute("s", service.summary(c));
        model.addAttribute("sub", suppliers.findById(b.supplierId).orElse(null));
        model.addAttribute("job", lookups.jobMap().get(b.jobOrderId));
        model.addAttribute("bill", b.billId == null ? null : bills.findById(b.billId).orElse(null));
        List<JournalEntry> es = entries.findBySourceAndSourceIdOrderByIdAsc("SUBCON", id);
        JournalEntry je = es.isEmpty() ? null : es.get(0);
        model.addAttribute("journal", je);
        model.addAttribute("jlines", je == null ? List.of() : journalLines.findByJournalIdOrderBySortOrderAscIdAsc(je.id));
        model.addAttribute("preview", false);
        Map<Long, Account> am = new HashMap<>(); accounts.findAll().forEach(a -> am.put(a.id, a));
        model.addAttribute("accountMap", am);
        model.addAttribute("activePage", "subcon-billings");
        return "acc-subcon-billing";
    }

    @PostMapping("/Accounting/SubconBillings/{id}")
    public String saveBilling(@PathVariable Long id, @RequestParam(required = false) String pctToDate, @RequestParam(required = false) String amount,
                              @RequestParam(required = false) String billingDate, @RequestParam(required = false) String invoiceNo,
                              @RequestParam(required = false) String verifiedBy, @RequestParam(required = false) String memo,
                              @RequestParam(defaultValue = "save") String action, RedirectAttributes ra) {
        SubconBilling b = billings.findById(id).orElse(null);
        if (b == null) return "redirect:/Accounting/SubconBillings";
        try {
            service.update(b, num(pctToDate), num(amount), date(billingDate), invoiceNo, verifiedBy, memo);
            if ("post".equals(action)) {
                Bill bill = service.post(b, me());
                ra.addFlashAttribute("success", b.getTitle() + " posted. Bill " + bill.number + " (₱" + String.format("%,.2f", bill.gross) + ") is in Payables, ready for a payment voucher.");
            } else ra.addFlashAttribute("success", "Draft saved.");
        } catch (IllegalArgumentException | IllegalStateException e) { ra.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/Accounting/SubconBillings/" + id;
    }

    @PostMapping("/Accounting/SubconBillings/{id}/{action}")
    public String billingAction(@PathVariable Long id, @PathVariable String action, @RequestParam(required = false) String reason, RedirectAttributes ra) {
        SubconBilling b = billings.findById(id).orElse(null);
        if (b == null) return "redirect:/Accounting/SubconBillings";
        Long cid = b.contractId;
        try {
            switch (action) {
                case "delete" -> { service.deleteDraft(b); ra.addFlashAttribute("success", "Draft deleted."); return "redirect:/Accounting/SubconBillings/contract/" + cid; }
                case "void" -> { service.voidBilling(b, reason, me()); ra.addFlashAttribute("success", b.number + " voided; its bill and posting were reversed."); }
                default -> { }
            }
        } catch (IllegalArgumentException | IllegalStateException e) { ra.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/Accounting/SubconBillings/" + id;
    }
}
