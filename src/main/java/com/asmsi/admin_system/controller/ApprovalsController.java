package com.asmsi.admin_system.controller;

import com.asmsi.admin_system.entity.Quotation;
import com.asmsi.admin_system.entity.QuotationSignoff;
import com.asmsi.admin_system.repository.QuotationRepository;
import com.asmsi.admin_system.repository.QuotationSignoffRepository;
import com.asmsi.admin_system.service.AuthUtil;
import com.asmsi.admin_system.service.QuoteWorkflowService;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.*;

/**
 * The approver's inbox (e.g. the CEO): every quotation waiting for approval, with its total, cost and margin,
 * approved or returned from here. Below it, what this approver decided recently.
 */
@Controller
public class ApprovalsController {

    private final QuotationRepository quotations;
    private final QuotationSignoffRepository signoffs;
    private final QuoteWorkflowService workflow;
    private final AuthUtil auth;
    private final com.asmsi.admin_system.inventory.CanvassApprovalService canvassApprovals;
    private final com.asmsi.admin_system.inventory.CanvassRepository canvasses;
    private final com.asmsi.admin_system.inventory.PurchasingService purchasing;
    private final com.asmsi.admin_system.inventory.Lookups lookups;
    private final com.asmsi.admin_system.inventory.CanvassSheet canvassSheet;
    private final com.asmsi.admin_system.repository.UserSignatureRepository userSignatures;
    private final com.asmsi.admin_system.accounting.VoucherService voucherService;
    private final com.asmsi.admin_system.accounting.PaymentVoucherRepository vouchers;
    private final com.asmsi.admin_system.accounting.VoucherLineRepository voucherLines;
    private final com.asmsi.admin_system.accounting.BillRepository bills;
    private final com.asmsi.admin_system.accounting.BankAccountRepository banks;
    @org.springframework.beans.factory.annotation.Autowired private com.asmsi.admin_system.accounting.VoucherSheet voucherSheet;
    @org.springframework.beans.factory.annotation.Autowired private com.asmsi.admin_system.accounting.ClientBillingService billingService;
    @org.springframework.beans.factory.annotation.Autowired private com.asmsi.admin_system.accounting.ClientBillingRepository clientBillings;
    @org.springframework.beans.factory.annotation.Autowired private com.asmsi.admin_system.accounting.BillingSheet billingSheet;

    public ApprovalsController(QuotationRepository quotations, QuotationSignoffRepository signoffs, QuoteWorkflowService workflow, AuthUtil auth,
                               com.asmsi.admin_system.inventory.CanvassApprovalService canvassApprovals,
                               com.asmsi.admin_system.inventory.CanvassRepository canvasses,
                               com.asmsi.admin_system.inventory.PurchasingService purchasing,
                               com.asmsi.admin_system.inventory.Lookups lookups,
                               com.asmsi.admin_system.inventory.CanvassSheet canvassSheet,
                               com.asmsi.admin_system.repository.UserSignatureRepository userSignatures,
                               com.asmsi.admin_system.accounting.VoucherService voucherService,
                               com.asmsi.admin_system.accounting.PaymentVoucherRepository vouchers,
                               com.asmsi.admin_system.accounting.VoucherLineRepository voucherLines,
                               com.asmsi.admin_system.accounting.BillRepository bills,
                               com.asmsi.admin_system.accounting.BankAccountRepository banks) {
        this.quotations = quotations; this.signoffs = signoffs; this.workflow = workflow; this.auth = auth;
        this.canvassApprovals = canvassApprovals; this.canvasses = canvasses; this.purchasing = purchasing; this.lookups = lookups;
        this.canvassSheet = canvassSheet; this.userSignatures = userSignatures;
        this.voucherService = voucherService; this.vouchers = vouchers; this.voucherLines = voucherLines; this.bills = bills; this.banks = banks;
    }

    /** A client billing waiting for approval, with where the job stands. */
    public record BillingPending(com.asmsi.admin_system.accounting.ClientBilling billing, String job,
                                 com.asmsi.admin_system.accounting.JobContract contract,
                                 com.asmsi.admin_system.accounting.ClientBillingService.Summary summary) {}

    /** A payment voucher waiting for approval, with the bills it pays. */
    public record VoucherPending(com.asmsi.admin_system.accounting.PaymentVoucher voucher, String supplier, String bank,
                                 List<String> bills) {}

    /** A canvass waiting for this user's signature, with its comparison grid. */
    public record CanvassPending(com.asmsi.admin_system.inventory.Canvass canvass, com.asmsi.admin_system.inventory.CanvassSignoff signoff,
                                 String step, String stepLabel, String stepRole, com.asmsi.admin_system.inventory.PurchasingService.Grid grid,
                                 String jobLabel) {}

    public record Pending(Quotation quotation, QuotationSignoff signoff, String submitterEmail) {}
    public record Recent(Quotation quotation, QuotationSignoff signoff) {}

    @GetMapping("/Approvals")
    @Transactional(readOnly = true)
    public String approvals(Model model, RedirectAttributes redirect) {
        var me = auth.getCurrentUser();
        boolean quotes = workflow.canApprove(me);
        Set<String> steps = canvassApprovals.stepsFor(me);
        boolean pays = voucherService.canApprove(me);
        if (!quotes && steps.isEmpty() && !pays) {
            redirect.addFlashAttribute("error", "Approvals is for the people who approve quotations, sign canvass sheets or approve payments. An administrator can set this for you in Users.");
            return "redirect:/home";
        }
        // Canvass sheets waiting for a signature this user can give
        List<CanvassPending> cv = new ArrayList<>();
        if (!steps.isEmpty()) {
            var jobs = lookups.jobMap();
            for (var c : canvasses.findByStatusInOrderByIdAsc(List.of("FOR_APPROVAL", "APPROVED", "ORDERED"))) {
                var cso = canvassApprovals.signoff(c.id);
                String step = canvassApprovals.pendingStep(c, cso);
                if (step == null || !steps.contains(step)) continue;
                var j = c.jobOrderId == null ? null : jobs.get(c.jobOrderId);
                cv.add(new CanvassPending(c, cso, step, com.asmsi.admin_system.inventory.CanvassApprovalService.label(step),
                        com.asmsi.admin_system.inventory.CanvassApprovalService.roleHint(step), purchasing.grid(c), j == null ? "Warehouse stock" : j.label()));
            }
        }
        model.addAttribute("canvassPending", cv);
        List<VoucherPending> vp = new ArrayList<>();
        if (pays) {
            var sup = lookups.supplierMap();
            for (var v : vouchers.findByStatusOrderByIdAsc("FOR_APPROVAL")) {
                List<String> bl = new ArrayList<>();
                for (var l : voucherLines.findByVoucherId(v.id)) bills.findById(l.billId).ifPresent(b ->
                        bl.add(b.number + (b.invoiceNo != null ? " (inv " + b.invoiceNo + ")" : "") + " · ₱" + String.format("%,.2f", l.gross)));
                var s0 = sup.get(v.supplierId);
                var bk = v.bankId == null ? null : banks.findById(v.bankId).orElse(null);
                vp.add(new VoucherPending(v, s0 == null ? "" : s0.name, bk == null ? "" : bk.getLabel(), bl));
            }
        }
        model.addAttribute("voucherPending", vp);
        List<BillingPending> bp = new ArrayList<>();
        if (pays) for (var b : clientBillings.findByStatusOrderByIdAsc("FOR_APPROVAL"))
            bp.add(new BillingPending(b, billingService.jobLabel(b.jobOrderId), billingService.contract(b.jobOrderId), billingService.summary(b.jobOrderId)));
        model.addAttribute("billingPending", bp);
        model.addAttribute("isPaymentApprover", pays);
        model.addAttribute("isQuoteApprover", quotes);
        model.addAttribute("supplierMap", lookups.supplierMap());
        Map<Long, QuotationSignoff> so = new HashMap<>();
        signoffs.findAll().forEach(s -> so.put(s.quotationId, s));

        List<Pending> pending = new ArrayList<>();
        if (quotes) for (Quotation q : quotations.findWithDetailsByStatus("FOR_APPROVAL"))
        {
            QuotationSignoff s = so.getOrDefault(q.getId(), new QuotationSignoff(q.getId()));
            String sub = workflow.submitterEmail(s);
            pending.add(new Pending(q, s, sub != null && me.getEmail() != null && sub.equalsIgnoreCase(me.getEmail()) ? null : sub));
        }
        pending.sort(Comparator.comparing(p -> p.signoff().submittedAt == null ? java.time.LocalDateTime.MIN : p.signoff().submittedAt));

        // What I approved recently (still on record; returned ones lose their approval, so they show as returned by me)
        List<Recent> recent = new ArrayList<>();
        Map<Long, Quotation> byId = new HashMap<>();
        for (QuotationSignoff s : so.values()) {
            boolean mine = (s.approverId != null && s.approverId.equals(me.getId())) || (s.returnedBy != null && s.returnedBy.equals(me.getName()) && s.approvedAt == null);
            if (mine && quotes) byId.put(s.quotationId, null);
        }
        if (!byId.isEmpty()) for (Quotation q : quotations.findAllById(byId.keySet())) recent.add(new Recent(q, so.get(q.getId())));
        recent.sort(Comparator.comparing((Recent r) -> {
            var s = r.signoff(); return s.approvedAt != null ? s.approvedAt : (s.returnedAt != null ? s.returnedAt : java.time.LocalDateTime.MIN);
        }).reversed());
        if (recent.size() > 12) recent = recent.subList(0, 12);

        model.addAttribute("pending", pending);
        model.addAttribute("recent", recent);
        model.addAttribute("hasSignature", userSignatures.existsById(me.getId()));
        model.addAttribute("activePage", "approvals");
        return "approvals";
    }

    // ------------------------------------------------------------------ canvass sheets (here so Finance can sign without Purchasing access)

    @org.springframework.web.bind.annotation.PostMapping("/Approvals/canvass/{id}/sign")
    public String signCanvass(@org.springframework.web.bind.annotation.PathVariable Long id,
                              @org.springframework.web.bind.annotation.RequestParam(required = false) String from, RedirectAttributes redirect) {
        var c = canvasses.findById(id).orElse(null);
        if (c == null) return "redirect:/Approvals";
        try {
            String step = canvassApprovals.sign(c, auth.getCurrentUser());
            redirect.addFlashAttribute("success", "Canvass " + c.number + ": signed \"" + com.asmsi.admin_system.inventory.CanvassApprovalService.label(step) + "\"."
                    + ("NOTE".equals(step) ? " Purchase orders can now be made." : ""));
        } catch (IllegalStateException e) { redirect.addFlashAttribute("error", e.getMessage()); }
        return "canvass".equals(from) ? "redirect:/Purchasing/Canvasses/" + id : "redirect:/Approvals";
    }

    @org.springframework.web.bind.annotation.PostMapping("/Approvals/canvass/{id}/return")
    public String returnCanvass(@org.springframework.web.bind.annotation.PathVariable Long id,
                                @org.springframework.web.bind.annotation.RequestParam(required = false) String note,
                                @org.springframework.web.bind.annotation.RequestParam(required = false) String from, RedirectAttributes redirect) {
        var c = canvasses.findById(id).orElse(null);
        if (c == null) return "redirect:/Approvals";
        try {
            canvassApprovals.returnToDraft(c, auth.getCurrentUser(), note);
            redirect.addFlashAttribute("success", "Canvass " + c.number + " returned to the preparer with your note.");
        } catch (IllegalStateException e) { redirect.addFlashAttribute("error", e.getMessage()); }
        return "canvass".equals(from) ? "redirect:/Purchasing/Canvasses/" + id : "redirect:/Approvals";
    }

    @GetMapping("/Approvals/canvass/{id}/print")
    @Transactional(readOnly = true)
    public String printCanvass(@org.springframework.web.bind.annotation.PathVariable Long id, Model model) {
        var c = canvasses.findById(id).orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND));
        canvassSheet.fill(model, c);
        return "pur-canvass-print";
    }

    // ------------------------------------------------------------------ payment vouchers

    @org.springframework.web.bind.annotation.PostMapping("/Approvals/voucher/{id}/approve")
    public String approveVoucher(@org.springframework.web.bind.annotation.PathVariable Long id,
                                 @org.springframework.web.bind.annotation.RequestParam(required = false) String from, RedirectAttributes redirect) {
        var v = vouchers.findById(id).orElse(null);
        if (v == null) return "redirect:/Approvals";
        try {
            voucherService.approve(v, auth.getCurrentUser());
            redirect.addFlashAttribute("success", "Voucher " + v.number + " approved. Finance can release the payment.");
        } catch (IllegalStateException e) { redirect.addFlashAttribute("error", e.getMessage()); }
        return "voucher".equals(from) ? "redirect:/Accounting/Payments/" + id : "redirect:/Approvals";
    }

    @org.springframework.web.bind.annotation.PostMapping("/Approvals/voucher/{id}/return")
    public String returnVoucher(@org.springframework.web.bind.annotation.PathVariable Long id,
                                @org.springframework.web.bind.annotation.RequestParam(required = false) String note,
                                @org.springframework.web.bind.annotation.RequestParam(required = false) String from, RedirectAttributes redirect) {
        var v = vouchers.findById(id).orElse(null);
        if (v == null) return "redirect:/Approvals";
        try {
            voucherService.returnToDraft(v, auth.getCurrentUser(), note);
            redirect.addFlashAttribute("success", "Voucher " + v.number + " returned to Finance with your note.");
        } catch (IllegalStateException e) { redirect.addFlashAttribute("error", e.getMessage()); }
        return "voucher".equals(from) ? "redirect:/Accounting/Payments/" + id : "redirect:/Approvals";
    }

    @GetMapping("/Approvals/voucher/{id}/print")
    @Transactional(readOnly = true)
    public String printVoucher(@org.springframework.web.bind.annotation.PathVariable Long id, Model model) {
        voucherSheet.fill(id, model);
        return "acc-voucher-print";
    }

    // ------------------------------------------------------------------ client billings

    @org.springframework.web.bind.annotation.PostMapping("/Approvals/billing/{id}/approve")
    public String approveBilling(@org.springframework.web.bind.annotation.PathVariable Long id,
                                 @org.springframework.web.bind.annotation.RequestParam(required = false) String from, RedirectAttributes redirect) {
        var b = clientBillings.findById(id).orElse(null);
        if (b == null) return "redirect:/Approvals";
        try {
            billingService.approve(b, auth.getCurrentUser());
            redirect.addFlashAttribute("success", b.getTitle() + " (" + b.number + ") approved and issued. Finance can send it to the client.");
        } catch (IllegalStateException e) { redirect.addFlashAttribute("error", e.getMessage()); }
        return "billing".equals(from) ? "redirect:/Accounting/Billing/" + id : "redirect:/Approvals";
    }

    @org.springframework.web.bind.annotation.PostMapping("/Approvals/billing/{id}/return")
    public String returnBilling(@org.springframework.web.bind.annotation.PathVariable Long id,
                                @org.springframework.web.bind.annotation.RequestParam(required = false) String note,
                                @org.springframework.web.bind.annotation.RequestParam(required = false) String from, RedirectAttributes redirect) {
        var b = clientBillings.findById(id).orElse(null);
        if (b == null) return "redirect:/Approvals";
        try {
            billingService.returnToDraft(b, auth.getCurrentUser(), note);
            redirect.addFlashAttribute("success", b.number + " returned to Finance with your note.");
        } catch (IllegalStateException e) { redirect.addFlashAttribute("error", e.getMessage()); }
        return "billing".equals(from) ? "redirect:/Accounting/Billing/" + id : "redirect:/Approvals";
    }

    @GetMapping("/Approvals/billing/{id}/print")
    @Transactional(readOnly = true)
    public String printBilling(@org.springframework.web.bind.annotation.PathVariable Long id, Model model) {
        var b = clientBillings.findById(id).orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND));
        billingSheet.fill(b, model);
        model.addAttribute("publicView", false);
        return "acc-billing-print";
    }
}
