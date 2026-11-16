package com.asmsi.admin_system.service;

import com.asmsi.admin_system.entity.*;
import com.asmsi.admin_system.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static org.springframework.web.util.HtmlUtils.htmlEscape;

/**
 * Quotation approval and client sign-off.
 *
 *   DRAFT ──submit──▶ FOR_APPROVAL ──approve──▶ APPROVED ──send (email or by hand)──▶ SENT ──client signs──▶ ACCEPTED
 *     ▲                    │                        │                                    └──client declines──▶ REJECTED
 *     └──── return to draft (with a note) ──────────┘
 *
 * Approvers are the users ticked "Can approve quotations" in Users. If nobody is ticked yet, administrators approve.
 * The approver's saved signature is copied onto the quotation when they approve, so changing it later does not
 * alter quotations already approved.
 */
@Service
public class QuoteWorkflowService {

    public static final int DEFAULT_VALID_DAYS = 30;
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("MMMM d, yyyy");
    private static final SecureRandom RANDOM = new SecureRandom();

    private final QuotationRepository quotations;
    private final QuotationSignoffRepository signoffs;
    private final QuoteApproverRepository approvers;
    private final UserRepository users;
    private final UserSignatureRepository signatures;
    private final OrganizationNodeRepository orgNodes;
    private final EmailService email;

    @org.springframework.beans.factory.annotation.Autowired
    private com.asmsi.admin_system.admin.SettingsService settings;

    public QuoteWorkflowService(QuotationRepository quotations, QuotationSignoffRepository signoffs, QuoteApproverRepository approvers,
                                UserRepository users, UserSignatureRepository signatures, OrganizationNodeRepository orgNodes, EmailService email) {
        this.quotations = quotations; this.signoffs = signoffs; this.approvers = approvers; this.users = users;
        this.signatures = signatures; this.orgNodes = orgNodes; this.email = email;
    }

    // ------------------------------------------------------------------ who approves

    /** Users who approve quotations: those ticked in Users, or every administrator while nobody is ticked. */
    @Transactional(readOnly = true)
    public List<User> approvers() {
        Set<Long> ids = new HashSet<>();
        approvers.findAll().forEach(a -> ids.add(a.userId));
        List<User> out = new ArrayList<>();
        for (User u : users.findByIsApprovedTrueOrderByNameAsc())
            if (ids.isEmpty() ? AccessService.isAdminRole(u.getRole()) : ids.contains(u.getId())) out.add(u);
        return out;
    }

    public boolean noApproversChosen() { return approvers.count() == 0; }

    public boolean canApprove(User u) {
        return u != null && approvers().stream().anyMatch(a -> a.getId().equals(u.getId()));
    }

    /** Email of whoever submitted the quotation for approval (to copy them when the approver sends it). */
    public String submitterEmail(QuotationSignoff so) {
        return so == null || so.submittedById == null ? null : users.findById(so.submittedById).map(User::getEmail).orElse(null);
    }

    public boolean hasSignature(User u) { return u != null && signatures.existsById(u.getId()); }

    public QuotationSignoff signoff(Long quotationId) {
        return signoffs.findById(quotationId).orElseGet(() -> new QuotationSignoff(quotationId));
    }

    private String titleOf(User u) {
        return orgNodes.findByAccountUserId(u.getId()).stream().map(OrganizationNode::getTitle)
                .filter(t -> t != null && !t.isBlank()).findFirst().orElse("Authorized signatory");
    }

    // ------------------------------------------------------------------ internal steps

    @Transactional
    public void submit(Quotation q, User by) {
        if (!"DRAFT".equals(q.getStatus())) throw new IllegalStateException("Only a draft can be submitted for approval.");
        if (q.getLineItems().isEmpty()) throw new IllegalStateException("Save at least one priced item before submitting.");
        if (q.getGrandTotal().signum() <= 0) throw new IllegalStateException("The quotation total is zero. Check the prices before submitting.");
        QuotationSignoff so = signoff(q.getId());
        so.submittedBy = by.getName(); so.submittedById = by.getId(); so.submittedAt = LocalDateTime.now();
        so.returnNote = null; so.returnedBy = null; so.returnedAt = null;
        signoffs.save(so);
        q.setStatus("FOR_APPROVAL");
        quotations.save(q);

        String link = email.link("/Approvals");
        for (User a : approvers()) {
            if (a.getId().equals(by.getId())) continue;
            String subject = "For approval: " + q.getQuotationNumber() + " · " + nz(q.getInquiry().getProjectName());
            String body = "<p>Hi " + esc(first(a.getName())) + ",</p><p><b>" + esc(by.getName()) + "</b> submitted a quotation for your approval.</p>"
                    + summaryTable(q) + button(link, "Open Approvals") + "<p style='color:#6b7785;font-size:13px'>Prices are locked while it waits for approval. You can approve it, or return it with a note.</p>";
            email.sendHtml(a.getEmail(), null, by.getEmail(), subject, wrap(body), subject + "\n\nReview it here: " + link);
        }
    }

    @Transactional
    public void approve(Quotation q, User by) { approve(q, by, true); }

    /** Approves; notifySubmitter=false when the approver sends it to the client at once (the preparer is on that email's CC). */
    @Transactional
    public void approve(Quotation q, User by, boolean notifySubmitter) {
        if (!"FOR_APPROVAL".equals(q.getStatus())) throw new IllegalStateException("This quotation is not waiting for approval.");
        if (!canApprove(by)) throw new IllegalStateException("You are not set up to approve quotations. An administrator can tick \"Can approve quotations\" for you in Users.");
        UserSignature sig = signatures.findById(by.getId())
                .orElseThrow(() -> new IllegalStateException("Save your signature on My profile first. It is printed on the quotations you approve."));
        QuotationSignoff so = signoff(q.getId());
        so.approverId = by.getId(); so.approverName = by.getName(); so.approverTitle = titleOf(by);
        so.approvedAt = LocalDateTime.now(); so.approverSig = sig.image; so.approverSigType = sig.contentType;
        so.returnNote = null;
        signoffs.save(so);
        q.setStatus("APPROVED");
        quotations.save(q);

        if (notifySubmitter) notifySubmitter(q, so, by, "Approved: " + q.getQuotationNumber(),
                "<p><b>" + esc(by.getName()) + "</b> approved quotation " + esc(q.getQuotationNumber()) + ". It is ready to send to the client.</p>", "Send to client");
    }

    /** Back to draft so the prices can be changed. Clears the approval; a note tells the preparer what to fix. */
    @Transactional
    public void returnToDraft(Quotation q, User by, String note) {
        if (!"FOR_APPROVAL".equals(q.getStatus()) && !"APPROVED".equals(q.getStatus()))
            throw new IllegalStateException("Only a quotation waiting for approval or approved but not yet sent can go back to draft.");
        QuotationSignoff so = signoff(q.getId());
        boolean asApprover = canApprove(by) && (so.submittedById == null || !so.submittedById.equals(by.getId()));
        so.approverId = null; so.approverName = null; so.approverTitle = null; so.approvedAt = null; so.approverSig = null; so.approverSigType = null;
        so.returnNote = note == null || note.isBlank() ? null : note.trim();
        so.returnedBy = by.getName(); so.returnedAt = LocalDateTime.now();
        signoffs.save(so);
        q.setStatus("DRAFT");
        quotations.save(q);
        if (asApprover)
            notifySubmitter(q, so, by, "Returned for changes: " + q.getQuotationNumber(),
                    "<p><b>" + esc(by.getName()) + "</b> returned quotation " + esc(q.getQuotationNumber()) + " for changes.</p>"
                            + (so.returnNote == null ? "" : "<blockquote style='margin:12px 0;padding:10px 14px;border-left:3px solid #c8102e;background:#f7f8fa'>" + esc(so.returnNote).replace("\n", "<br>") + "</blockquote>"),
                    "Open the quotation");
    }

    // ------------------------------------------------------------------ sending

    public record SendResult(boolean emailed, String link) {}

    /**
     * Sends an approved quotation. With emailIt, the client gets an email with a button to their private page
     * (CC as given, replies go to the sender); if that email can't be sent nothing changes. Without emailIt the
     * quotation is only marked as sent, and the client link can be copied and shared another way.
     */
    @Transactional
    public SendResult send(Quotation q, User by, boolean emailIt, String to, List<String> cc, String message, int validDays) {
        if (!"APPROVED".equals(q.getStatus())) throw new IllegalStateException("Only an approved quotation can be sent to the client.");
        if (validDays < 1 || validDays > 365) validDays = DEFAULT_VALID_DAYS;
        QuotationSignoff so = signoff(q.getId());
        if (so.publicToken == null) so.publicToken = newToken();
        LocalDateTime now = LocalDateTime.now();
        so.expiresAt = now.plusDays(validDays);
        String link = email.link("/q/" + so.publicToken);

        if (emailIt) {
            if (to == null || !to.trim().matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) throw new IllegalArgumentException("Enter the client's email address.");
            List<String> ccClean = new ArrayList<>();
            if (cc != null) for (String c : cc) if (c != null && c.trim().matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+") && !c.trim().equalsIgnoreCase(to.trim()) && !ccClean.contains(c.trim())) ccClean.add(c.trim());
            var inq = q.getInquiry();
            var co = settings.company();
            String coName = co.name == null || co.name.isBlank() ? "CBM Engineering & Construction" : co.name;
            String coLine = co.getContactLine();
            String subject = "Quotation " + q.getQuotationNumber() + " · " + nz(inq.getProjectName()) + " | " + coName;
            String body = "<p>Dear " + esc(nz(inq.getFullName())) + ",</p>"
                    + (message == null || message.isBlank() ? "<p>Thank you for considering " + esc(coName) + ". Please find our quotation for your project below.</p>"
                                                            : "<p>" + esc(message.trim()).replace("\n", "<br>") + "</p>")
                    + summaryTable(q)
                    + button(link, "View and sign the quotation")
                    + "<p style='color:#6b7785;font-size:13px'>You can read the full quotation, download or print it, and accept it by signing online or uploading a signed copy. This link is for you only and is valid until "
                    + esc(so.expiresAt.format(DAY)) + ".</p>"
                    + "<p>Regards,<br><b>" + esc(by.getName()) + "</b><br>" + esc(coName)
                    + (coLine.isEmpty() ? "" : "<br><span style='color:#6b7785;font-size:12px'>" + esc(coLine) + "</span>") + "</p>";
            String text = "Dear " + nz(inq.getFullName()) + ",\n\n" + (message == null || message.isBlank() ? "Please find our quotation for your project." : message.trim())
                    + "\n\nQuotation " + q.getQuotationNumber() + " · total " + peso(q.getGrandTotal())
                    + "\nView and sign it here: " + link + "\n(valid until " + so.expiresAt.format(DAY) + ")\n\nRegards,\n" + by.getName() + "\n" + coName + (coLine.isEmpty() ? "" : "\n" + coLine);
            String failed = email.trySendHtml(to.trim(), ccClean, by.getEmail(), subject, wrap(body), text);
            if (failed != null)
                throw new IllegalStateException("The email could not be sent: " + failed + ". The quotation was not marked as sent; you can also copy the client link and send it yourself, then use \"Mark as sent\".");
            so.sentTo = to.trim(); so.sentCc = String.join(", ", ccClean); so.emailed = true;
        } else {
            so.emailed = false; so.sentTo = null; so.sentCc = null;
        }
        so.sentBy = by.getName(); so.sentByEmail = by.getEmail();
        signoffs.save(so);
        q.setStatus("SENT");
        q.setSentAt(now);
        quotations.save(q);
        return new SendResult(emailIt, link);
    }

    /** The client link for an approved or sent quotation, made on first use so it can be copied before sending. */
    @Transactional
    public String clientLink(Quotation q) {
        QuotationSignoff so = signoff(q.getId());
        if (so.publicToken == null) { so.publicToken = newToken(); signoffs.save(so); }
        return email.link("/q/" + so.publicToken);
    }

    // ------------------------------------------------------------------ the client's side

    public record ClientView(Quotation quotation, QuotationSignoff signoff) {}

    /** The quotation behind a client link; counts the visit while it is waiting for the client. */
    @Transactional
    public Optional<ClientView> openForClient(String token, boolean countVisit) {
        if (token == null || token.length() < 20) return Optional.empty();
        return signoffs.findByPublicToken(token).flatMap(so -> quotations.findWithDetailsById(so.quotationId).map(q -> {
            if (countVisit && "SENT".equals(q.getStatus())) {
                LocalDateTime now = LocalDateTime.now();
                if (so.firstViewedAt == null) so.firstViewedAt = now;
                so.lastViewedAt = now; so.viewCount = so.viewCount == null ? 1 : so.viewCount + 1;
                signoffs.save(so);
            }
            return new ClientView(q, so);
        }));
    }

    @Transactional
    public void clientAccept(String token, String name, String title, String signatureData, MultipartFile file, boolean agreed, String ip, String agent) throws java.io.IOException {
        ClientView v = waiting(token);
        if (!agreed) throw new IllegalArgumentException("Tick the box to confirm that you accept the quotation.");
        if (name == null || name.trim().length() < 2) throw new IllegalArgumentException("Type your full name.");
        QuotationSignoff so = v.signoff();
        boolean hasFile = file != null && !file.isEmpty();
        if (hasFile) {
            String type = file.getContentType() == null ? "" : file.getContentType().toLowerCase();
            if (!(type.equals("application/pdf") || type.equals("image/png") || type.equals("image/jpeg") || type.equals("image/webp")))
                throw new IllegalArgumentException("Upload the signed copy as a PDF, JPG or PNG.");
            if (file.getSize() > 8_000_000) throw new IllegalArgumentException("The signed copy must be under 8 MB.");
            so.clientFile = file.getBytes(); so.clientFileType = type;
            so.clientFileName = cut(file.getOriginalFilename() == null ? "signed-copy" : file.getOriginalFilename(), 200);
        }
        if (signatureData != null && !signatureData.isBlank()) so.clientSig = SignatureImages.decodePng(signatureData);
        else if (!hasFile) throw new IllegalArgumentException("Sign in the box, or upload a signed copy of the quotation.");
        so.clientName = cut(name.trim(), 160); so.clientTitle = title == null || title.isBlank() ? null : cut(title.trim(), 160);
        so.clientRespondedAt = LocalDateTime.now(); so.clientIp = cut(ip, 64); so.clientAgent = cut(agent, 300);
        signoffs.save(so);
        Quotation q = v.quotation();
        q.setStatus("ACCEPTED"); q.setDecidedAt(LocalDateTime.now());
        quotations.save(q);
        notifyTeam(q, so, "Accepted by the client: " + q.getQuotationNumber(),
                "<p><b>" + esc(so.clientName) + "</b>" + (so.clientTitle == null ? "" : " (" + esc(so.clientTitle) + ")") + " accepted quotation "
                        + esc(q.getQuotationNumber()) + " and " + (so.clientSig != null ? "signed it online" : "uploaded a signed copy") + ".</p>" + summaryTable(q)
                        + "<p>Next step: create the job order.</p>");
    }

    @Transactional
    public void clientDecline(String token, String name, String reason, String ip, String agent) {
        ClientView v = waiting(token);
        QuotationSignoff so = v.signoff();
        so.clientName = name == null || name.isBlank() ? null : cut(name.trim(), 160);
        so.declineReason = reason == null || reason.isBlank() ? null : cut(reason.trim(), 1000);
        so.clientRespondedAt = LocalDateTime.now(); so.clientIp = cut(ip, 64); so.clientAgent = cut(agent, 300);
        signoffs.save(so);
        Quotation q = v.quotation();
        q.setStatus("REJECTED"); q.setDecidedAt(LocalDateTime.now());
        quotations.save(q);
        notifyTeam(q, so, "Declined by the client: " + q.getQuotationNumber(),
                "<p>" + (so.clientName == null ? "The client" : "<b>" + esc(so.clientName) + "</b>") + " declined quotation " + esc(q.getQuotationNumber()) + ".</p>"
                        + (so.declineReason == null ? "<p>No reason was given.</p>" : "<blockquote style='margin:12px 0;padding:10px 14px;border-left:3px solid #c8102e;background:#f7f8fa'>" + esc(so.declineReason).replace("\n", "<br>") + "</blockquote>"));
    }

    private ClientView waiting(String token) {
        ClientView v = openForClient(token, false).orElseThrow(() -> new IllegalStateException("This link is not valid."));
        if (!"SENT".equals(v.quotation().getStatus())) throw new IllegalStateException("This quotation has already been answered.");
        if (v.signoff().isExpired()) throw new IllegalStateException("This quotation has expired. Please contact us for an updated quotation.");
        return v;
    }

    // ------------------------------------------------------------------ mail helpers

    private void notifySubmitter(Quotation q, QuotationSignoff so, User by, String subject, String html, String buttonText) {
        if (so.submittedById == null || so.submittedById.equals(by.getId())) return;
        users.findById(so.submittedById).ifPresent(u -> {
            String link = email.link("/Quotations/" + q.getId());
            email.sendHtml(u.getEmail(), null, by.getEmail(), subject, wrap(html + button(link, buttonText)), subject + "\n\n" + link);
        });
    }

    /** Tells the person who sent the quotation, with the approvers in copy. */
    private void notifyTeam(Quotation q, QuotationSignoff so, String subject, String html) {
        String link = email.link("/Quotations/" + q.getId());
        List<String> cc = new ArrayList<>();
        for (User a : approvers()) if (a.getEmail() != null && !a.getEmail().equalsIgnoreCase(so.sentByEmail)) cc.add(a.getEmail());
        String to = so.sentByEmail;
        if (to == null || to.isBlank()) { if (cc.isEmpty()) return; to = cc.remove(0); }
        email.sendHtml(to, cc, null, subject, wrap(html + button(link, "Open the quotation")), subject + "\n\n" + link);
    }

    private static String summaryTable(Quotation q) {
        var inq = q.getInquiry();
        return "<table cellpadding='0' cellspacing='0' style='width:100%;max-width:520px;margin:16px 0;border:1px solid #e3e8ee;border-radius:8px;font-size:14px'>"
                + row("Quotation", q.getQuotationNumber()) + row("Project", nz(inq.getProjectName()))
                + (inq.getProjectLocation() == null ? "" : row("Location", inq.getProjectLocation()))
                + row("Client", nz(inq.getFullName()) + (inq.getCompany() == null || inq.getCompany().isBlank() ? "" : " · " + inq.getCompany()))
                + "<tr><td style='padding:10px 14px;color:#6b7785'>Total</td><td style='padding:10px 14px;text-align:right;font-weight:700;font-size:16px'>" + esc(peso(q.getGrandTotal())) + "</td></tr></table>";
    }
    private static String row(String k, String v) {
        return "<tr><td style='padding:10px 14px;color:#6b7785;border-bottom:1px solid #e3e8ee'>" + esc(k) + "</td><td style='padding:10px 14px;text-align:right;border-bottom:1px solid #e3e8ee'>" + esc(v) + "</td></tr>";
    }
    private static String button(String href, String label) {
        return "<p style='margin:22px 0'><a href='" + esc(href) + "' style='display:inline-block;padding:12px 22px;background:#c8102e;color:#ffffff;text-decoration:none;border-radius:6px;font-weight:600'>" + esc(label) + "</a></p>"
                + "<p style='color:#6b7785;font-size:12px;word-break:break-all'>Or open: " + esc(href) + "</p>";
    }
    private static String wrap(String inner) {
        return "<div style='font-family:Segoe UI,Arial,sans-serif;color:#0b1524;line-height:1.55;max-width:600px'>"
                + "<div style='padding:14px 0;border-bottom:3px solid #c8102e;margin-bottom:18px;font-weight:700;letter-spacing:.04em'>CBM ENGINEERING &amp; CONSTRUCTION</div>"
                + inner + "</div>";
    }
    private static String peso(BigDecimal v) { return "₱" + String.format("%,.2f", v); }
    private static String esc(String s) { return s == null ? "" : htmlEscape(s); }
    private static String nz(String s) { return s == null ? "" : s; }
    private static String first(String name) { return name == null ? "" : name.trim().split("\\s+")[0]; }
    private static String cut(String s, int n) { return s == null ? null : (s.length() <= n ? s : s.substring(0, n)); }
    private static String newToken() {
        byte[] b = new byte[24]; RANDOM.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }
}
