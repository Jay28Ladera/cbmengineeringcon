package com.asmsi.admin_system.accounting;

import com.asmsi.admin_system.inventory.*;
import com.asmsi.admin_system.service.AuthUtil;
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

/** Accounting › Payables (supplier bills) and Payments (disbursement vouchers). */
@Controller
@RequestMapping("/Accounting")
public class PayablesController {

    private final BillRepository bills;
    private final BillLineRepository billLines;
    private final PaymentVoucherRepository vouchers;
    private final VoucherLineRepository voucherLines;
    private final PayablesService payables;
    private final VoucherService voucherService;
    private final PurchaseOrderRepository orders;
    private final PurchaseOrderLineRepository orderLines;
    private final SupplierRepository suppliers;
    private final AccountRepository accounts;
    private final BankAccountRepository banks;
    private final JournalEntryRepository entries;
    private final JournalLineRepository journalLines;
    private final Lookups lookups;
    private final Ledger ledger;
    private final AuthUtil auth;
    private final com.asmsi.admin_system.repository.UserSignatureRepository signatures;
    @org.springframework.beans.factory.annotation.Autowired private VoucherSheet sheet;

    public PayablesController(BillRepository bills, BillLineRepository billLines, PaymentVoucherRepository vouchers, VoucherLineRepository voucherLines,
                              PayablesService payables, VoucherService voucherService, PurchaseOrderRepository orders, PurchaseOrderLineRepository orderLines,
                              SupplierRepository suppliers, AccountRepository accounts, BankAccountRepository banks, JournalEntryRepository entries,
                              JournalLineRepository journalLines, Lookups lookups, Ledger ledger, AuthUtil auth,
                              com.asmsi.admin_system.repository.UserSignatureRepository signatures) {
        this.bills = bills; this.billLines = billLines; this.vouchers = vouchers; this.voucherLines = voucherLines; this.payables = payables;
        this.voucherService = voucherService; this.orders = orders; this.orderLines = orderLines; this.suppliers = suppliers; this.accounts = accounts;
        this.banks = banks; this.entries = entries; this.journalLines = journalLines; this.lookups = lookups; this.ledger = ledger; this.auth = auth;
        this.signatures = signatures;
    }

    @GetMapping({"", "/"})
    public String home() { return "redirect:/Accounting/Dashboard"; }

    // ================================================================== Payables

    /** A PO with something to bill: pay-first POs not yet billed, and deliveries received but not billed. */
    public record ToBill(PurchaseOrder po, Supplier supplier, BigDecimal amount, boolean payFirst) {}

    @GetMapping("/Payables")
    @Transactional(readOnly = true)
    public String payables(Model model) {
        Map<Long, Supplier> sup = lookups.supplierMap();
        List<ToBill> toBill = new ArrayList<>();
        for (PurchaseOrder po : orders.findAll()) {
            if ("CANCELLED".equals(po.status)) continue;
            boolean payFirst = po.isPayFirst() && "DRAFT".equals(po.status);
            if (!payFirst && "DRAFT".equals(po.status)) continue;
            BigDecimal amt = BigDecimal.ZERO;
            for (PurchaseOrderLine l : orderLines.findByPoIdOrderBySortOrderAscIdAsc(po.id)) {
                BigDecimal q = (payFirst || po.isPayFirst() ? l.qty : l.receivedQty).subtract(l.billed());
                if (q.signum() > 0) amt = amt.add(q.multiply(l.unitPrice));
            }
            if (amt.signum() > 0) toBill.add(new ToBill(po, sup.get(po.supplierId), Ledger.r2(amt), payFirst));
        }
        toBill.sort(Comparator.comparing((ToBill t) -> !t.payFirst()).thenComparing(t -> t.po().number));
        List<Bill> open = bills.findByStatusInOrderByDueDateAscIdAsc(PayablesService.OPEN);
        LocalDate week = LocalDate.now().plusDays(7);
        BigDecimal totalOpen = BigDecimal.ZERO, overdue = BigDecimal.ZERO, dueWeek = BigDecimal.ZERO;
        for (Bill b : open) {
            totalOpen = totalOpen.add(b.getBalance());
            if (b.isOverdue()) overdue = overdue.add(b.getBalance());
            else if (!b.dueDate.isAfter(week)) dueWeek = dueWeek.add(b.getBalance());
        }
        model.addAttribute("toBill", toBill);
        model.addAttribute("open", open);
        model.addAttribute("closed", bills.findTop50ByStatusInOrderByIdDesc(List.of("PAID", "VOID")));
        model.addAttribute("totalOpen", totalOpen);
        model.addAttribute("overdue", overdue);
        model.addAttribute("dueWeek", dueWeek);
        model.addAttribute("forApproval", vouchers.findByStatusInOrderByIdDesc(List.of("FOR_APPROVAL")).size());
        model.addAttribute("supplierMap", sup);
        model.addAttribute("activePage", "payables");
        return "acc-payables";
    }

    @GetMapping("/Payables/new")
    @Transactional(readOnly = true)
    public String newBill(@RequestParam(required = false) Long po, Model model, RedirectAttributes ra) {
        List<PayablesService.LineIn> ls = new ArrayList<>();
        Bill b;
        PurchaseOrder p = po == null ? null : orders.findById(po).orElse(null);
        if (p != null) {
            b = payables.draftFromPo(p, ls);
            if (ls.isEmpty()) {
                ra.addFlashAttribute("error", "Nothing on PO " + p.number + " is waiting to be billed" + (p.isPayFirst() ? "." : ": bills follow what was received."));
                return "redirect:/Accounting/Payables";
            }
        } else {
            b = new Bill(); b.billDate = LocalDate.now(); b.dueDate = LocalDate.now(); b.vatMode = "INCLUSIVE";
        }
        model.addAttribute("bill", b);
        model.addAttribute("po", p);
        model.addAttribute("lines", ls);
        model.addAttribute("suppliers", suppliers.findByActiveTrueOrderByNameAsc());
        model.addAttribute("expenseAccounts", accounts.findByActiveTrueOrderByCodeAsc().stream().filter(a -> !"INCOME".equals(a.type)).toList());
        model.addAttribute("grni", ledger.id(Acct.GRNI));
        model.addAttribute("jobs", lookups.jobOrders());
        model.addAttribute("supplierMap", lookups.supplierMap());
        model.addAttribute("activePage", "payables");
        return "acc-bill-form";
    }

    @PostMapping("/Payables")
    public String saveBill(@RequestParam(required = false) String supplierId, @RequestParam(required = false) String poId,
                           @RequestParam(required = false) String invoiceNo, @RequestParam(required = false) String billDate,
                           @RequestParam(required = false) String dueDate, @RequestParam(required = false) String vatMode,
                           @RequestParam(required = false) String vatRate, @RequestParam(required = false) String ewtRate,
                           @RequestParam(required = false) String jobOrderId, @RequestParam(required = false) String memo,
                           @RequestParam(name = "l_desc", required = false) List<String> desc, @RequestParam(name = "l_qty", required = false) List<String> qty,
                           @RequestParam(name = "l_price", required = false) List<String> price, @RequestParam(name = "l_account", required = false) List<String> acc,
                           @RequestParam(name = "l_job", required = false) List<String> job, @RequestParam(name = "l_poline", required = false) List<String> poLine,
                           RedirectAttributes ra) {
        Bill b = new Bill();
        b.supplierId = id(supplierId); b.poId = id(poId); b.kind = b.poId == null ? "EXPENSE" : "PO";
        b.invoiceNo = cut(invoiceNo, 60); b.billDate = Optional.ofNullable(date(billDate)).orElse(LocalDate.now());
        b.dueDate = Optional.ofNullable(date(dueDate)).orElse(b.billDate);
        b.vatMode = vatMode != null && List.of("INCLUSIVE", "EXCLUSIVE", "NONE").contains(vatMode) ? vatMode : "INCLUSIVE";
        BigDecimal vr = num(vatRate); if (vr != null && vr.signum() > 0) b.vatRate = vr;
        BigDecimal er = num(ewtRate); b.ewtRate = er == null ? BigDecimal.ZERO : er;
        b.jobOrderId = id(jobOrderId); b.memo = cut(memo, 500);
        if (b.poId != null) orders.findById(b.poId).ifPresent(p -> { b.supplierId = p.supplierId; if (b.jobOrderId == null) b.jobOrderId = p.jobOrderId; });
        List<PayablesService.LineIn> in = new ArrayList<>();
        for (int i = 0; i < size(desc, qty, price); i++)
            in.add(new PayablesService.LineIn(at(desc, i), num(at(qty, i)), num(at(price, i)), id(at(acc, i)), id(at(job, i)), id(at(poLine, i))));
        try {
            Bill saved = payables.create(b, in, auth.getCurrentUser().getName());
            ra.addFlashAttribute("success", "Bill " + saved.number + " recorded and posted: ₱" + String.format("%,.2f", saved.gross) + " owed, due " + saved.dueDate + ".");
            return "redirect:/Accounting/Payables/" + saved.id;
        } catch (IllegalArgumentException | IllegalStateException e) {
            ra.addFlashAttribute("error", e.getMessage());
            return b.poId != null ? "redirect:/Accounting/Payables/new?po=" + b.poId : "redirect:/Accounting/Payables/new";
        }
    }

    @GetMapping("/Payables/{id}")
    @Transactional(readOnly = true)
    public String bill(@PathVariable Long id, Model model) {
        Bill b = bills.findById(id).orElseThrow(() -> new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND));
        model.addAttribute("bill", b);
        model.addAttribute("lines", billLines.findByBillIdOrderBySortOrderAscIdAsc(id));
        model.addAttribute("supplier", suppliers.findById(b.supplierId).orElse(null));
        model.addAttribute("po", b.poId == null ? null : orders.findById(b.poId).orElse(null));
        List<Map.Entry<PaymentVoucher, VoucherLine>> pays = new ArrayList<>();
        for (VoucherLine vl : voucherLines.findByBillId(id)) vouchers.findById(vl.voucherId).ifPresent(v -> pays.add(Map.entry(v, vl)));
        model.addAttribute("payments", pays);
        addJournal(model, "BILL", id);
        model.addAttribute("accountMap", accountMap());
        model.addAttribute("jobMap", lookups.jobMap());
        model.addAttribute("activePage", "payables");
        return "acc-bill";
    }

    @PostMapping("/Payables/{id}/void")
    public String voidBill(@PathVariable Long id, @RequestParam(required = false) String reason, RedirectAttributes ra) {
        Bill b = bills.findById(id).orElse(null);
        if (b == null) return "redirect:/Accounting/Payables";
        try { payables.voidBill(b, blank(reason) ? "Voided" : reason.trim(), auth.getCurrentUser().getName()); ra.addFlashAttribute("success", "Bill " + b.number + " voided; its posting was reversed."); }
        catch (IllegalStateException e) { ra.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/Accounting/Payables/" + id;
    }

    // ================================================================== Payments

    @GetMapping("/Payments")
    @Transactional(readOnly = true)
    public String payments(Model model) {
        List<PaymentVoucher> all = vouchers.findTop100ByOrderByIdDesc();
        model.addAttribute("active", all.stream().filter(v -> List.of("DRAFT", "FOR_APPROVAL", "APPROVED").contains(v.status)).toList());
        model.addAttribute("done", all.stream().filter(v -> List.of("RELEASED", "VOID", "CANCELLED").contains(v.status)).toList());
        model.addAttribute("supplierMap", lookups.supplierMap());
        model.addAttribute("bankMap", bankMap());
        model.addAttribute("activePage", "payments");
        return "acc-payments";
    }

    @GetMapping("/Payments/new")
    @Transactional(readOnly = true)
    public String newVoucher(@RequestParam(required = false) Long supplier, @RequestParam(required = false) Long bill, Model model) {
        PaymentVoucher v = new PaymentVoucher();
        v.voucherDate = LocalDate.now();
        if (bill != null) bills.findById(bill).ifPresent(b -> v.supplierId = b.supplierId);
        if (supplier != null) v.supplierId = supplier;
        if (v.supplierId != null) orders.findAll().stream().filter(p -> p.supplierId.equals(v.supplierId) && p.paymentMode != null).findFirst()
                .ifPresent(p -> v.method = "CASH".equals(p.paymentMode) ? "CASH" : "CHEQUE");
        return voucherForm(v, bill == null ? Set.of() : Set.of(bill), Map.of(), model);
    }

    @GetMapping("/Payments/{id}/edit")
    @Transactional(readOnly = true)
    public String editVoucher(@PathVariable Long id, Model model) {
        PaymentVoucher v = vouchers.findById(id).orElseThrow(() -> new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND));
        if (!v.isEditable()) return "redirect:/Accounting/Payments/" + id;
        Set<Long> ticked = new HashSet<>();
        Map<Long, VoucherLine> amounts = new HashMap<>();
        for (VoucherLine l : voucherLines.findByVoucherId(id)) { ticked.add(l.billId); amounts.put(l.billId, l); }
        return voucherForm(v, ticked, amounts, model);
    }

    private String voucherForm(PaymentVoucher v, Set<Long> ticked, Map<Long, VoucherLine> amounts, Model model) {
        // suppliers with something to pay
        Map<Long, Supplier> sup = lookups.supplierMap();
        Set<Long> owing = new TreeSet<>();
        for (Bill b : bills.findByStatusInOrderByDueDateAscIdAsc(PayablesService.OPEN)) owing.add(b.supplierId);
        model.addAttribute("owing", owing.stream().map(sup::get).filter(Objects::nonNull).sorted(Comparator.comparing(s -> s.name)).toList());
        List<Bill> open = v.supplierId == null ? List.of() : bills.findBySupplierIdAndStatusInOrderByDueDateAscIdAsc(v.supplierId, PayablesService.OPEN);
        // what is already on other active vouchers
        Map<Long, BigDecimal> pending = new HashMap<>();
        for (Bill b : open) {
            BigDecimal p = BigDecimal.ZERO;
            for (VoucherLine vl : voucherLines.findByBillId(b.id)) {
                if (v.id != null && vl.voucherId.equals(v.id)) continue;
                PaymentVoucher o = vouchers.findById(vl.voucherId).orElse(null);
                if (o != null && List.of("DRAFT", "FOR_APPROVAL", "APPROVED").contains(o.status)) p = p.add(vl.gross);
            }
            pending.put(b.id, p);
        }
        model.addAttribute("v", v);
        model.addAttribute("bills", open);
        model.addAttribute("pending", pending);
        model.addAttribute("ticked", ticked);
        model.addAttribute("amounts", amounts);
        model.addAttribute("banks", banks.findByActiveTrueOrderByKindAscNameAsc());
        model.addAttribute("supplier", v.supplierId == null ? null : sup.get(v.supplierId));
        model.addAttribute("activePage", "payments");
        return "acc-voucher-form";
    }

    @PostMapping("/Payments")
    public String saveVoucher(@RequestParam(required = false) Long id, @RequestParam(required = false) String supplierId,
                              @RequestParam(required = false) String voucherDate, @RequestParam(required = false) String method,
                              @RequestParam(required = false) String bankId, @RequestParam(required = false) String chequeNo,
                              @RequestParam(required = false) String chequeDate, @RequestParam(required = false) String particulars,
                              @RequestParam(defaultValue = "save") String action,
                              @RequestParam Map<String, String> form, RedirectAttributes ra) {
        PaymentVoucher v = id == null ? new PaymentVoucher() : vouchers.findById(id).orElse(null);
        if (v == null) return "redirect:/Accounting/Payments";
        v.supplierId = id(supplierId); v.voucherDate = Optional.ofNullable(date(voucherDate)).orElse(LocalDate.now());
        v.method = "CASH".equals(method) ? "CASH" : "CHEQUE"; v.bankId = id(bankId);
        v.chequeNo = "CHEQUE".equals(v.method) ? cut(chequeNo, 40) : null; v.chequeDate = "CHEQUE".equals(v.method) ? date(chequeDate) : null;
        v.particulars = cut(particulars, 600);
        List<VoucherService.Pay> pays = new ArrayList<>();
        for (var e : form.entrySet()) {
            if (!e.getKey().startsWith("pay_")) continue;
            Long billId = id(e.getKey().substring(4));
            if (billId == null || !"true".equals(form.get("tick_" + billId))) continue;
            pays.add(new VoucherService.Pay(billId, num(e.getValue()), num(form.get("ewt_" + billId))));
        }
        try {
            var me = auth.getCurrentUser();
            voucherService.save(v, pays, me);
            if ("submit".equals(action)) { voucherService.submit(v, me); ra.addFlashAttribute("success", "Voucher " + v.number + " submitted for approval: ₱" + String.format("%,.2f", v.net) + " to release."); }
            else ra.addFlashAttribute("success", "Voucher " + v.number + " saved as draft.");
            return "redirect:/Accounting/Payments/" + v.id;
        } catch (IllegalArgumentException | IllegalStateException e) {
            ra.addFlashAttribute("error", e.getMessage());
            return v.id != null ? "redirect:/Accounting/Payments/" + v.id + "/edit" : "redirect:/Accounting/Payments/new" + (v.supplierId == null ? "" : "?supplier=" + v.supplierId);
        }
    }

    @GetMapping("/Payments/{id}")
    @Transactional(readOnly = true)
    public String voucher(@PathVariable Long id, Model model) {
        voucherModel(id, model);
        var me = auth.getCurrentUser();
        model.addAttribute("canApprove", voucherService.canApprove(me));
        model.addAttribute("hasSignature", me != null && signatures.existsById(me.getId()));
        model.addAttribute("approverNames", voucherService.approvers().stream().map(u -> u.getName()).toList());
        model.addAttribute("activePage", "payments");
        return "acc-voucher";
    }

    @GetMapping("/Payments/{id}/print")
    @Transactional(readOnly = true)
    public String printVoucher(@PathVariable Long id, Model model) {
        voucherModel(id, model);
        return "acc-voucher-print";
    }

    public void voucherModel(Long id, Model model) { sheet.fill(id, model); }

    @PostMapping("/Payments/{id}/{action}")
    public String voucherAction(@PathVariable Long id, @PathVariable String action,
                                @RequestParam(required = false) String note, @RequestParam(required = false) String reason,
                                @RequestParam(required = false) String releaseDate, @RequestParam(required = false) String chequeNo,
                                @RequestParam(required = false) String chequeDate, @RequestParam(required = false) String receivedBy,
                                @RequestParam(required = false) String orNo, @RequestParam(required = false) String from, RedirectAttributes ra) {
        PaymentVoucher v = vouchers.findById(id).orElse(null);
        if (v == null) return "redirect:/Accounting/Payments";
        var me = auth.getCurrentUser();
        try {
            switch (action) {
                case "submit" -> { voucherService.submit(v, me); ra.addFlashAttribute("success", "Submitted for approval."); }
                case "release" -> {
                    voucherService.release(v, date(releaseDate), chequeNo, date(chequeDate), receivedBy, orNo, me);
                    ra.addFlashAttribute("success", "Voucher " + v.number + " released and posted. The bills are updated.");
                }
                case "cancel" -> { voucherService.cancel(v, reason, me); ra.addFlashAttribute("success", "RELEASED".equals(v.status) || "VOID".equals(v.status) ? "Payment voided and reversed." : "Voucher cancelled."); }
                default -> { return "redirect:/Accounting/Payments/" + id; }
            }
        } catch (IllegalArgumentException | IllegalStateException e) { ra.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/Accounting/Payments/" + id;
    }

    // ================================================================== helpers

    private void addJournal(Model model, String source, Long id) {
        List<JournalEntry> es = entries.findBySourceAndSourceIdOrderByIdAsc(source, id);
        JournalEntry main = es.isEmpty() ? null : es.get(0);
        model.addAttribute("journal", main);
        model.addAttribute("jlines", main == null ? List.of() : journalLines.findByJournalIdOrderBySortOrderAscIdAsc(main.id));
        model.addAttribute("preview", false);
    }

    private Map<Long, Account> accountMap() { Map<Long, Account> m = new HashMap<>(); accounts.findAll().forEach(a -> m.put(a.id, a)); return m; }
    private Map<Long, BankAccount> bankMap() { Map<Long, BankAccount> m = new HashMap<>(); banks.findAll().forEach(b -> m.put(b.id, b)); return m; }
}
