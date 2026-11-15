package com.asmsi.admin_system.inventory;

import com.asmsi.admin_system.entity.OrganizationNode;
import com.asmsi.admin_system.entity.User;
import com.asmsi.admin_system.entity.UserSignature;
import com.asmsi.admin_system.repository.OrganizationNodeRepository;
import com.asmsi.admin_system.repository.UserRepository;
import com.asmsi.admin_system.repository.UserSignatureRepository;
import com.asmsi.admin_system.service.AccessService;
import com.asmsi.admin_system.service.EmailService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

import static org.springframework.web.util.HtmlUtils.htmlEscape;

/**
 * Sign-off of a canvass sheet (form CBM-CD-006), done in the portal instead of routing the sheet by email:
 *
 *   DRAFT ──submit──▶ FOR_APPROVAL ──Approved by (QS)──▶ ──Noted by (Manager)──▶ APPROVED: purchase orders can be made
 *                                                                                  └──Processed by (Finance), when paying
 *   Return for changes (before "Noted") sends it back to DRAFT with a note and clears the signatures.
 *
 * Who signs each step is set in Users (Canvass: Approved by / Noted by / Processed by). While nobody is set for a
 * step, administrators sign it. Each signature is copied onto the canvass when signed.
 */
@Service
public class CanvassApprovalService {

    public static final String APPROVE = "APPROVE", NOTE = "NOTE", PROCESS = "PROCESS";
    public static final List<String> STEPS = List.of(APPROVE, NOTE, PROCESS);

    public static String label(String step) {
        return switch (step) { case APPROVE -> "Approved by"; case NOTE -> "Noted by"; case PROCESS -> "Processed by"; default -> step; };
    }
    public static String roleHint(String step) {
        return switch (step) { case APPROVE -> "Office Engineer/QS"; case NOTE -> "Project Manager / Operations Manager"; case PROCESS -> "Finance"; default -> ""; };
    }

    private final CanvassRepository canvasses;
    private final CanvassSignoffRepository signoffs;
    private final CanvassSignerRepository signers;
    private final PurchasingService purchasing;
    private final UserRepository users;
    private final UserSignatureRepository signatures;
    private final OrganizationNodeRepository orgNodes;
    private final EmailService email;

    public CanvassApprovalService(CanvassRepository canvasses, CanvassSignoffRepository signoffs, CanvassSignerRepository signers,
                                  PurchasingService purchasing, UserRepository users, UserSignatureRepository signatures,
                                  OrganizationNodeRepository orgNodes, EmailService email) {
        this.canvasses = canvasses; this.signoffs = signoffs; this.signers = signers; this.purchasing = purchasing;
        this.users = users; this.signatures = signatures; this.orgNodes = orgNodes; this.email = email;
    }

    // ------------------------------------------------------------------ who signs

    /** People who sign a step: those set in Users, or every administrator while nobody is set for that step. */
    @Transactional(readOnly = true)
    public List<User> signersOf(String step) {
        Set<Long> ids = new HashSet<>();
        signers.findByStep(step).forEach(s -> ids.add(s.userId));
        List<User> out = new ArrayList<>();
        for (User u : users.findByIsApprovedTrueOrderByNameAsc())
            if (ids.isEmpty() ? AccessService.isAdminRole(u.getRole()) : ids.contains(u.getId())) out.add(u);
        return out;
    }

    public boolean canSign(User u, String step) {
        return u != null && signersOf(step).stream().anyMatch(x -> x.getId().equals(u.getId()));
    }

    /** Steps this user may sign. */
    public Set<String> stepsFor(User u) {
        Set<String> out = new LinkedHashSet<>();
        for (String s : STEPS) if (canSign(u, s)) out.add(s);
        return out;
    }

    public CanvassSignoff signoff(Long canvassId) {
        return signoffs.findById(canvassId).orElseGet(() -> new CanvassSignoff(canvassId));
    }

    /** The step waiting for a signature, or null: APPROVE / NOTE while for approval, PROCESS once approved or ordered. */
    public String pendingStep(Canvass c, CanvassSignoff so) {
        if ("FOR_APPROVAL".equals(c.status)) return so.approvedAt == null ? APPROVE : NOTE;
        if (("APPROVED".equals(c.status) || "ORDERED".equals(c.status)) && so.notedAt != null && so.processedAt == null) return PROCESS;
        return null;
    }

    private String titleOf(User u, String step) {
        return orgNodes.findByAccountUserId(u.getId()).stream().map(OrganizationNode::getTitle)
                .filter(t -> t != null && !t.isBlank()).findFirst().orElse(roleHint(step));
    }

    // ------------------------------------------------------------------ steps

    @Transactional
    public void submit(Canvass c, User by) {
        purchasing.lockChoices(c);       // throws if anything is missing; fixes the chosen suppliers
        CanvassSignoff so = signoff(c.id);
        so.clearSignatures();
        so.submittedBy = by.getName(); so.submittedById = by.getId(); so.submittedAt = LocalDateTime.now();
        so.returnNote = null; so.returnedBy = null; so.returnedAt = null;
        signoffs.save(so);
        c.status = "FOR_APPROVAL"; c.approvedBy = null; c.approvedAt = null;
        canvasses.save(c);
        notifySigners(c, APPROVE, by, by.getName() + " submitted canvass " + c.number + " for your approval.");
    }

    /** Signs the step that is waiting. Returns the step signed. */
    @Transactional
    public String sign(Canvass c, User by) {
        CanvassSignoff so = signoff(c.id);
        String step = pendingStep(c, so);
        if (step == null) throw new IllegalStateException("Canvass " + c.number + " is not waiting for a signature.");
        if (!canSign(by, step))
            throw new IllegalStateException("You can't sign \"" + label(step) + "\" (" + roleHint(step) + "). An administrator can set this for you in Users.");
        UserSignature sig = signatures.findById(by.getId())
                .orElseThrow(() -> new IllegalStateException("Save your signature on My profile first. It is printed on the canvass sheet."));
        LocalDateTime now = LocalDateTime.now();
        String title = titleOf(by, step);
        switch (step) {
            case APPROVE -> { so.approvedById = by.getId(); so.approvedBy = by.getName(); so.approvedTitle = title; so.approvedAt = now; so.approvedSig = sig.image; so.approvedSigType = sig.contentType; }
            case NOTE -> { so.notedById = by.getId(); so.notedBy = by.getName(); so.notedTitle = title; so.notedAt = now; so.notedSig = sig.image; so.notedSigType = sig.contentType; }
            default -> { so.processedById = by.getId(); so.processedBy = by.getName(); so.processedTitle = title; so.processedAt = now; so.processedSig = sig.image; so.processedSigType = sig.contentType; }
        }
        signoffs.save(so);
        if (APPROVE.equals(step)) {
            notifySigners(c, NOTE, by, by.getName() + " approved canvass " + c.number + ". It is waiting to be noted.");
        } else if (NOTE.equals(step)) {
            purchasing.markApproved(c, by.getName());
            notifySubmitter(c, so, by, "Canvass " + c.number + " is approved",
                    "<p>Canvass " + esc(c.number) + " was approved by " + esc(so.approvedBy) + " and noted by " + esc(by.getName()) + ". You can now create the purchase orders.</p>");
            notifySigners(c, PROCESS, by, "Canvass " + c.number + " is approved and noted. Sign \"Processed by\" when you process the payment.");
        }
        return step;
    }

    /** Back to draft with a note (before it is noted). Clears the signatures. */
    @Transactional
    public void returnToDraft(Canvass c, User by, String note) {
        if (!"FOR_APPROVAL".equals(c.status)) throw new IllegalStateException("Only a canvass waiting for approval can be returned. An approved canvass without purchase orders can be reopened.");
        CanvassSignoff so = signoff(c.id);
        so.clearSignatures();
        so.returnNote = note == null || note.isBlank() ? null : note.trim();
        so.returnedBy = by.getName(); so.returnedAt = LocalDateTime.now();
        signoffs.save(so);
        c.status = "DRAFT";
        canvasses.save(c);
        notifySubmitter(c, so, by, "Returned for changes: canvass " + c.number,
                "<p>" + esc(by.getName()) + " returned canvass " + esc(c.number) + " for changes.</p>"
                        + (so.returnNote == null ? "" : "<blockquote style='margin:12px 0;padding:10px 14px;border-left:3px solid #c8102e;background:#f7f8fa'>" + esc(so.returnNote).replace("\n", "<br>") + "</blockquote>"));
    }

    /** Reopening an approved canvass (no purchase orders yet) clears its signatures too. */
    @Transactional
    public void reopen(Canvass c) {
        purchasing.reopen(c);
        signoffs.findById(c.id).ifPresent(so -> { so.clearSignatures(); signoffs.save(so); });
    }

    // ------------------------------------------------------------------ mail

    private void notifySigners(Canvass c, String step, User by, String what) {
        String link = email.link("/Approvals");
        for (User u : signersOf(step)) {
            if (u.getId().equals(by.getId()) || u.getEmail() == null) continue;
            String subject = label(step).replace(" by", "") + ": canvass " + c.number + " · " + c.title;
            String html = "<div style='font-family:Segoe UI,Arial,sans-serif;color:#0b1524;line-height:1.55;max-width:600px'>"
                    + "<div style='padding:14px 0;border-bottom:3px solid #c8102e;margin-bottom:18px;font-weight:700;letter-spacing:.04em'>CBM ENGINEERING &amp; CONSTRUCTION</div>"
                    + "<p>Hi " + esc(u.getName() == null ? "" : u.getName().trim().split("\\s+")[0]) + ",</p><p>" + esc(what) + "</p>"
                    + "<p><b>" + esc(c.number) + "</b> · " + esc(c.title) + (c.mrNo == null ? "" : " · MR # " + esc(c.mrNo)) + "</p>"
                    + "<p style='margin:22px 0'><a href='" + esc(link) + "' style='display:inline-block;padding:12px 22px;background:#c8102e;color:#fff;text-decoration:none;border-radius:6px;font-weight:600'>Open Approvals</a></p></div>";
            email.sendHtml(u.getEmail(), null, by.getEmail(), subject, html, what + "\n\n" + link);
        }
    }

    private void notifySubmitter(Canvass c, CanvassSignoff so, User by, String subject, String body) {
        if (so.submittedById == null || so.submittedById.equals(by.getId())) return;
        users.findById(so.submittedById).ifPresent(u -> {
            String link = email.link("/Purchasing/Canvasses/" + c.id);
            String html = "<div style='font-family:Segoe UI,Arial,sans-serif;color:#0b1524;line-height:1.55;max-width:600px'>"
                    + "<div style='padding:14px 0;border-bottom:3px solid #c8102e;margin-bottom:18px;font-weight:700;letter-spacing:.04em'>CBM ENGINEERING &amp; CONSTRUCTION</div>"
                    + body + "<p style='margin:22px 0'><a href='" + esc(link) + "' style='display:inline-block;padding:12px 22px;background:#c8102e;color:#fff;text-decoration:none;border-radius:6px;font-weight:600'>Open the canvass</a></p></div>";
            email.sendHtml(u.getEmail(), null, by.getEmail(), subject, html, subject + "\n\n" + link);
        });
    }

    private static String esc(String s) { return s == null ? "" : htmlEscape(s); }
}
