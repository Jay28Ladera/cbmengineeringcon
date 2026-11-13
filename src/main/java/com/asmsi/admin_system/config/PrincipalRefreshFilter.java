package com.asmsi.admin_system.config;

import com.asmsi.admin_system.entity.User;
import com.asmsi.admin_system.repository.UserRepository;
import com.asmsi.admin_system.service.AccessService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * After an admin changes accounts or access (made admin, access changed, disabled, deleted),
 * signed-in people get their account reloaded on their next request instead of at their next
 * sign-in. Disabled or deleted accounts are signed out. Costs one query only after a change.
 */
public class PrincipalRefreshFilter extends OncePerRequestFilter {

    private static final String KEY = "cbm.principalVersion";

    private final UserRepository users;
    private final AccessService accessService;

    public PrincipalRefreshFilter(UserRepository users, AccessService accessService) {
        this.users = users;
        this.accessService = accessService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String p = request.getRequestURI();
        return p.startsWith("/css/") || p.startsWith("/js/") || p.startsWith("/vendor/") || p.startsWith("/images/")
                || p.startsWith("/webjars/") || p.startsWith("/uploads/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        HttpSession session = request.getSession(false);
        if (session != null && auth != null && auth.getPrincipal() instanceof User current) {
            long now = accessService.version();
            Object seen = session.getAttribute(KEY);
            if (!(seen instanceof Long v) || v != now) {
                User fresh = users.findById(current.getId()).orElse(null);
                if (fresh == null || !fresh.isEnabled()) {
                    session.invalidate();
                    SecurityContextHolder.clearContext();
                    response.sendRedirect(request.getContextPath() + "/login?disabled");
                    return;
                }
                UsernamePasswordAuthenticationToken refreshed =
                        new UsernamePasswordAuthenticationToken(fresh, null, fresh.getAuthorities());
                refreshed.setDetails(auth.getDetails());
                SecurityContext ctx = SecurityContextHolder.createEmptyContext();
                ctx.setAuthentication(refreshed);
                SecurityContextHolder.setContext(ctx);
                session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, ctx);
                session.setAttribute(KEY, now);
            }
        }
        chain.doFilter(request, response);
    }
}
