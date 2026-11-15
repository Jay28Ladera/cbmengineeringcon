package com.asmsi.admin_system.service;

import com.resend.Resend;
import com.resend.services.emails.model.CreateEmailOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    @Value("${resend.api.key}")
    private String resendApiKey;

    @Value("${resend.from.email}")
    private String fromEmail;

    /** Public address of the app, e.g. https://portal.cbm.com. Blank = use the address of the current request. */
    @Value("${app.base-url:}")
    private String baseUrl;

    public void sendApprovalEmail(String toEmail, String fullName) {
        String subject = "Your CBM portal account is approved";
        String body =
                "Dear " + safe(fullName) + ",\n\n" +
                "Your account on the CBM Engineering & Construction management portal has been approved.\n" +
                "You can now sign in at " + appUrl("/login") + "\n\n" +
                "Regards,\nCBM Engineering & Construction";
        sendPlainText(toEmail, subject, body);
    }

    public boolean sendPasswordResetEmail(String toEmail, String token) {
        String resetLink = appUrl("/reset-password?token=" + token);
        String subject = "Reset your CBM portal password";
        String body =
                "Hello,\n\n" +
                "We received a request to reset the password for your CBM portal account.\n" +
                "Open the link below to choose a new password (valid for 15 minutes):\n\n" +
                resetLink + "\n\n" +
                "If you did not request this, you can ignore this email.\n\n" +
                "Regards,\nCBM Engineering & Construction";
        return sendPlainText(toEmail, subject, body);
    }

    /**
     * Sends a plain-text email. Returns false instead of throwing, so a mail outage never
     * blocks the action that triggered it (approving a user used to fail completely).
     */
    public boolean sendPlainText(String toEmail, String subject, String body) {
        if (toEmail == null || toEmail.isBlank()) return false;
        if (resendApiKey == null || resendApiKey.isBlank()) {
            log.warn("Email not sent to {}: resend.api.key is not set", toEmail);
            return false;
        }
        try {
            CreateEmailOptions params = CreateEmailOptions.builder()
                    .from(fromEmail)
                    .to(toEmail.trim())
                    .subject(subject)
                    .text(body)
                    .build();
            new Resend(resendApiKey).emails().send(params);
            log.info("Email sent to {}: {}", toEmail, subject);
            return true;
        } catch (Exception e) {
            log.error("Email to {} failed: {}", toEmail, e.getMessage());
            return false;
        }
    }

    /** Optional "From" for client-facing mail, e.g. "CBM Engineering &lt;quotes@cbm.ph&gt;". Blank = resend.from.email. */
    @Value("${quote.mail.from:}")
    private String quoteFrom;

    /**
     * Sends an HTML email with a plain-text copy, optional CC and Reply-To.
     * Returns false instead of throwing. Without an API key nothing is sent, and the subject and
     * links are written to the log so the flow can still be tried on a developer machine.
     */
    public boolean sendHtml(String to, java.util.List<String> cc, String replyTo, String subject, String html, String text) {
        return trySendHtml(to, cc, replyTo, subject, html, text) == null;
    }

    /** Same as {@link #sendHtml}, but returns null when sent, or a short reason it was not sent. */
    public String trySendHtml(String to, java.util.List<String> cc, String replyTo, String subject, String html, String text) {
        if (to == null || to.isBlank()) return "there is no recipient address";
        if (resendApiKey == null || resendApiKey.isBlank()) {
            log.warn("Email NOT sent (resend.api.key is not set) to {} cc {}: {}\n{}", to, cc, subject, text);
            return "email is not set up here (RESEND_API_KEY is empty)";
        }
        String from = quoteFrom == null || quoteFrom.isBlank() ? fromEmail : quoteFrom;
        try {
            var b = CreateEmailOptions.builder()
                    .from(from)
                    .to(to.trim())
                    .subject(subject)
                    .html(html)
                    .text(text);
            if (cc != null && !cc.isEmpty()) b.cc(cc);
            if (replyTo != null && !replyTo.isBlank()) b.replyTo(replyTo.trim());
            new Resend(resendApiKey).emails().send(b.build());
            log.info("Email sent from {} to {} (cc {}): {}", from, to, cc, subject);
            return null;
        } catch (Exception e) {
            log.error("Email from {} to {} (cc {}) failed: {}", from, to, cc, e.getMessage());
            String why = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage().replaceAll("\\s+", " ").trim();
            return "Resend said: " + (why.length() > 300 ? why.substring(0, 300) + "…" : why) + " (sender " + from + ")";
        }
    }

    public String link(String path) { return appUrl(path); }

    private String appUrl(String path) {
        String root = (baseUrl == null || baseUrl.isBlank())
                ? ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString()
                : baseUrl.replaceAll("/+$", "");
        return root + path;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
