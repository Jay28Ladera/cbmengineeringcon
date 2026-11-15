package com.asmsi.admin_system.controller;

import com.asmsi.admin_system.service.StaffAccountService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** Public page opened from the activation email: the staff member sets their password. */
@Controller
public class ActivationController {

    private final StaffAccountService staffAccounts;

    public ActivationController(StaffAccountService staffAccounts) {
        this.staffAccounts = staffAccounts;
    }

    @GetMapping("/activate")
    public String form(@RequestParam(required = false) String token, Model model) {
        staffAccounts.findPending(token).ifPresentOrElse(p -> {
            model.addAttribute("token", token);
            model.addAttribute("name", p.user().getName());
            model.addAttribute("username", p.user().getUsername());
        }, () -> model.addAttribute("error", "This activation link is invalid or was already used. Ask the CBM office for a new one."));
        return "activate";
    }

    @PostMapping("/activate")
    public String activate(@RequestParam String token, @RequestParam String username,
                           @RequestParam String password, @RequestParam String confirmPassword, Model model) {
        if (!password.equals(confirmPassword)) {
            return again(token, username, "The passwords don't match.", model);
        }
        try {
            staffAccounts.activate(token, username, password);
            return "redirect:/login?activated";
        } catch (StaffAccountService.InviteException e) {
            return again(token, username, e.getMessage(), model);
        }
    }

    private String again(String token, String username, String error, Model model) {
        staffAccounts.findPending(token).ifPresent(p -> {
            model.addAttribute("token", token);
            model.addAttribute("name", p.user().getName());
        });
        model.addAttribute("username", username);
        model.addAttribute("error", error);
        return "activate";
    }
}
