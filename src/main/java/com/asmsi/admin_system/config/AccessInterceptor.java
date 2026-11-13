package com.asmsi.admin_system.config;

import com.asmsi.admin_system.entity.AccessModule;
import com.asmsi.admin_system.entity.User;
import com.asmsi.admin_system.service.AccessService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Blocks pages a person's department (or custom access) doesn't include, even if they type the
 * URL directly. Hiding the menu items alone isn't enough. Shows the "No access" page (403).
 */
@Component
public class AccessInterceptor implements HandlerInterceptor {

    private final AccessService accessService;

    public AccessInterceptor(AccessService accessService) {
        this.accessService = accessService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        AccessModule module = AccessModule.forPath(path);
        if (module == null) return true;

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof User user)) return true; // Spring Security handles sign-in
        // Public quote form posts here without signing in; it's matched by /Clients/public.
        if (path.equalsIgnoreCase("/Clients/public")) return true;

        if (accessService.current(user, request.getSession(false)).can(module)) return true;
        response.sendError(HttpServletResponse.SC_FORBIDDEN, "Your access does not include " + module.getLabel() + ".");
        return false;
    }
}
