package com.asmsi.admin_system.admin;

import com.asmsi.admin_system.entity.User;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.InteractiveAuthenticationSuccessEvent;
import org.springframework.security.authentication.event.LogoutSuccessEvent;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.sql.Timestamp;
import java.time.LocalDateTime;

/** Sign-ins, failed sign-ins and sign-outs on the audit trail. */
@Component
public class AuthAuditListener {

    private static final Logger log = LoggerFactory.getLogger(AuthAuditListener.class);
    private final JdbcTemplate jdbc;

    public AuthAuditListener(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @EventListener
    public void success(InteractiveAuthenticationSuccessEvent e) { write("LOGIN", e.getAuthentication(), "Signed in"); }

    @EventListener
    public void failure(AbstractAuthenticationFailureEvent e) {
        String why = e.getException() == null ? "" : e.getException().getClass().getSimpleName()
                .replace("BadCredentialsException", "wrong password or username")
                .replace("DisabledException", "account not approved")
                .replace("LockedException", "account locked");
        write("LOGIN_FAILED", e.getAuthentication(), "Sign-in failed" + (why.isEmpty() ? "" : ": " + why));
    }

    @EventListener
    public void logout(LogoutSuccessEvent e) { write("LOGOUT", e.getAuthentication(), "Signed out"); }

    private void write(String action, Authentication a, String label) {
        try {
            String username = a == null ? null : a.getName();
            String name = a != null && a.getPrincipal() instanceof User u ? u.getName() : username;
            String ip = null;
            if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes sra) {
                HttpServletRequest r = sra.getRequest();
                ip = r.getRemoteAddr();
            }
            jdbc.update("insert into sys_audit (at, username, user_name, action, module, entity, label, ip) values (?,?,?,?,?,?,?,?)",
                    Timestamp.valueOf(LocalDateTime.now()), AuditListener.cut(username, 120), AuditListener.cut(name, 160), action,
                    "Administration", "User", label, AuditListener.cut(ip, 64));
        } catch (RuntimeException ex) {
            log.warn("Sign-in not written to the audit trail: {}", ex.getMessage());
        }
    }
}
