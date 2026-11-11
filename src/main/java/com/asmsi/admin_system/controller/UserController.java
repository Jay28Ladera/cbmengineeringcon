package com.asmsi.admin_system.controller;

import com.asmsi.admin_system.entity.User;
import com.asmsi.admin_system.repository.UserRepository;
import com.asmsi.admin_system.service.AuthUtil;
import com.asmsi.admin_system.service.UserService;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Set;

/** User approval and account administration. Admin-only (see SecurityConfig). */
@Controller
@RequestMapping("/users")
public class UserController {

    private static final Set<String> ROLES = Set.of("user", "manager", "admin");

    private final UserService userService;
    private final UserRepository userRepository;
    private final AuthUtil authUtil;

    public UserController(UserService userService, UserRepository userRepository, AuthUtil authUtil) {
        this.userService = userService;
        this.userRepository = userRepository;
        this.authUtil = authUtil;
    }

    @GetMapping
    public String viewUsers(Model model) {
        model.addAttribute("activePage", "users");
        model.addAttribute("currentUserId", authUtil.getCurrentUserId());
        model.addAttribute("approvedUsers", userService.getApprovedUsers());
        model.addAttribute("unapprovedUsers", userService.getUnapprovedUsers());
        return "user-management";
    }

    @PostMapping("/approve")
    public String approveUser(@RequestParam("id") Long id, RedirectAttributes redirect) {
        userService.approveUser(id, authUtil.getCurrentUserId());
        redirect.addFlashAttribute("success", "User approved. They can now sign in.");
        return "redirect:/users";
    }

    @PostMapping("/reject")
    public String rejectUser(@RequestParam("id") Long id, RedirectAttributes redirect) {
        userService.deleteUser(id);
        redirect.addFlashAttribute("success", "Account request rejected.");
        return "redirect:/users#pending";
    }

    /** The delete form posts here (the page used to post to a URL that did not exist). */
    @PostMapping("/delete")
    public String deleteUser(@RequestParam("id") Long id, RedirectAttributes redirect) {
        if (id.equals(authUtil.getCurrentUserId())) {
            redirect.addFlashAttribute("error", "You cannot delete your own account while signed in.");
            return "redirect:/users";
        }
        userService.deleteUser(id);
        redirect.addFlashAttribute("success", "User deleted.");
        return "redirect:/users";
    }

    @PostMapping("/edit")
    public String updateUserFromModal(
            @RequestParam("id") Long id,
            @RequestParam("name") String name,
            @RequestParam("email") String email,
            @RequestParam("username") String username,
            @RequestParam("role") String role,
            RedirectAttributes redirect) {

        User existing = userService.getUserById(id);
        String cleanRole = role == null ? "" : role.trim().toLowerCase();
        if (!ROLES.contains(cleanRole)) {
            redirect.addFlashAttribute("error", "Choose a valid role.");
            return "redirect:/users";
        }
        if (!username.equals(existing.getUsername()) && userRepository.existsByUsername(username.trim())) {
            redirect.addFlashAttribute("error", "That username is already taken.");
            return "redirect:/users";
        }
        if (!email.equalsIgnoreCase(existing.getEmail()) && userRepository.existsByEmail(email.trim())) {
            redirect.addFlashAttribute("error", "That email address is already in use.");
            return "redirect:/users";
        }

        User updatedUser = new User();
        updatedUser.setName(name.trim());
        updatedUser.setEmail(email.trim());
        updatedUser.setUsername(username.trim());
        updatedUser.setRole(cleanRole);
        userService.updateUser(id, updatedUser);
        redirect.addFlashAttribute("success", "User details saved.");
        return "redirect:/users";
    }
}
