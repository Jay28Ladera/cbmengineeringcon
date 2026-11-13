package com.asmsi.admin_system.service;

import com.asmsi.admin_system.entity.OrganizationNode;
import com.asmsi.admin_system.entity.StaffActivation;
import com.asmsi.admin_system.entity.User;
import com.asmsi.admin_system.entity.UserAccess;
import com.asmsi.admin_system.repository.OrganizationNodeRepository;
import com.asmsi.admin_system.repository.StaffActivationRepository;
import com.asmsi.admin_system.repository.UserAccessRepository;
import com.asmsi.admin_system.repository.UserRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.Base64;
import java.util.Optional;

/**
 * Login accounts for people on the Organizational Chart.
 *
 * An admin clicks "Create login account" on someone's box: the account is created (not yet
 * usable), linked to the box, and a one-time activation link is emailed. The person opens the
 * link, sets a password, and can sign in. Their access follows their department.
 * If the email fails, the admin gets the link to send another way.
 */
@Service
public class StaffAccountService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final OrganizationNodeRepository nodes;
    private final UserRepository users;
    private final UserAccessRepository userAccess;
    private final StaffActivationRepository activations;
    private final EmailService email;
    private final BCryptPasswordEncoder encoder;
    private final AccessService accessService;

    public StaffAccountService(OrganizationNodeRepository nodes, UserRepository users, UserAccessRepository userAccess,
                               StaffActivationRepository activations, EmailService email,
                               BCryptPasswordEncoder encoder, AccessService accessService) {
        this.nodes = nodes;
        this.users = users;
        this.userAccess = userAccess;
        this.activations = activations;
        this.email = email;
        this.encoder = encoder;
        this.accessService = accessService;
    }

    /** Message the admin can read. */
    public static class InviteException extends Exception {
        public InviteException(String message) { super(message); }
    }

    public record InviteResult(String name, String email, String username, String link, boolean emailSent,
                               boolean resent, boolean linkedExisting) {}

    public record Status(String state, String username, Long userId) {
        /** "none" (no account), "invited" (waiting for activation), "active", "disabled". */
        public static final Status NONE = new Status("none", null, null);
    }

    /** Account state for a box on the chart. */
    public Status statusOf(OrganizationNode node, User account) {
        if (node.getAccountUserId() == null || account == null) return Status.NONE;
        UserAccess ua = userAccess.findById(account.getId()).orElse(null);
        if (ua != null && ua.isInvitePending()) return new Status("invited", account.getUsername(), account.getId());
        return new Status(account.isApproved() ? "active" : "disabled", account.getUsername(), account.getId());
    }

    public boolean isInvitePending(Long userId) {
        return userId != null && userAccess.findById(userId).map(UserAccess::isInvitePending).orElse(false);
    }

    /**
     * Creates the account for a box (or re-sends the invitation if one is pending) and returns
     * the activation link. {@code appUrl} is the site address, e.g. https://cbm.example.com.
     */
    @Transactional
    public InviteResult invite(Long nodeId, Long adminId, String appUrl) throws InviteException {
        OrganizationNode node = nodes.findById(nodeId).orElseThrow(() -> new InviteException("That position no longer exists."));
        if (node.isResigned()) throw new InviteException("This person is marked as resigned.");
        if (blank(node.getPersonName())) throw new InviteException("Add the person's name to the box first (vacant positions can't have an account).");
        if (blank(node.getEmailAddress())) throw new InviteException("Add " + node.getPersonName() + "'s email address first; the activation link is sent there.");
        String mail = node.getEmailAddress().trim();

        boolean resent = false, linkedExisting = false;
        User user = node.getAccountUserId() == null ? null : users.findById(node.getAccountUserId()).orElse(null);

        if (user != null) {
            if (!isInvitePending(user.getId())) {
                throw new InviteException(node.getPersonName() + " already has an active account (@" + user.getUsername() + ").");
            }
            resent = true;
        } else {
            Optional<User> sameEmail = users.findByEmail(mail);
            if (sameEmail.isPresent()) {
                // Someone already signed up with this email: link that account instead of making a duplicate.
                User existing = sameEmail.get();
                boolean usedElsewhere = nodes.findByAccountUserId(existing.getId()).stream()
                        .anyMatch(n -> !n.getId().equals(node.getId()) && !n.isResigned());
                if (usedElsewhere) throw new InviteException("The email " + mail + " belongs to an account that is already linked to another box.");
                node.setAccountUserId(existing.getId());
                nodes.save(node);
                accessService.changed();
                if (existing.isApproved()) {
                    return new InviteResult(existing.getName(), mail, existing.getUsername(), null, false, false, true);
                }
                user = existing;
                linkedExisting = true;
            } else {
                user = new User();
                user.setName(node.getPersonName().trim());
                user.setEmail(mail);
                user.setUsername(uniqueUsername(node.getPersonName(), mail));
                user.setPassword(encoder.encode(token())); // unusable until they set their own
                user.setRole("user");
                user.setApproved(false);
                user.setAdminId(adminId);
                user = users.save(user);
                node.setAccountUserId(user.getId());
                nodes.save(node);
            }
            UserAccess ua = userAccess.findById(user.getId()).orElse(new UserAccess(user.getId()));
            ua.setInvitePending(true);
            userAccess.save(ua);
            accessService.changed();
        }

        activations.deleteByUserId(user.getId());          // older links stop working
        String token = token();
        activations.save(new StaffActivation(token, user.getId()));
        String link = appUrl.replaceAll("/+$", "") + "/activate?token=" + token;

        boolean sent = email.sendPlainText(mail, "Activate your CBM portal account",
                "Hello " + user.getName() + ",\n\n" +
                "An account has been created for you on the CBM Engineering & Construction portal.\n" +
                "Your username is: " + user.getUsername() + "\n\n" +
                "Open this link to choose your password and activate the account (it works once):\n" +
                link + "\n\n" +
                "If you weren't expecting this, please tell the CBM office.\n\n" +
                "CBM Engineering & Construction");

        return new InviteResult(user.getName(), mail, user.getUsername(), link, sent, resent, linkedExisting);
    }

    // ------------------------------------------------------------------ activation

    public record Pending(StaffActivation activation, User user) {}

    public Optional<Pending> findPending(String token) {
        if (blank(token)) return Optional.empty();
        return activations.findByToken(token.trim())
                .flatMap(a -> users.findById(a.getUserId()).map(u -> new Pending(a, u)));
    }

    @Transactional
    public User activate(String token, String username, String password) throws InviteException {
        Pending p = findPending(token).orElseThrow(() -> new InviteException("This activation link is invalid or was already used."));
        User user = p.user();
        String wanted = username == null ? "" : username.trim();
        if (!wanted.matches("[A-Za-z0-9._-]{3,40}")) throw new InviteException("Usernames use 3–40 letters, numbers, dots, dashes or underscores.");
        if (!wanted.equalsIgnoreCase(user.getUsername()) && users.findByUsernameIgnoreCase(wanted).isPresent())
            throw new InviteException("The username " + wanted + " is already taken.");
        if (password == null || password.length() < 8) throw new InviteException("Use at least 8 characters for the password.");

        user.setUsername(wanted);
        user.setPassword(encoder.encode(password));
        user.setApproved(true);
        user.setDateApproved(LocalDate.now());
        users.save(user);
        userAccess.findById(user.getId()).ifPresent(ua -> { ua.setInvitePending(false); userAccess.save(ua); });
        activations.deleteByUserId(user.getId());
        accessService.changed();
        return user;
    }

    // ------------------------------------------------------------------ helpers

    /** e.g. "Engr. Liza M. Abad" → "liza.abad", then "liza.abad2" if taken. */
    String uniqueUsername(String fullName, String mail) {
        String clean = Normalizer.normalize(fullName == null ? "" : fullName, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        String[] parts = clean.toLowerCase().replaceAll("[^a-z\\s.-]", " ").trim().split("[\\s]+");
        java.util.List<String> words = new java.util.ArrayList<>();
        for (String w : parts) {
            String x = w.replace(".", "");
            if (x.isEmpty() || x.length() == 1 || x.matches("engr|arch|atty|dr|mr|mrs|ms|jr|sr|ii|iii|iv")) continue;
            words.add(x.replaceAll("[^a-z]", ""));
        }
        String base = words.isEmpty() ? mail.split("@")[0].toLowerCase().replaceAll("[^a-z0-9._-]", "")
                : words.size() == 1 ? words.get(0) : words.get(0) + "." + words.get(words.size() - 1);
        if (base.length() < 3) base = (base + "user").substring(0, Math.max(3, base.length()));
        if (base.length() > 36) base = base.substring(0, 36);
        String candidate = base;
        for (int i = 2; users.findByUsernameIgnoreCase(candidate).isPresent(); i++) candidate = base + i;
        return candidate;
    }

    private static String token() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static boolean blank(String s) { return s == null || s.isBlank(); }
}
