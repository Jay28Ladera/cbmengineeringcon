package com.asmsi.admin_system.controller;

import com.asmsi.admin_system.entity.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;


@Controller
public class HomeController {

    private static final Logger logger = LoggerFactory.getLogger(HomeController.class);

    @GetMapping("/home")
    public String home(Model model) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        User loggedInUser = (User) authentication.getPrincipal();

        logger.info("✅ Logged in user: {}", loggedInUser.getUsername());
        logger.info("🆔 User ID: {}", loggedInUser.getId());
        logger.info("🔐 Role: {}", loggedInUser.getRole());

        model.addAttribute("username", loggedInUser.getUsername());
        model.addAttribute("userId", loggedInUser.getId());
        model.addAttribute("role", loggedInUser.getRole());
        model.addAttribute("name", loggedInUser.getName());
        model.addAttribute("activePage", "home");

        return "home";
    }

    // @GetMapping("/events")
    // public String showEventPage() {
    // return "events";
    // }

}
