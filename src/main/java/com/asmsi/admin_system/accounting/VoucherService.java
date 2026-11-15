package com.asmsi.admin_system.accounting;

import com.asmsi.admin_system.entity.User;
import com.asmsi.admin_system.entity.UserSignature;
import com.asmsi.admin_system.inventory.InventoryService;
import com.asmsi.admin_system.inventory.Supplier;
import com.asmsi.admin_system.inventory.SupplierRepository;
import com.asmsi.admin_system.repository.OrganizationNodeRepository;
import com.asmsi.admin_system.repository.UserRepository;
import com.asmsi.admin_system.repository.UserSignatureRepository;
import com.asmsi.admin_system.service.AccessService;
import com.asmsi.admin_system.service.EmailService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static org.springframework.web.util.HtmlUtils.htmlEscape;

/**
 * Payment vouchers: Finance prepares, the approver (CEO) approves with his signature, Finance releases.
 * Releasing posts Dr accounts payable / Cr cash or bank / Cr withholding tax payable and settles the bills.
 */
@Service
public class VoucherService {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final PaymentVoucherRepository vouchers;
    private final VoucherLineRepository lines;
    private final BillRepository bills;
    private final PayablesService payables;
    private final BankAccountRepository banks;
    private final SupplierRepository suppliers;
    private final PaymentApproverRepository approvers;
    private final UserRepository users;
    private final UserSignatureRepository signatures;
    private final OrganizationNodeRepository orgNodes;
    private final EmailService email;
    private final Ledger ledger;
    private final InventoryService numbers;

    public VoucherService(PaymentVoucherRepository vouchers, VoucherLineRepository lines, BillRepository bills, PayablesService payables,
                          BankAccountRepository banks, SupplierRepository suppliers, PaymentApproverRepository approvers, UserRepository users,
                          UserSignatureRepository signatures, OrganizationNodeRepository orgNodes, EmailService email, Ledger ledger, InventoryService numbers) {
        this.vouchers = vouchers; this.lines = lines; this.bills = bills; this.payables = payables; this.banks = banks; this.suppliers = suppliers;
        this.approvers = approvers; this.users = users; this.signatures = signatures; this.orgNodes = orgNodes; this.email = email;
        this.ledger = ledger; this.numbers = numbers;
    }

    // ------------------------------------------------------------------ who approves

    public List<User> approvers() {
        Set<Long> ids = new HashSet<>();
        approvers.findAll().forEach(a -> ids.add(a.userId));
        List<User> out = new ArrayList<>();
        for (User u : users.findByIsApprovedTrueOrderByNameAsc())
            if (ids.isEmpty() ? AccessService.isAdminRole(u.getRole()) : ids.contains(u.getId())) out.add(u);
        return out;
    }
    public boolean canApprove(User u) { return u != null && approvers().stream().anyMatch(a -> a.getId().equals(u.getId())); }

    // ------------------------------------------------------------------ prepare

    public record Pay(Long billId, BigDecimal gross, BigDecimal ewtRate) {}

    /** Creates or replaces a draft voucher for one supplier's bills. */
    @Transactional
    public PaymentVoucher save(PaymentVoucher v, List<Pay> pays, User by) {
        if (v.id != null && !v.isEditable()) throw new IllegalStateException("Voucher " + v.number + " is " + v.getStatusLabel().toLowerCase() + " and can't be changed.");
        if (v.supplierId == null) throw new IllegalArgumentException("Choose the supplier to pay.");
        if (!"CASH".equals(v.method) && !"CHEQUE".equals(v.method)) v.method = "CHEQUE";
        if (v.bankId == null) throw new IllegalArgumentException("Choose where the money comes from (cash on hand or a bank account).");
        BankAccount bank = banks.findById(v.bankId).orElseThrow(() -> new IllegalArgumentException("Unknown cash or bank account."));
        if ("CHEQUE".equals(v.method) && !bank.isCheques()) throw new IllegalArgumentException("Cheques are drawn from a bank account. Choose a bank, or pay in cash.");
        if (v.voucherDate == null) v.voucherDate = LocalDate.now();
        List<Pay> use = pays.stream().filter(p -> p.billId() != null && p.gross() != null && p.gross().signum() > 0).toList();
        if (use.isEmpty()) throw new IllegalArgumentException("Tick at least one bill and the amount to pay.");

        // amounts already on other active vouchers count against each bill's balance
        Map<Long, BigDecimal> onOthers = new HashMap<>();
        for (Pay p : use) {
            BigDecimal pending = BigDecimal.ZERO;
            for (VoucherLine vl : lines.findByBillId(p.billId())) {
                if (v.id != null && vl.voucherId.equals(v.id)) continue;
                PaymentVoucher o = vouchers.findById(vl.voucherId).orElse(null);
                if (o != null && List.of("DRAFT", "FOR_APPROVAL", "APPROVED").contains(o.status)) pending = pending.add(vl.gross);
            }
            onOthers.put(p.billId(), pending);
        }
        if (v.id == null) { v.number = numbers.nextNumber("acc_voucher", "DV"); v.preparedBy = by.getName(); v.preparedById = by.getId(); v.preparedAt = LocalDateTime.now(); }
        else lines.deleteAll(lines.findByVoucherId(v.id));
        vouchers.save(v);
        BigDecimal g = BigDecimal.ZERO, w = BigDecimal.ZERO;
        for (Pay p : use) {
            Bill b = bills.findById(p.billId()).orElseThrow(() -> new IllegalArgumentException("A bill on the voucher no longer exists."));
            if (!b.supplierId.equals(v.supplierId)) throw new IllegalArgumentException("Bill " + b.number + " is for another supplier.");
            if (!b.isPayable()) throw new IllegalArgumentException("Bill " + b.number + " is " + b.status.toLowerCase() + ".");
            BigDecimal room = b.getBalance().subtract(onOthers.get(b.id));
            BigDecimal gross = Ledger.r2(p.gross());
            if (gross.compareTo(room) > 0) throw new IllegalArgumentException("Bill " + b.number + " has only ₱" + String.format("%,.2f", room) + " left to pay.");
            BigDecimal rate = p.ewtRate() == null || p.ewtRate().signum() < 0 ? b.ewtRate : p.ewtRate();
            // withholding is on the amount net of VAT: the part of this payment that isn't VAT
            BigDecimal base = b.gross.signum() == 0 ? BigDecimal.ZERO : gross.multiply(b.getNetOfVat()).divide(b.gross, 6, RoundingMode.HALF_UP);
            BigDecimal ewt = Ledger.r2(base.multiply(rate).divide(HUNDRED, 6, RoundingMode.HALF_UP));
            VoucherLine l = new VoucherLine();
            l.voucherId = v.id; l.billId = b.id; l.gross = gross; l.ewtRate = rate; l.ewt = ewt; l.net = gross.subtract(ewt);
            lines.save(l);
            g = g.add(gross); w = w.add(ewt);
        }
        v.gross = g; v.ewt = w; v.net = g.subtract(w);
        v.returnNote = null;
        return vouchers.save(v);
    }

    @Transactional
    public void submit(PaymentVoucher v, User by) {
        if (!"DRAFT".equals(v.status)) throw new IllegalStateException("Only a draft voucher can be submitted.");
        if (lines.findByVoucherId(v.id).isEmpty()) throw new IllegalStateException("The voucher has no bills.");
        v.status = "FOR_APPROVAL"; v.submittedAt = LocalDateTime.now(); v.returnNote = null;
        vouchers.save(v);
        String supplier = supplierName(v);
        for (User a : approvers()) {
            if (a.getId().equals(by.getId()) || a.getEmail() == null) continue;
            String subject = "Payment for approval: " + v.number + " · " + supplier + " · ₱" + String.format("%,.2f", v.net);
            email.sendHtml(a.getEmail(), null, by.getEmail(), subject, mail("<p>" + esc(by.getName()) + " prepared a payment for your approval.</p>"
                    + "<p><b>" + esc(v.number) + "</b> · " + esc(supplier) + " · " + esc("CASH".equals(v.method) ? "cash" : "cheque") + "<br>Amount ₱" + String.format("%,.2f", v.gross)
                    + (v.ewt.signum() > 0 ? ", less withholding ₱" + String.format("%,.2f", v.ewt) : "") + ", to release <b>₱" + String.format("%,.2f", v.net) + "</b></p>", "/Approvals", "Open Approvals"),
                    subject + "\n\n" + email.link("/Approvals"));
        }
    }

    @Transactional
    public void approve(PaymentVoucher v, User by) {
        if (!"FOR_APPROVAL".equals(v.status)) throw new IllegalStateException("Voucher " + v.number + " is not waiting for approval.");
        if (!canApprove(by)) throw new IllegalStateException("You are not set up to approve payments. An administrator can tick \"Approves payment vouchers\" for you in Users.");
        UserSignature sig = signatures.findById(by.getId()).orElseThrow(() -> new IllegalStateException("Save your signature on My profile first."));
        v.status = "APPROVED"; v.approvedBy = by.getName(); v.approvedById = by.getId(); v.approvedAt = LocalDateTime.now();
        v.approvedTitle = orgNodes.findByAccountUserId(by.getId()).stream().map(n -> n.getTitle()).filter(t -> t != null && !t.isBlank()).findFirst().orElse("Approver");
        v.approvedSig = sig.image; v.approvedSigType = sig.contentType;
        vouchers.save(v);
        notifyPreparer(v, by, "Payment approved: " + v.number, "<p>" + esc(by.getName()) + " approved voucher " + esc(v.number) + " (" + esc(supplierName(v))
                + ", ₱" + String.format("%,.2f", v.net) + "). It can be released.</p>");
    }

    @Transactional
    public void returnToDraft(PaymentVoucher v, User by, String note) {
        if (!"FOR_APPROVAL".equals(v.status) && !"APPROVED".equals(v.status)) throw new IllegalStateException("Only a voucher waiting for approval or approved can be returned.");
        v.status = "DRAFT"; v.approvedBy = null; v.approvedById = null; v.approvedAt = null; v.approvedSig = null; v.approvedSigType = null; v.approvedTitle = null;
        v.returnNote = note == null || note.isBlank() ? null : note.trim(); v.returnedBy = by.getName();
        vouchers.save(v);
        notifyPreparer(v, by, "Payment returned: " + v.number, "<p>" + esc(by.getName()) + " returned voucher " + esc(v.number) + " for changes.</p>"
                + (v.returnNote == null ? "" : "<blockquote style='margin:12px 0;padding:10px 14px;border-left:3px solid #c8102e;background:#f7f8fa'>" + esc(v.returnNote) + "</blockquote>"));
    }

    /** Hands the cash or cheque to the supplier: posts the payment and settles the bills. */
    @Transactional
    public void release(PaymentVoucher v, LocalDate releaseDate, String chequeNo, LocalDate chequeDate, String receivedBy, String orNo, User by) {
        if (!"APPROVED".equals(v.status)) throw new IllegalStateException("Only an approved voucher can be released.");
        BankAccount bank = banks.findById(v.bankId).orElseThrow();
        if ("CHEQUE".equals(v.method)) {
            String no = chequeNo == null || chequeNo.isBlank() ? v.chequeNo : chequeNo.trim();
            if (no == null || no.isBlank()) throw new IllegalArgumentException("Enter the cheque number.");
            v.chequeNo = cut(no, 40);
            v.chequeDate = chequeDate != null ? chequeDate : (v.chequeDate != null ? v.chequeDate : releaseDate);
        }
        v.releaseDate = releaseDate == null ? LocalDate.now() : releaseDate;
        v.receivedBy = receivedBy == null || receivedBy.isBlank() ? null : cut(receivedBy.trim(), 160);
        v.orNo = orNo == null || orNo.isBlank() ? null : cut(orNo.trim(), 60);
        String supplier = supplierName(v);
        String memo = "Payment " + v.number + " · " + supplier + ("CHEQUE".equals(v.method) ? " · cheque " + v.chequeNo + (v.isPdc() ? " (PDC " + v.chequeDate + ")" : "") : " · cash");
        List<JournalLine> j = new ArrayList<>();
        List<VoucherLine> vls = lines.findByVoucherId(v.id);
        for (VoucherLine l : vls) {
            Bill b = bills.findById(l.billId).orElseThrow();
            if (!b.isPayable()) throw new IllegalStateException("Bill " + b.number + " is " + b.status.toLowerCase() + ".");
            if (l.gross.compareTo(b.getBalance()) > 0) throw new IllegalStateException("Bill " + b.number + " has only ₱" + String.format("%,.2f", b.getBalance()) + " left to pay.");
            j.add(JournalLine.dr(ledger.id(b.apCode()), l.gross, "Bill " + b.number + (b.invoiceNo == null ? "" : " · invoice " + b.invoiceNo)).supplier(v.supplierId).job(b.jobOrderId));
        }
        j.add(JournalLine.cr(bank.accountId, v.net, memo).supplier(v.supplierId));
        if (v.ewt.signum() > 0) j.add(JournalLine.cr(ledger.id(Acct.EWT_PAYABLE), v.ewt, "Withholding tax · " + v.number + " · " + supplier).supplier(v.supplierId));
        JournalEntry e = ledger.post(v.releaseDate, memo, "PAYMENT", v.id, v.number, j, by.getName());
        v.journalId = e.id; v.status = "RELEASED"; v.releasedBy = by.getName(); v.releasedAt = LocalDateTime.now();
        vouchers.save(v);
        for (VoucherLine l : vls) bills.findById(l.billId).ifPresent(payables::refresh);
    }

    /** Before release: cancelled. After release: the payment is reversed and the bills reopen. */
    @Transactional
    public void cancel(PaymentVoucher v, String reason, User by) {
        switch (v.status) {
            case "CANCELLED", "VOID" -> { return; }
            case "RELEASED" -> {
                if (reason == null || reason.isBlank()) throw new IllegalArgumentException("Give the reason for voiding a released payment.");
                ledger.reverseSource("PAYMENT", v.id, reason, by.getName());
                v.status = "VOID"; v.voidReason = reason.trim();
                vouchers.save(v);
                for (VoucherLine l : lines.findByVoucherId(v.id)) bills.findById(l.billId).ifPresent(payables::refresh);
            }
            default -> { v.status = "CANCELLED"; v.voidReason = reason; vouchers.save(v); }
        }
    }

    // ------------------------------------------------------------------ amount in words (for the printed voucher)

    private static final String[] ONES = {"", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten", "eleven", "twelve",
            "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen"};
    private static final String[] TENS = {"", "", "twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety"};

    public static String inWords(BigDecimal amount) {
        BigDecimal a = amount.setScale(2, RoundingMode.HALF_UP);
        long pesos = a.longValue();
        int cents = a.remainder(BigDecimal.ONE).movePointRight(2).intValue();
        String w = pesos == 0 ? "zero" : words(pesos);
        String s = Character.toUpperCase(w.charAt(0)) + w.substring(1) + " peso" + (pesos == 1 ? "" : "s");
        return s + (cents > 0 ? " and " + String.format("%02d", cents) + "/100" : " only");
    }
    private static String words(long n) {
        if (n < 20) return ONES[(int) n];
        if (n < 100) return TENS[(int) (n / 10)] + (n % 10 == 0 ? "" : "-" + ONES[(int) (n % 10)]);
        if (n < 1000) return ONES[(int) (n / 100)] + " hundred" + (n % 100 == 0 ? "" : " " + words(n % 100));
        String[] big = {"thousand", "million", "billion"};
        long[] div = {1_000, 1_000_000, 1_000_000_000};
        for (int i = big.length - 1; i >= 0; i--)
            if (n >= div[i]) return words(n / div[i]) + " " + big[i] + (n % div[i] == 0 ? "" : " " + words(n % div[i]));
        return "";
    }

    // ------------------------------------------------------------------ helpers

    public String supplierName(PaymentVoucher v) { return suppliers.findById(v.supplierId).map(s -> s.name).orElse("supplier"); }

    private void notifyPreparer(PaymentVoucher v, User by, String subject, String body) {
        if (v.preparedById == null || v.preparedById.equals(by.getId())) return;
        users.findById(v.preparedById).ifPresent(u -> email.sendHtml(u.getEmail(), null, by.getEmail(), subject,
                mail(body, "/Accounting/Payments/" + v.id, "Open the voucher"), subject + "\n\n" + email.link("/Accounting/Payments/" + v.id)));
    }
    String mail(String body, String path, String button) {
        String link = email.link(path);
        return "<div style='font-family:Segoe UI,Arial,sans-serif;color:#0b1524;line-height:1.55;max-width:600px'>"
                + "<div style='padding:14px 0;border-bottom:3px solid #c8102e;margin-bottom:18px;font-weight:700;letter-spacing:.04em'>CBM ENGINEERING &amp; CONSTRUCTION</div>"
                + body + "<p style='margin:22px 0'><a href='" + esc(link) + "' style='display:inline-block;padding:12px 22px;background:#c8102e;color:#fff;text-decoration:none;border-radius:6px;font-weight:600'>" + esc(button) + "</a></p></div>";
    }
    private static String esc(String s) { return s == null ? "" : htmlEscape(s); }
    private static String cut(String s, int n) { return s == null ? null : (s.length() <= n ? s : s.substring(0, n)); }
    public Optional<Supplier> supplier(Long id) { return suppliers.findById(id); }
}
