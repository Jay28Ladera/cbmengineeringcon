package com.asmsi.admin_system.accounting;

import com.asmsi.admin_system.entity.Inquiry;
import com.asmsi.admin_system.entity.JobOrder;
import com.asmsi.admin_system.entity.Quotation;
import com.asmsi.admin_system.entity.QuotationLineItem;
import com.asmsi.admin_system.entity.User;
import com.asmsi.admin_system.entity.UserSignature;
import com.asmsi.admin_system.inventory.InventoryService;
import com.asmsi.admin_system.repository.JobOrderRepository;
import com.asmsi.admin_system.repository.OrganizationNodeRepository;
import com.asmsi.admin_system.repository.UserRepository;
import com.asmsi.admin_system.repository.UserSignatureRepository;
import com.asmsi.admin_system.service.EmailService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static org.springframework.web.util.HtmlUtils.htmlEscape;

/**
 * Client billing for job orders (contractor style): a downpayment billed up front, progress billings by % accomplishment
 * that recoup the downpayment and hold back retention, and a retention release at the end.
 *
 * Postings (amounts as billed, VAT included when the contract has VAT):
 *   Downpayment: Dr AR / Cr customer deposits (net) + output VAT
 *   Progress:    Dr AR (amount due) + retention receivable + customer deposits (recouped, net of VAT) / Cr contract revenue (net) + output VAT
 *   Retention:   Dr AR / Cr retention receivable
 *   Collection:  Dr bank (cash) + creditable withholding tax / Cr AR
 */
@Service
public class ClientBillingService {

    static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    static final BigDecimal VAT_RATE = BigDecimal.valueOf(12);
    static final List<String> ISSUED = List.of("ISSUED", "PARTIAL", "PAID");
    static final List<String> PENDING = List.of("DRAFT", "FOR_APPROVAL");
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ClientBillingRepository billings;
    private final ClientBillingLineRepository lines;
    private final ClientCollectionRepository collections;
    private final ClientCollectionLineRepository collectionLines;
    private final JobContractRepository contracts;
    private final JobOrderRepository jobs;
    private final BankAccountRepository banks;
    private final VoucherService approvals;
    private final UserRepository users;
    private final UserSignatureRepository signatures;
    private final OrganizationNodeRepository orgNodes;
    private final EmailService email;
    private final Ledger ledger;
    private final InventoryService numbers;

    @Value("${app.accounting.vat-registered:true}") private boolean vatRegistered;

    public ClientBillingService(ClientBillingRepository billings, ClientBillingLineRepository lines, ClientCollectionRepository collections,
                                ClientCollectionLineRepository collectionLines, JobContractRepository contracts, JobOrderRepository jobs,
                                BankAccountRepository banks, VoucherService approvals, UserRepository users, UserSignatureRepository signatures,
                                OrganizationNodeRepository orgNodes, EmailService email, Ledger ledger, InventoryService numbers) {
        this.billings = billings; this.lines = lines; this.collections = collections; this.collectionLines = collectionLines; this.contracts = contracts;
        this.jobs = jobs; this.banks = banks; this.approvals = approvals; this.users = users; this.signatures = signatures; this.orgNodes = orgNodes;
        this.email = email; this.ledger = ledger; this.numbers = numbers;
    }

    static BigDecimal r2(BigDecimal v) { return Ledger.r2(v); }
    static BigDecimal pctOf(BigDecimal amount, BigDecimal pct) { return r2(amount.multiply(pct).divide(HUNDRED, 8, RoundingMode.HALF_UP)); }
    static BigDecimal vatIn(BigDecimal amount) { return r2(amount.multiply(VAT_RATE).divide(HUNDRED.add(VAT_RATE), 8, RoundingMode.HALF_UP)); }

    // ================================================================== contract terms

    public JobOrder job(Long id) { return jobs.findById(id).orElseThrow(() -> new IllegalArgumentException("Job order not found.")); }

    /** The saved terms, or terms suggested from the accepted quotation (not saved). */
    @Transactional(readOnly = true)
    public JobContract contract(Long jobId) {
        return contracts.findById(jobId).orElseGet(() -> defaults(job(jobId)));
    }

    JobContract defaults(JobOrder j) {
        JobContract c = new JobContract();
        c.jobOrderId = j.getId();
        Quotation q = j.getQuotation();
        if (q != null) {
            c.contractAmount = r2(q.getGrandTotal());
            c.vatMode = q.getTaxRate().signum() > 0 || vatRegistered ? "VAT" : "NONE";
            Inquiry i = q.getInquiry();
            if (i != null) c.billTo = cut(nz(i.getCompany()).isBlank() ? i.getFullName() : i.getCompany() + " · Attn: " + i.getFullName(), 300);
        }
        return c;
    }

    @Transactional
    public JobContract saveTerms(Long jobId, BigDecimal amount, String vatMode, BigDecimal dp, BigDecimal ret, BigDecimal cwt, Integer termDays,
                                 String basis, String clientRef, String billTo, String by) {
        JobOrder j = job(jobId);
        JobContract c = contracts.findById(jobId).orElseGet(() -> defaults(j));
        Summary s = summary(jobId);
        if (amount == null || amount.signum() <= 0) throw new IllegalArgumentException("Enter the contract amount.");
        if (amount.compareTo(s.accomplished()) < 0) throw new IllegalArgumentException("The contract can't be less than what was already billed as accomplished (₱" + peso(s.accomplished()) + ").");
        if (dp == null || dp.signum() < 0 || dp.compareTo(HUNDRED) > 0) throw new IllegalArgumentException("Downpayment % must be 0 to 100.");
        if (ret == null || ret.signum() < 0 || ret.compareTo(BigDecimal.valueOf(50)) > 0) throw new IllegalArgumentException("Retention % must be 0 to 50.");
        boolean billed = billings.findByJobOrderIdOrderByIdAsc(jobId).stream().anyMatch(b -> !"VOID".equals(b.status));
        String mode = "NONE".equals(vatMode) ? "NONE" : "VAT";
        String bs = "LUMP".equals(basis) ? "LUMP" : "ITEMS";
        if (billed && !mode.equals(c.vatMode)) throw new IllegalStateException("VAT can't change once the job has billings.");
        if (billed && dp.compareTo(c.dpPct) != 0 && s.dpBilled().signum() > 0) throw new IllegalStateException("The downpayment % can't change after the downpayment was billed.");
        boolean progress = billings.findByJobOrderIdOrderByIdAsc(jobId).stream().anyMatch(b -> "PROGRESS".equals(b.kind) && !"VOID".equals(b.status));
        if (progress && !bs.equals(c.basis)) throw new IllegalStateException("The billing basis can't change after a progress billing.");
        c.contractAmount = r2(amount); c.vatMode = mode; c.dpPct = dp; c.retentionPct = ret;
        c.cwtRate = cwt == null || cwt.signum() < 0 ? BigDecimal.ZERO : cwt;
        c.termDays = termDays == null || termDays < 0 ? 15 : termDays; c.basis = bs;
        c.clientRef = cut(clientRef, 80); c.billTo = cut(billTo, 300);
        c.updatedBy = by; c.updatedAt = LocalDateTime.now();
        return contracts.save(c);
    }

    // ================================================================== where a job stands

    public record Summary(BigDecimal contract, BigDecimal dpBilled, BigDecimal recouped, BigDecimal accomplished, BigDecimal pct,
                          BigDecimal retentionHeld, BigDecimal retentionBilled, BigDecimal billed, BigDecimal collected, BigDecimal cwt, int openCount) {
        public BigDecimal getBalance() { return billed.subtract(collected); }
        public BigDecimal getUnbilled() { return contract.subtract(accomplished).max(BigDecimal.ZERO); }
        public BigDecimal getDpUnrecouped() { return dpBilled.subtract(recouped); }
        public BigDecimal getRetentionUnbilled() { return retentionHeld.subtract(retentionBilled); }
        public BigDecimal getPctBilled() { return contract.signum() == 0 ? BigDecimal.ZERO : accomplished.multiply(HUNDRED).divide(contract, 1, RoundingMode.HALF_UP); }
        public BigDecimal getPctCollected() { return contract.signum() == 0 ? BigDecimal.ZERO : collected.multiply(HUNDRED).divide(contract, 1, RoundingMode.HALF_UP); }
    }

    /** Totals from issued billings (drafts don't count). */
    @Transactional(readOnly = true)
    public Summary summary(Long jobId) {
        JobContract c = contracts.findById(jobId).orElse(null);
        BigDecimal contract = c == null ? BigDecimal.ZERO : c.contractAmount;
        BigDecimal dp = BigDecimal.ZERO, rec = BigDecimal.ZERO, acc = BigDecimal.ZERO, pct = BigDecimal.ZERO, held = BigDecimal.ZERO, relB = BigDecimal.ZERO,
                billed = BigDecimal.ZERO, coll = BigDecimal.ZERO;
        int open = 0;
        for (ClientBilling b : billings.findByJobOrderIdOrderByIdAsc(jobId)) {
            if (!b.isIssued()) continue;
            switch (b.kind) {
                case "DOWNPAYMENT" -> dp = dp.add(b.gross);
                case "RETENTION" -> relB = relB.add(b.gross);
                default -> { rec = rec.add(b.recoupment); held = held.add(b.retention); acc = b.grossToDate; pct = b.pctToDate; }
            }
            billed = billed.add(b.amountDue); coll = coll.add(b.collected);
            if (b.isOpen()) open++;
        }
        BigDecimal cwt = BigDecimal.ZERO;
        for (ClientCollection x : collections.findByJobOrderIdOrderByIdAsc(jobId)) if (x.isPosted()) cwt = cwt.add(x.cwt);
        return new Summary(contract, dp, rec, acc, pct, held, relB, billed, coll, cwt, open);
    }

    // ================================================================== scope of work

    public record Scope(Long quoteItemId, String description, BigDecimal value) {}

    /** The contract split into billable items: each quotation line's share of the contract amount (discount and VAT spread pro rata). */
    @Transactional(readOnly = true)
    public List<Scope> scope(JobContract c) {
        JobOrder j = job(c.jobOrderId);
        Quotation q = j.getQuotation();
        List<Scope> out = new ArrayList<>();
        if ("LUMP".equals(c.basis) || q == null || q.getLineItems().isEmpty() || q.getSubtotal().signum() == 0) {
            out.add(new Scope(null, q != null && q.getInquiry() != null ? "Contract works · " + q.getInquiry().getProjectName() : "Contract works", c.contractAmount));
            return out;
        }
        BigDecimal sub = q.getSubtotal(), left = c.contractAmount;
        List<QuotationLineItem> items = q.getLineItems().stream().filter(i -> i.getLineTotal().signum() > 0).toList();
        for (int i = 0; i < items.size(); i++) {
            QuotationLineItem it = items.get(i);
            BigDecimal v = i == items.size() - 1 ? left : r2(c.contractAmount.multiply(it.getLineTotal()).divide(sub, 8, RoundingMode.HALF_UP));
            left = left.subtract(v);
            out.add(new Scope(it.getId(), cut(it.getDescription(), 400), v));
        }
        return out;
    }

    // ================================================================== drafts

    private Optional<ClientBilling> pendingFor(Long jobId) {
        return billings.findByJobOrderIdAndStatusInOrderByIdAsc(jobId, PENDING).stream().findFirst();
    }

    private ClientBilling newBilling(JobContract c, String kind, User by) {
        if (!contracts.existsById(c.jobOrderId)) throw new IllegalStateException("Save the job's billing terms first.");
        pendingFor(c.jobOrderId).ifPresent(p -> { throw new IllegalStateException("Finish " + p.getTitle() + " (" + p.number + ") first: it is still " + p.getStatusLabel().toLowerCase() + "."); });
        ClientBilling b = new ClientBilling();
        b.jobOrderId = c.jobOrderId; b.kind = kind;
        b.seqNo = (int) billings.findByJobOrderIdOrderByIdAsc(c.jobOrderId).stream().filter(x -> kind.equals(x.kind) && !"VOID".equals(x.status)).count() + 1;
        b.billingDate = LocalDate.now(); b.dueDate = b.billingDate.plusDays(c.termDays); b.asOf = b.billingDate;
        b.number = numbers.nextNumber("acc_client_billing", "PB");
        b.preparedBy = by.getName(); b.preparedById = by.getId(); b.preparedAt = LocalDateTime.now();
        return b;
    }

    @Transactional
    public ClientBilling draftDownpayment(Long jobId, BigDecimal amount, User by) {
        JobContract c = contract(jobId);
        ClientBilling b = newBilling(c, "DOWNPAYMENT", by);
        BigDecimal room = c.getDpAmount().subtract(summary(jobId).dpBilled());
        BigDecimal a = amount == null ? room : r2(amount);
        if (a.signum() <= 0) throw new IllegalArgumentException("The downpayment (" + c.dpPct.stripTrailingZeros().toPlainString() + "% = ₱" + peso(c.getDpAmount()) + ") is already billed.");
        if (a.compareTo(room) > 0) throw new IllegalArgumentException("Only ₱" + peso(room) + " of the downpayment is left to bill.");
        b.gross = a; b.amountDue = a; b.vat = c.isVat() ? vatIn(a) : BigDecimal.ZERO;
        b.memo = "Downpayment " + c.dpPct.stripTrailingZeros().toPlainString() + "% of the contract price, to be recouped from the progress billings.";
        return billings.save(b);
    }

    @Transactional
    public ClientBilling draftRetention(Long jobId, BigDecimal amount, User by) {
        JobContract c = contract(jobId);
        ClientBilling b = newBilling(c, "RETENTION", by);
        BigDecimal room = summary(jobId).getRetentionUnbilled();
        BigDecimal a = amount == null ? room : r2(amount);
        if (a.signum() <= 0) throw new IllegalArgumentException("There is no retention held to release.");
        if (a.compareTo(room) > 0) throw new IllegalArgumentException("Only ₱" + peso(room) + " of retention is held.");
        b.gross = a; b.amountDue = a; b.vat = BigDecimal.ZERO;
        b.memo = "Release of retention held from the progress billings.";
        return billings.save(b);
    }

    /** A progress billing pre-filled with each item's % accomplished on the last issued billing. */
    @Transactional
    public ClientBilling draftProgress(Long jobId, User by) {
        JobContract c = contract(jobId);
        ClientBilling b = newBilling(c, "PROGRESS", by);
        Map<String, ClientBillingLine> prev = lastLines(jobId);
        billings.save(b);
        int n = 0;
        for (Scope s : scope(c)) {
            ClientBillingLine l = new ClientBillingLine();
            l.billingId = b.id; l.quoteItemId = s.quoteItemId(); l.description = s.description(); l.contractValue = s.value(); l.sortOrder = n++;
            ClientBillingLine p = prev.get(key(s.quoteItemId(), s.description()));
            if (p != null) { l.prevPct = p.toDatePct; l.prevAmount = p.toDateAmount; }
            l.toDatePct = l.prevPct; l.toDateAmount = l.prevAmount;
            lines.save(l);
        }
        // anything billed before on an item no longer in the scope stays billed
        compute(b, c);
        return billings.save(b);
    }

    private static String key(Long quoteItemId, String description) { return quoteItemId != null ? "q" + quoteItemId : "d" + description; }

    private Map<String, ClientBillingLine> lastLines(Long jobId) {
        Map<String, ClientBillingLine> m = new HashMap<>();
        ClientBilling last = null;
        for (ClientBilling x : billings.findByJobOrderIdOrderByIdAsc(jobId)) if ("PROGRESS".equals(x.kind) && x.isIssued()) last = x;
        if (last != null) for (ClientBillingLine l : lines.findByBillingIdOrderBySortOrderAscIdAsc(last.id)) m.put(key(l.quoteItemId, l.description), l);
        return m;
    }

    /** Updates a draft progress billing: % to date per line id. */
    @Transactional
    public ClientBilling updateProgress(ClientBilling b, Map<Long, BigDecimal> pcts, LocalDate asOf, LocalDate billingDate, LocalDate dueDate, String memo) {
        if (!b.isEditable()) throw new IllegalStateException(b.number + " is " + b.getStatusLabel().toLowerCase() + " and can't be changed.");
        JobContract c = contract(b.jobOrderId);
        for (ClientBillingLine l : lines.findByBillingIdOrderBySortOrderAscIdAsc(b.id)) {
            BigDecimal p = pcts.get(l.id);
            if (p == null) continue;
            p = p.setScale(3, RoundingMode.HALF_UP);
            if (p.compareTo(l.prevPct) < 0) throw new IllegalArgumentException(l.description + ": can't go below the " + l.prevPct.stripTrailingZeros().toPlainString() + "% already billed.");
            if (p.compareTo(HUNDRED) > 0) throw new IllegalArgumentException(l.description + ": can't be more than 100%.");
            l.toDatePct = p;
            l.toDateAmount = p.compareTo(HUNDRED) == 0 ? l.contractValue : pctOf(l.contractValue, p).max(l.prevAmount);
            lines.save(l);
        }
        applyDates(b, c, asOf, billingDate, dueDate);
        b.memo = cut(memo, 600);
        compute(b, c);
        return billings.save(b);
    }

    @Transactional
    public ClientBilling updateSimple(ClientBilling b, BigDecimal amount, LocalDate billingDate, LocalDate dueDate, String memo) {
        if (!b.isEditable()) throw new IllegalStateException(b.number + " is " + b.getStatusLabel().toLowerCase() + " and can't be changed.");
        JobContract c = contract(b.jobOrderId);
        if (amount != null && amount.signum() > 0) {
            Summary s = summary(b.jobOrderId);
            BigDecimal room = "DOWNPAYMENT".equals(b.kind) ? c.getDpAmount().subtract(s.dpBilled()) : s.getRetentionUnbilled();
            if (r2(amount).compareTo(room) > 0) throw new IllegalArgumentException("Only ₱" + peso(room) + " is left to bill.");
            b.gross = r2(amount); b.amountDue = b.gross;
            b.vat = "DOWNPAYMENT".equals(b.kind) && c.isVat() ? vatIn(b.gross) : BigDecimal.ZERO;
        }
        applyDates(b, c, null, billingDate, dueDate);
        b.memo = cut(memo, 600);
        return billings.save(b);
    }

    private void applyDates(ClientBilling b, JobContract c, LocalDate asOf, LocalDate billingDate, LocalDate dueDate) {
        if (billingDate != null) b.billingDate = billingDate;
        b.dueDate = dueDate != null ? dueDate : b.billingDate.plusDays(c.termDays);
        if (b.dueDate.isBefore(b.billingDate)) throw new IllegalArgumentException("The due date can't be before the billing date.");
        if (asOf != null) b.asOf = asOf;
    }

    /** Works out this billing's figures from its lines: work this period, downpayment recouped, retention held, amount due and VAT. */
    void compute(ClientBilling b, JobContract c) {
        List<ClientBillingLine> ls = lines.findByBillingIdOrderBySortOrderAscIdAsc(b.id);
        BigDecimal toDate = BigDecimal.ZERO, prev = BigDecimal.ZERO, value = BigDecimal.ZERO;
        for (ClientBillingLine l : ls) { toDate = toDate.add(l.toDateAmount); prev = prev.add(l.prevAmount); value = value.add(l.contractValue); }
        Summary s = summary(b.jobOrderId);
        // billed before on items since removed from the scope still counts
        BigDecimal carried = s.accomplished().subtract(prev).max(BigDecimal.ZERO);
        b.prevGross = prev.add(carried); b.grossToDate = toDate.add(carried);
        b.gross = toDate.subtract(prev);
        b.pctToDate = c.contractAmount.signum() == 0 ? BigDecimal.ZERO : b.grossToDate.multiply(HUNDRED).divide(c.contractAmount, 3, RoundingMode.HALF_UP).min(HUNDRED);
        BigDecimal unrecouped = s.getDpUnrecouped().max(BigDecimal.ZERO);
        boolean last = b.grossToDate.compareTo(c.contractAmount) >= 0;
        b.recoupment = last ? unrecouped : pctOf(b.gross, c.dpPct).min(unrecouped);
        b.retention = pctOf(b.gross, c.retentionPct);
        b.amountDue = b.gross.subtract(b.recoupment).subtract(b.retention);
        b.vat = c.isVat() ? vatIn(b.gross.subtract(b.recoupment)) : BigDecimal.ZERO;
    }

    @Transactional
    public void deleteDraft(ClientBilling b) {
        if (!b.isEditable()) throw new IllegalStateException("Only a draft can be deleted.");
        lines.deleteAll(lines.findByBillingIdOrderBySortOrderAscIdAsc(b.id));
        billings.delete(b);
    }

    // ================================================================== approval

    @Transactional
    public void submit(ClientBilling b, User by) {
        if (!b.isEditable()) throw new IllegalStateException("Only a draft can be submitted.");
        if (b.amountDue.signum() <= 0) throw new IllegalStateException("Nothing to bill: enter the % accomplished to date (higher than the last billing).");
        b.status = "FOR_APPROVAL"; b.submittedAt = LocalDateTime.now(); b.returnNote = null;
        billings.save(b);
        String jobLabel = jobLabel(b.jobOrderId);
        for (User a : approvals.approvers()) {
            if (a.getId().equals(by.getId()) || a.getEmail() == null) continue;
            String subject = "Billing for approval: " + b.number + " · " + jobLabel + " · ₱" + peso(b.amountDue);
            email.sendHtml(a.getEmail(), null, by.getEmail(), subject, approvals.mail("<p>" + esc(by.getName()) + " prepared a client billing for your approval.</p>"
                    + "<p><b>" + esc(b.getTitle()) + "</b> (" + esc(b.number) + ") · " + esc(jobLabel) + "<br>Amount due <b>₱" + peso(b.amountDue) + "</b></p>", "/Approvals", "Open Approvals"),
                    subject + "\n\n" + email.link("/Approvals"));
        }
    }

    /** Approves and issues the billing: posts it to the ledger and opens it for collection. */
    @Transactional
    public void approve(ClientBilling b, User by) {
        if (!"FOR_APPROVAL".equals(b.status)) throw new IllegalStateException(b.number + " is not waiting for approval.");
        if (!approvals.canApprove(by)) throw new IllegalStateException("You are not set up to approve billings. An administrator can tick \"Approves payment vouchers and client billings\" for you in Users.");
        UserSignature sig = signatures.findById(by.getId()).orElseThrow(() -> new IllegalStateException("Save your signature on My profile first."));
        JobContract c = contract(b.jobOrderId);
        if ("PROGRESS".equals(b.kind)) { compute(b, c); if (b.amountDue.signum() <= 0) throw new IllegalStateException("Nothing left to bill on this draft."); }
        b.status = "ISSUED"; b.approvedBy = by.getName(); b.approvedById = by.getId(); b.approvedAt = LocalDateTime.now();
        b.approvedTitle = orgNodes.findByAccountUserId(by.getId()).stream().map(n -> n.getTitle()).filter(t -> t != null && !t.isBlank()).findFirst().orElse("Approver");
        b.approvedSig = sig.image; b.approvedSigType = sig.contentType;
        if (b.publicToken == null) b.publicToken = token();
        b.journalId = post(b, c, by.getName()).id;
        billings.save(b);
        if (b.preparedById != null && !b.preparedById.equals(by.getId()))
            users.findById(b.preparedById).ifPresent(u -> email.sendHtml(u.getEmail(), null, by.getEmail(), "Billing approved: " + b.number,
                    approvals.mail("<p>" + esc(by.getName()) + " approved " + esc(b.getTitle()) + " (" + esc(b.number) + "), ₱" + peso(b.amountDue) + ". It can be sent to the client.</p>",
                            "/Accounting/Billing/" + b.id, "Open the billing"), "Billing approved\n\n" + email.link("/Accounting/Billing/" + b.id)));
    }

    @Transactional
    public void returnToDraft(ClientBilling b, User by, String note) {
        if (!"FOR_APPROVAL".equals(b.status)) throw new IllegalStateException("Only a billing waiting for approval can be returned.");
        b.status = "DRAFT"; b.returnNote = note == null || note.isBlank() ? null : cut(note.trim(), 600); b.returnedBy = by.getName();
        billings.save(b);
        if (b.preparedById != null && !b.preparedById.equals(by.getId()))
            users.findById(b.preparedById).ifPresent(u -> email.sendHtml(u.getEmail(), null, by.getEmail(), "Billing returned: " + b.number,
                    approvals.mail("<p>" + esc(by.getName()) + " returned " + esc(b.number) + " for changes.</p>" + (b.returnNote == null ? "" : "<blockquote>" + esc(b.returnNote) + "</blockquote>"),
                            "/Accounting/Billing/" + b.id, "Open the billing"), "Billing returned\n\n" + email.link("/Accounting/Billing/" + b.id)));
    }

    private JournalEntry post(ClientBilling b, JobContract c, String by) {
        Long job = b.jobOrderId;
        String memo = b.getTitle() + " " + b.number + " · " + jobLabel(job);
        List<JournalLine> j = new ArrayList<>();
        switch (b.kind) {
            case "DOWNPAYMENT" -> {
                j.add(JournalLine.dr(ledger.id(Acct.AR_CLIENTS), b.amountDue, "Downpayment billed").job(job));
                j.add(JournalLine.cr(ledger.id(Acct.CUSTOMER_DEPOSITS), b.gross.subtract(b.vat), "Downpayment received in advance").job(job));
                if (b.vat.signum() > 0) j.add(JournalLine.cr(ledger.id(Acct.OUTPUT_VAT), b.vat, "Output VAT · " + b.number).job(job));
            }
            case "RETENTION" -> {
                j.add(JournalLine.dr(ledger.id(Acct.AR_CLIENTS), b.amountDue, "Retention released for collection").job(job));
                j.add(JournalLine.cr(ledger.id(Acct.RETENTION_RECEIVABLE), b.amountDue, "Retention").job(job));
            }
            default -> {
                BigDecimal revenue = c.isVat() ? b.gross.subtract(vatIn(b.gross)) : b.gross;
                // the recouped downpayment's VAT was charged when the downpayment was billed; the rest balances to the cent
                BigDecimal depositNet = revenue.add(b.vat).subtract(b.gross.subtract(b.recoupment));
                j.add(JournalLine.dr(ledger.id(Acct.AR_CLIENTS), b.amountDue, "Amount due").job(job));
                if (b.retention.signum() > 0) j.add(JournalLine.dr(ledger.id(Acct.RETENTION_RECEIVABLE), b.retention, "Retention " + c.retentionPct.stripTrailingZeros().toPlainString() + "%").job(job));
                if (depositNet.signum() != 0) j.add(JournalLine.dr(ledger.id(Acct.CUSTOMER_DEPOSITS), depositNet, "Downpayment recouped").job(job));
                j.add(JournalLine.cr(ledger.id(Acct.CONTRACT_REVENUE), revenue, "Work accomplished this period").job(job));
                if (b.vat.signum() > 0) j.add(JournalLine.cr(ledger.id(Acct.OUTPUT_VAT), b.vat, "Output VAT · " + b.number).job(job));
            }
        }
        return ledger.post(b.billingDate, memo, "BILLING", b.id, b.number, j, by);
    }

    @Transactional
    public void voidBilling(ClientBilling b, String reason, User by) {
        if (!b.isIssued()) throw new IllegalStateException("Only an issued billing can be voided; a draft can simply be deleted.");
        if (b.collected.signum() > 0) throw new IllegalStateException("Payments were already collected on " + b.number + ". Void those collections first.");
        if ("PROGRESS".equals(b.kind)) {
            ClientBilling later = billings.findByJobOrderIdOrderByIdAsc(b.jobOrderId).stream()
                    .filter(x -> x.id > b.id && "PROGRESS".equals(x.kind) && !"VOID".equals(x.status)).findFirst().orElse(null);
            if (later != null) throw new IllegalStateException("Progress billing " + later.number + " was made after this one. Void or delete it first.");
        }
        if ("DOWNPAYMENT".equals(b.kind) && summary(b.jobOrderId).recouped().signum() > 0)
            throw new IllegalStateException("The downpayment is already being recouped in progress billings.");
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("Give the reason for voiding.");
        ledger.reverseSource("BILLING", b.id, reason.trim(), by.getName());
        b.status = "VOID"; b.voidReason = cut(reason.trim(), 300);
        billings.save(b);
    }

    // ================================================================== send to the client

    @Transactional
    public String send(ClientBilling b, String to, List<String> cc, String message, User by) {
        if (!b.isIssued()) throw new IllegalStateException("Only an approved billing can be sent.");
        if (to == null || !to.contains("@")) throw new IllegalArgumentException("Enter the client's email address.");
        if (b.publicToken == null) b.publicToken = token();
        String link = email.link("/sb/" + b.publicToken);
        String subject = b.getTitle() + " · " + jobLabel(b.jobOrderId) + " · ₱" + peso(b.amountDue);
        String html = "<div style='font-family:Segoe UI,Arial,sans-serif;color:#0b1524;line-height:1.55;max-width:600px'>"
                + "<div style='padding:14px 0;border-bottom:3px solid #c8102e;margin-bottom:18px;font-weight:700;letter-spacing:.04em'>CBM ENGINEERING &amp; CONSTRUCTION</div>"
                + (message == null || message.isBlank() ? "" : "<p style='white-space:pre-line'>" + esc(message.trim()) + "</p>")
                + "<p><b>" + esc(b.getTitle()) + "</b> (" + esc(b.number) + ")<br>Amount due: <b>₱" + peso(b.amountDue) + "</b><br>Due on " + b.dueDate + "</p>"
                + "<p style='margin:22px 0'><a href='" + esc(link) + "' style='display:inline-block;padding:12px 22px;background:#c8102e;color:#fff;text-decoration:none;border-radius:6px;font-weight:600'>View and print the billing</a></p>"
                + "<p style='color:#526176;font-size:13px'>Please send the BIR Form 2307 for any tax withheld together with your payment.</p></div>";
        String err = email.trySendHtml(to.trim(), cc, by.getEmail(), subject, html, subject + "\n\n" + link);
        if (err != null) return err;
        b.sentAt = LocalDateTime.now(); b.sentTo = cut(to.trim(), 300);
        billings.save(b);
        return null;
    }

    @Transactional
    public Optional<ClientBilling> openForClient(String token) {
        if (token == null || token.length() < 20) return Optional.empty();
        return billings.findByPublicToken(token).filter(ClientBilling::isIssued).map(b -> {
            if (b.viewedAt == null) { b.viewedAt = LocalDateTime.now(); billings.save(b); }
            return b;
        });
    }

    // ================================================================== collections

    public record Apply(Long billingId, BigDecimal applied, BigDecimal cwt) {}

    /** Records money received: Dr bank (cash) + CWT / Cr AR, and updates the billings. */
    @Transactional
    public ClientCollection collect(ClientCollection x, List<Apply> applies, User by) {
        if (x.jobOrderId == null) throw new IllegalArgumentException("Choose the job order.");
        if (x.bankId == null) throw new IllegalArgumentException("Choose where the money was deposited.");
        BankAccount bank = banks.findById(x.bankId).orElseThrow(() -> new IllegalArgumentException("Unknown bank or cash account."));
        if (x.receivedDate == null) x.receivedDate = LocalDate.now();
        if (x.method == null || !List.of("CASH", "CHEQUE", "TRANSFER").contains(x.method)) x.method = "CHEQUE";
        if (!"CASH".equals(x.method) && !bank.isCheques()) throw new IllegalArgumentException("Cheques and transfers are deposited to a bank account.");
        JobContract c = contract(x.jobOrderId);
        List<Apply> use = applies.stream().filter(a -> a.billingId() != null && a.applied() != null && a.applied().signum() > 0).toList();
        if (use.isEmpty()) throw new IllegalArgumentException("Tick the billing(s) this payment is for and the amount.");
        x.number = numbers.nextNumber("acc_collection", "CR");
        x.createdBy = by.getName();
        collections.save(x);
        BigDecimal applied = BigDecimal.ZERO, cwt = BigDecimal.ZERO;
        List<ClientBilling> touched = new ArrayList<>();
        for (Apply a : use) {
            ClientBilling b = billings.findById(a.billingId()).orElseThrow(() -> new IllegalArgumentException("A billing no longer exists."));
            if (!b.jobOrderId.equals(x.jobOrderId)) throw new IllegalArgumentException(b.number + " is for another job.");
            if (!b.isOpen()) throw new IllegalArgumentException(b.number + " is " + b.getStatusLabel().toLowerCase() + ".");
            BigDecimal amt = r2(a.applied());
            if (amt.compareTo(b.getBalance()) > 0) throw new IllegalArgumentException(b.number + " has only ₱" + peso(b.getBalance()) + " left to collect.");
            BigDecimal w = a.cwt() != null ? r2(a.cwt()) : defaultCwt(amt, c);
            if (w.signum() < 0 || w.compareTo(amt) >= 0) throw new IllegalArgumentException("Check the tax withheld on " + b.number + ".");
            ClientCollectionLine l = new ClientCollectionLine();
            l.collectionId = x.id; l.billingId = b.id; l.applied = amt; l.cwt = w;
            collectionLines.save(l);
            applied = applied.add(amt); cwt = cwt.add(w); touched.add(b);
        }
        x.applied = applied; x.cwt = cwt; x.cash = applied.subtract(cwt);
        if (x.form2307 && x.form2307At == null) x.form2307At = x.receivedDate;
        List<JournalLine> j = new ArrayList<>();
        j.add(JournalLine.dr(bank.accountId, x.cash, bank.name + (x.chequeNo != null ? " · cheque " + x.chequeNo : "")).job(x.jobOrderId));
        if (cwt.signum() > 0) j.add(JournalLine.dr(ledger.id(Acct.CWT), cwt, "Tax withheld by the client (BIR 2307)").job(x.jobOrderId));
        j.add(JournalLine.cr(ledger.id(Acct.AR_CLIENTS), applied, "Collected").job(x.jobOrderId));
        x.journalId = ledger.post(x.receivedDate, "Collection " + x.number + " · " + jobLabel(x.jobOrderId) + (x.orNo != null ? " · OR " + x.orNo : ""),
                "COLLECTION", x.id, x.number, j, by.getName()).id;
        collections.save(x);
        for (ClientBilling b : touched) refresh(b);
        return x;
    }

    BigDecimal defaultCwt(BigDecimal amount, JobContract c) {
        BigDecimal base = c.isVat() ? amount.subtract(vatIn(amount)) : amount;
        return pctOf(base, c.cwtRate);
    }

    @Transactional
    public void voidCollection(ClientCollection x, String reason, User by) {
        if (!x.isPosted()) return;
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("Give the reason (e.g. bounced cheque).");
        ledger.reverseSource("COLLECTION", x.id, reason.trim(), by.getName());
        x.status = "VOID"; x.voidReason = cut(reason.trim(), 300);
        collections.save(x);
        for (ClientCollectionLine l : collectionLines.findByCollectionId(x.id)) billings.findById(l.billingId).ifPresent(this::refresh);
    }

    @Transactional
    public void mark2307(ClientCollection x, boolean received) {
        x.form2307 = received; x.form2307At = received ? LocalDate.now() : null;
        collections.save(x);
    }

    void refresh(ClientBilling b) {
        BigDecimal got = BigDecimal.ZERO;
        for (ClientCollectionLine l : collectionLines.findByBillingId(b.id)) {
            ClientCollection x = collections.findById(l.collectionId).orElse(null);
            if (x != null && x.isPosted()) got = got.add(l.applied);
        }
        b.collected = got;
        if (b.isIssued()) b.status = got.signum() == 0 ? "ISSUED" : (got.compareTo(b.amountDue) >= 0 ? "PAID" : "PARTIAL");
        billings.save(b);
    }

    // ================================================================== helpers

    public String jobLabel(Long jobId) {
        return jobs.findById(jobId).map(j -> (j.getJobOrderNumber() == null ? "JO #" + j.getId() : j.getJobOrderNumber())
                + (j.getQuotation() != null && j.getQuotation().getInquiry() != null ? " · " + j.getQuotation().getInquiry().getProjectName() : "")).orElse("job");
    }
    /** Client email from the inquiry behind the job. */
    public String clientEmail(Long jobId) {
        return jobs.findById(jobId).map(j -> j.getQuotation() != null && j.getQuotation().getInquiry() != null ? j.getQuotation().getInquiry().getEmail() : null).orElse(null);
    }
    static String peso(BigDecimal v) { return String.format("%,.2f", v); }
    private static String esc(String s) { return s == null ? "" : htmlEscape(s); }
    private static String nz(String s) { return s == null ? "" : s; }
    static String cut(String s, int n) { if (s == null) return null; s = s.trim(); return s.isEmpty() ? null : (s.length() <= n ? s : s.substring(0, n)); }
    private static String token() { byte[] b = new byte[24]; RANDOM.nextBytes(b); return Base64.getUrlEncoder().withoutPadding().encodeToString(b); }
}
