package com.asmsi.admin_system.accounting;

import com.asmsi.admin_system.entity.JobOrder;
import com.asmsi.admin_system.inventory.Lookups;
import com.asmsi.admin_system.repository.JobOrderRepository;
import com.asmsi.admin_system.repository.UserSignatureRepository;
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

/** Accounting › Client Billing (downpayment, progress, retention) and Collections. */
@Controller
@RequestMapping("/Accounting")
public class ClientBillingController {

    private final ClientBillingService service;
    private final ClientBillingRepository billings;
    private final ClientCollectionRepository collections;
    private final ClientCollectionLineRepository collectionLines;
    private final JobContractRepository contracts;
    private final JobOrderRepository jobs;
    private final BankAccountRepository banks;
    private final JournalEntryRepository entries;
    private final JournalLineRepository journalLines;
    private final AccountRepository accounts;
    private final BillingSheet sheet;
    private final Lookups lookups;
    private final AuthUtil auth;
    private final UserSignatureRepository signatures;
    private final VoucherService approvals;

    public ClientBillingController(ClientBillingService service, ClientBillingRepository billings, ClientCollectionRepository collections,
                                   ClientCollectionLineRepository collectionLines, JobContractRepository contracts, JobOrderRepository jobs,
                                   BankAccountRepository banks, JournalEntryRepository entries, JournalLineRepository journalLines, AccountRepository accounts,
                                   BillingSheet sheet, Lookups lookups, AuthUtil auth, UserSignatureRepository signatures, VoucherService approvals) {
        this.service = service; this.billings = billings; this.collections = collections; this.collectionLines = collectionLines; this.contracts = contracts;
        this.jobs = jobs; this.banks = banks; this.entries = entries; this.journalLines = journalLines; this.accounts = accounts; this.sheet = sheet;
        this.lookups = lookups; this.auth = auth; this.signatures = signatures; this.approvals = approvals;
    }

    // ================================================================== overview

    public record JobRow(Lookups.JobRef job, JobContract contract, ClientBillingService.Summary s) {}
    public record Aging(BigDecimal current, BigDecimal d30, BigDecimal d60, BigDecimal d90, BigDecimal over90) {
        public BigDecimal getTotal() { return current.add(d30).add(d60).add(d90).add(over90); }
    }

    @GetMapping("/Billing")
    @Transactional(readOnly = true)
    public String billing(Model model) {
        Map<Long, JobContract> cm = new HashMap<>();
        contracts.findAll().forEach(c -> cm.put(c.jobOrderId, c));
        List<JobRow> rows = new ArrayList<>(), unset = new ArrayList<>();
        BigDecimal ar = BigDecimal.ZERO, held = BigDecimal.ZERO, unbilled = BigDecimal.ZERO;
        for (Lookups.JobRef j : lookups.jobOrders()) {
            if ("CANCELLED".equals(j.status())) continue;
            JobContract c = cm.get(j.id());
            if (c == null) { unset.add(new JobRow(j, null, null)); continue; }
            var s = service.summary(j.id());
            rows.add(new JobRow(j, c, s));
            ar = ar.add(s.getBalance()); held = held.add(s.getRetentionUnbilled()); unbilled = unbilled.add(s.getUnbilled());
        }
        List<ClientBilling> open = billings.findByStatusInOrderByDueDateAscIdAsc(List.of("ISSUED", "PARTIAL"));
        BigDecimal[] a = {BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO};
        BigDecimal overdue = BigDecimal.ZERO;
        for (ClientBilling b : open) {
            long d = b.getDaysOverdue();
            int k = !b.isOverdue() ? 0 : d <= 30 ? 1 : d <= 60 ? 2 : d <= 90 ? 3 : 4;
            a[k] = a[k].add(b.getBalance());
            if (k > 0) overdue = overdue.add(b.getBalance());
        }
        model.addAttribute("rows", rows);
        model.addAttribute("unset", unset);
        model.addAttribute("open", open);
        model.addAttribute("pending", billings.findByStatusInOrderByDueDateAscIdAsc(ClientBillingService.PENDING));
        model.addAttribute("aging", new Aging(a[0], a[1], a[2], a[3], a[4]));
        model.addAttribute("ar", ar); model.addAttribute("overdue", overdue); model.addAttribute("held", held); model.addAttribute("unbilled", unbilled);
        model.addAttribute("jobMap", lookups.jobMap());
        model.addAttribute("activePage", "client-billing");
        return "acc-billing";
    }

    // ================================================================== one job

    @GetMapping("/Billing/job/{jobId}")
    @Transactional(readOnly = true)
    public String job(@PathVariable Long jobId, Model model) {
        JobOrder j = jobs.findById(jobId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        JobContract c = service.contract(jobId);
        model.addAttribute("job", lookups.jobMap().get(jobId));
        model.addAttribute("jobOrder", j);
        model.addAttribute("c", c);
        model.addAttribute("saved", contracts.existsById(jobId));
        model.addAttribute("s", service.summary(jobId));
        model.addAttribute("scope", service.scope(c));
        model.addAttribute("quoteTotal", j.getQuotation() == null ? null : j.getQuotation().getGrandTotal());
        List<ClientBilling> bl = billings.findByJobOrderIdOrderByIdAsc(jobId);
        model.addAttribute("billings", bl);
        model.addAttribute("pendingBilling", bl.stream().filter(b -> ClientBillingService.PENDING.contains(b.status)).findFirst().orElse(null));
        model.addAttribute("collections", collections.findByJobOrderIdOrderByIdAsc(jobId));
        model.addAttribute("bankMap", bankMap());
        model.addAttribute("activePage", "client-billing");
        return "acc-billing-job";
    }

    @PostMapping("/Billing/job/{jobId}/terms")
    public String saveTerms(@PathVariable Long jobId, @RequestParam(required = false) String contractAmount, @RequestParam(required = false) String vatMode,
                            @RequestParam(required = false) String dpPct, @RequestParam(required = false) String retentionPct,
                            @RequestParam(required = false) String cwtRate, @RequestParam(required = false) String termDays,
                            @RequestParam(required = false) String basis, @RequestParam(required = false) String clientRef,
                            @RequestParam(required = false) String billTo, RedirectAttributes ra) {
        try {
            Integer td = blank(termDays) ? null : Integer.valueOf(termDays.trim());
            service.saveTerms(jobId, num(contractAmount), vatMode, num(dpPct), num(retentionPct), num(cwtRate), td, basis, clientRef, billTo, me().getName());
            ra.addFlashAttribute("success", "Billing terms saved.");
        } catch (NumberFormatException e) { ra.addFlashAttribute("error", "Payment terms must be a number of days.");
        } catch (IllegalArgumentException | IllegalStateException e) { ra.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/Accounting/Billing/job/" + jobId;
    }

    @PostMapping("/Billing/job/{jobId}/new")
    public String newBilling(@PathVariable Long jobId, @RequestParam String kind, @RequestParam(required = false) String amount, RedirectAttributes ra) {
        try {
            ClientBilling b = switch (kind) {
                case "DOWNPAYMENT" -> service.draftDownpayment(jobId, num(amount), me());
                case "RETENTION" -> service.draftRetention(jobId, num(amount), me());
                default -> service.draftProgress(jobId, me());
            };
            return "redirect:/Accounting/Billing/" + b.id + "/edit";
        } catch (IllegalArgumentException | IllegalStateException e) {
            ra.addFlashAttribute("error", e.getMessage());
            return "redirect:/Accounting/Billing/job/" + jobId;
        }
    }

    // ================================================================== one billing

    private ClientBilling find(Long id) { return billings.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND)); }

    @GetMapping("/Billing/{id}")
    @Transactional(readOnly = true)
    public String view(@PathVariable Long id, Model model) {
        ClientBilling b = find(id);
        sheet.fill(b, model);
        var me = me();
        model.addAttribute("canApprove", approvals.canApprove(me));
        model.addAttribute("hasSignature", me != null && signatures.existsById(me.getId()));
        model.addAttribute("approverNames", approvals.approvers().stream().map(u -> u.getName()).toList());
        model.addAttribute("clientEmail", service.clientEmail(b.jobOrderId));
        model.addAttribute("clientLink", b.publicToken == null ? null : "/sb/" + b.publicToken);
        List<Map.Entry<ClientCollection, ClientCollectionLine>> pays = new ArrayList<>();
        for (ClientCollectionLine l : collectionLines.findByBillingId(id)) collections.findById(l.collectionId).ifPresent(x -> pays.add(Map.entry(x, l)));
        model.addAttribute("payments", pays);
        List<JournalEntry> es = entries.findBySourceAndSourceIdOrderByIdAsc("BILLING", id);
        JournalEntry je = es.isEmpty() ? null : es.get(0);
        model.addAttribute("journal", je);
        model.addAttribute("jlines", je == null ? List.of() : journalLines.findByJournalIdOrderBySortOrderAscIdAsc(je.id));
        model.addAttribute("preview", false);
        model.addAttribute("accountMap", accountMap());
        model.addAttribute("activePage", "client-billing");
        return "acc-billing-view";
    }

    @GetMapping("/Billing/{id}/edit")
    @Transactional(readOnly = true)
    public String edit(@PathVariable Long id, Model model) {
        ClientBilling b = find(id);
        if (!b.isEditable()) return "redirect:/Accounting/Billing/" + id;
        sheet.fill(b, model);
        var s = service.summary(b.jobOrderId);
        model.addAttribute("s", s);
        BigDecimal prevLines = BigDecimal.ZERO;
        for (ClientBillingLine l : (List<ClientBillingLine>) model.getAttribute("lines")) prevLines = prevLines.add(l.prevAmount);
        model.addAttribute("carried", s.accomplished().subtract(prevLines).max(BigDecimal.ZERO));
        model.addAttribute("room", "DOWNPAYMENT".equals(b.kind) ? service.contract(b.jobOrderId).getDpAmount().subtract(s.dpBilled()) : s.getRetentionUnbilled());
        model.addAttribute("activePage", "client-billing");
        return "acc-billing-form";
    }

    @PostMapping("/Billing/{id}")
    public String save(@PathVariable Long id, @RequestParam(required = false) String asOf, @RequestParam(required = false) String billingDate,
                       @RequestParam(required = false) String dueDate, @RequestParam(required = false) String memo, @RequestParam(required = false) String amount,
                       @RequestParam(defaultValue = "save") String action, @RequestParam Map<String, String> form, RedirectAttributes ra) {
        ClientBilling b = billings.findById(id).orElse(null);
        if (b == null) return "redirect:/Accounting/Billing";
        try {
            if ("PROGRESS".equals(b.kind)) {
                Map<Long, BigDecimal> pcts = new HashMap<>();
                for (var e : form.entrySet()) if (e.getKey().startsWith("pct_")) {
                    Long lid = id(e.getKey().substring(4)); BigDecimal p = num(e.getValue());
                    if (lid != null && p != null) pcts.put(lid, p);
                }
                service.updateProgress(b, pcts, date(asOf), date(billingDate), date(dueDate), memo);
            } else service.updateSimple(b, num(amount), date(billingDate), date(dueDate), memo);
            if ("submit".equals(action)) {
                service.submit(b, me());
                ra.addFlashAttribute("success", b.getTitle() + " submitted for approval: ₱" + ClientBillingService.peso(b.amountDue) + " due from the client.");
                return "redirect:/Accounting/Billing/" + id;
            }
            ra.addFlashAttribute("success", "Draft saved.");
            return "recalc".equals(action) ? "redirect:/Accounting/Billing/" + id + "/edit" : "redirect:/Accounting/Billing/" + id;
        } catch (IllegalArgumentException | IllegalStateException e) {
            ra.addFlashAttribute("error", e.getMessage());
            return "redirect:/Accounting/Billing/" + id + "/edit";
        }
    }

    @PostMapping("/Billing/{id}/{action}")
    public String action(@PathVariable Long id, @PathVariable String action, @RequestParam(required = false) String reason,
                         @RequestParam(required = false) String to, @RequestParam(required = false) String cc,
                         @RequestParam(required = false) String message, RedirectAttributes ra) {
        ClientBilling b = billings.findById(id).orElse(null);
        if (b == null) return "redirect:/Accounting/Billing";
        Long job = b.jobOrderId;
        try {
            switch (action) {
                case "submit" -> { service.submit(b, me()); ra.addFlashAttribute("success", "Submitted for approval."); }
                case "delete" -> { service.deleteDraft(b); ra.addFlashAttribute("success", "Draft deleted."); return "redirect:/Accounting/Billing/job/" + job; }
                case "void" -> { service.voidBilling(b, reason, me()); ra.addFlashAttribute("success", b.number + " voided; its posting was reversed."); }
                case "send" -> {
                    List<String> ccs = blank(cc) ? List.of() : Arrays.stream(cc.split("[,;\\s]+")).filter(x -> x.contains("@")).toList();
                    String err = service.send(b, to, ccs, message, me());
                    if (err != null) ra.addFlashAttribute("error", "The email was not sent: " + err + " You can copy the client link instead.");
                    else ra.addFlashAttribute("success", "Sent to " + to.trim() + ". You'll see here when the client opens it.");
                }
                default -> { }
            }
        } catch (IllegalArgumentException | IllegalStateException e) { ra.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/Accounting/Billing/" + id;
    }

    @GetMapping("/Billing/{id}/print")
    @Transactional(readOnly = true)
    public String print(@PathVariable Long id, Model model) {
        sheet.fill(find(id), model);
        model.addAttribute("publicView", false);
        return "acc-billing-print";
    }

    // ================================================================== collections

    @GetMapping("/Collections")
    @Transactional(readOnly = true)
    public String collectionList(Model model) {
        List<ClientCollection> all = collections.findTop100ByOrderByIdDesc();
        model.addAttribute("rows", all);
        model.addAttribute("missing2307", all.stream().filter(x -> x.isPosted() && x.cwt.signum() > 0 && !x.form2307).toList());
        model.addAttribute("jobMap", lookups.jobMap());
        model.addAttribute("bankMap", bankMap());
        model.addAttribute("activePage", "collections");
        return "acc-collections";
    }

    @GetMapping("/Collections/new")
    @Transactional(readOnly = true)
    public String newCollection(@RequestParam(required = false) Long job, @RequestParam(required = false) Long billing, Model model) {
        if (billing != null) job = billings.findById(billing).map(b -> b.jobOrderId).orElse(job);
        List<ClientBilling> open = billings.findByStatusInOrderByDueDateAscIdAsc(List.of("ISSUED", "PARTIAL"));
        Set<Long> owing = new LinkedHashSet<>();
        open.forEach(b -> owing.add(b.jobOrderId));
        var jm = lookups.jobMap();
        model.addAttribute("owing", owing.stream().map(jm::get).filter(Objects::nonNull).toList());
        model.addAttribute("jobId", job);
        model.addAttribute("job", job == null ? null : jm.get(job));
        final Long jid = job;
        List<ClientBilling> mine = jid == null ? List.of() : open.stream().filter(b -> b.jobOrderId.equals(jid)).toList();
        model.addAttribute("bills", mine);
        model.addAttribute("ticked", billing != null ? Set.of(billing) : (mine.size() == 1 ? Set.of(mine.get(0).id) : Set.of()));
        JobContract c = jid == null ? null : service.contract(jid);
        model.addAttribute("c", c);
        model.addAttribute("vatFactor", c != null && c.isVat() ? 100.0 / 112.0 : 1.0);
        model.addAttribute("banks", banks.findByActiveTrueOrderByKindAscNameAsc());
        model.addAttribute("today", LocalDate.now());
        model.addAttribute("activePage", "collections");
        return "acc-collection-form";
    }

    @PostMapping("/Collections")
    public String saveCollection(@RequestParam(required = false) String jobOrderId, @RequestParam(required = false) String receivedDate,
                                 @RequestParam(required = false) String method, @RequestParam(required = false) String bankId,
                                 @RequestParam(required = false) String chequeNo, @RequestParam(required = false) String chequeBank,
                                 @RequestParam(required = false) String chequeDate, @RequestParam(required = false) String orNo,
                                 @RequestParam(defaultValue = "false") boolean form2307, @RequestParam(required = false) String memo,
                                 @RequestParam Map<String, String> form, RedirectAttributes ra) {
        ClientCollection x = new ClientCollection();
        x.jobOrderId = id(jobOrderId); x.receivedDate = Optional.ofNullable(date(receivedDate)).orElse(LocalDate.now());
        x.method = blank(method) ? "CHEQUE" : method; x.bankId = id(bankId);
        x.chequeNo = "CHEQUE".equals(x.method) ? cut(chequeNo, 40) : (cut(chequeNo, 40));
        x.chequeBank = "CHEQUE".equals(x.method) ? cut(chequeBank, 80) : null; x.chequeDate = "CHEQUE".equals(x.method) ? date(chequeDate) : null;
        x.orNo = cut(orNo, 40); x.form2307 = form2307; x.memo = cut(memo, 500);
        List<ClientBillingService.Apply> applies = new ArrayList<>();
        for (var e : form.entrySet()) {
            if (!e.getKey().startsWith("amt_")) continue;
            Long bid = id(e.getKey().substring(4));
            if (bid == null || !"true".equals(form.get("tick_" + bid))) continue;
            applies.add(new ClientBillingService.Apply(bid, num(e.getValue()), num(form.get("cwt_" + bid))));
        }
        try {
            ClientCollection saved = service.collect(x, applies, me());
            ra.addFlashAttribute("success", "Collection " + saved.number + " recorded: ₱" + ClientBillingService.peso(saved.cash) + " received"
                    + (saved.cwt.signum() > 0 ? " + ₱" + ClientBillingService.peso(saved.cwt) + " tax withheld" : "") + ".");
            return "redirect:/Accounting/Collections/" + saved.id;
        } catch (IllegalArgumentException | IllegalStateException e) {
            ra.addFlashAttribute("error", e.getMessage());
            return "redirect:/Accounting/Collections/new" + (x.jobOrderId == null ? "" : "?job=" + x.jobOrderId);
        }
    }

    @GetMapping("/Collections/{id}")
    @Transactional(readOnly = true)
    public String collection(@PathVariable Long id, Model model) {
        ClientCollection x = collections.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        List<Map.Entry<ClientBilling, ClientCollectionLine>> ls = new ArrayList<>();
        for (ClientCollectionLine l : collectionLines.findByCollectionId(id)) billings.findById(l.billingId).ifPresent(b -> ls.add(Map.entry(b, l)));
        model.addAttribute("x", x);
        model.addAttribute("lines", ls);
        model.addAttribute("job", lookups.jobMap().get(x.jobOrderId));
        model.addAttribute("bank", banks.findById(x.bankId).orElse(null));
        List<JournalEntry> es = entries.findBySourceAndSourceIdOrderByIdAsc("COLLECTION", id);
        JournalEntry je = es.isEmpty() ? null : es.get(0);
        model.addAttribute("journal", je);
        model.addAttribute("jlines", je == null ? List.of() : journalLines.findByJournalIdOrderBySortOrderAscIdAsc(je.id));
        model.addAttribute("preview", false);
        model.addAttribute("accountMap", accountMap());
        model.addAttribute("activePage", "collections");
        return "acc-collection";
    }

    @PostMapping("/Collections/{id}/{action}")
    public String collectionAction(@PathVariable Long id, @PathVariable String action, @RequestParam(required = false) String reason, RedirectAttributes ra) {
        ClientCollection x = collections.findById(id).orElse(null);
        if (x == null) return "redirect:/Accounting/Collections";
        try {
            switch (action) {
                case "void" -> { service.voidCollection(x, reason, me()); ra.addFlashAttribute("success", x.number + " voided; the billings are open again."); }
                case "2307" -> { service.mark2307(x, !x.form2307); ra.addFlashAttribute("success", x.form2307 ? "BIR 2307 marked as received." : "BIR 2307 marked as not yet received."); }
                default -> { }
            }
        } catch (IllegalArgumentException | IllegalStateException e) { ra.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/Accounting/Collections/" + id;
    }

    // ================================================================== helpers

    private com.asmsi.admin_system.entity.User me() { return auth.getCurrentUser(); }
    private Map<Long, Account> accountMap() { Map<Long, Account> m = new HashMap<>(); accounts.findAll().forEach(a -> m.put(a.id, a)); return m; }
    private Map<Long, BankAccount> bankMap() { Map<Long, BankAccount> m = new HashMap<>(); banks.findAll().forEach(b -> m.put(b.id, b)); return m; }
}
