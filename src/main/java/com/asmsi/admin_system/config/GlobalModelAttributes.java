package com.asmsi.admin_system.config;

import com.asmsi.admin_system.entity.User;
import com.asmsi.admin_system.service.AccessService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Header and sidebar details for every page.
 *
 * The logged-in {@link User} is already held in the security context, so this reads it from
 * there instead of querying the database. (Previously each page load ran three separate user
 * queries before doing any real work, which was slow against the remote database.)
 * ProfileController refreshes the security context after a profile edit.
 */
@ControllerAdvice
public class GlobalModelAttributes {

    private final AccessService accessService;

    public GlobalModelAttributes(AccessService accessService) {
        this.accessService = accessService;
    }

    @ModelAttribute
    public void addUserAttributes(Model model, HttpServletRequest request) {
        model.addAttribute("allModules", com.asmsi.admin_system.entity.AccessModule.values());
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof User user)) {
            model.addAttribute("role", "user");
            model.addAttribute("access", java.util.Set.of());
            return;
        }
        String role = blank(user.getRole()) ? "user" : user.getRole();
        String displayName = blank(user.getName()) ? user.getUsername() : user.getName();

        if (!model.containsAttribute("username")) model.addAttribute("username", user.getUsername());
        model.addAttribute("displayName", displayName);
        model.addAttribute("profileImageUrl", blank(user.getProfileImageUrl()) ? null : user.getProfileImageUrl());
        model.addAttribute("role", role);
        model.addAttribute("isAdmin", "admin".equalsIgnoreCase(role) || "ceo".equalsIgnoreCase(role));
        model.addAttribute("userInitials", initials(displayName));
        // Module keys this person can open, e.g. ${access.contains('INQUIRY')} in templates (cached per session).
        model.addAttribute("access", accessService.current(user, request.getSession(false)).keys());
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static String initials(String name) {
        StringBuilder sb = new StringBuilder();
        for (String part : name.trim().split("\\s+")) {
            if (!part.isEmpty() && sb.length() < 2) sb.append(Character.toUpperCase(part.charAt(0)));
        }
        return sb.length() == 0 ? "?" : sb.toString();
    }
}
