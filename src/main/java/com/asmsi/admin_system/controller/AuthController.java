package com.asmsi.admin_system.controller;

import com.asmsi.admin_system.entity.PasswordResetToken;
import com.asmsi.admin_system.entity.User;
import com.asmsi.admin_system.repository.PasswordResetTokenRepository;
import com.asmsi.admin_system.repository.UserRepository;
import com.asmsi.admin_system.service.EmailService;
import com.asmsi.admin_system.service.TokenUtil;
import com.asmsi.admin_system.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Optional;

@Controller
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final UserService userService;
    private final UserRepository userRepository;
    private final PasswordResetTokenRepository passwordTokenRepository;
    private final EmailService emailService;
    private final BCryptPasswordEncoder passwordEncoder;

    public AuthController(UserService userService, UserRepository userRepository,
                          PasswordResetTokenRepository passwordTokenRepository,
                          EmailService emailService, BCryptPasswordEncoder passwordEncoder) {
        this.userService = userService;
        this.userRepository = userRepository;
        this.passwordTokenRepository = passwordTokenRepository;
        this.emailService = emailService;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping("/")
    public String showLandingPage() {
        return "landing-page";
    }

    @GetMapping("/signup")
    public String showSignupForm() {
        return "signup";
    }

    @PostMapping("/request-account")
    public String registerUser(
            @RequestParam String name,
            @RequestParam String username,
            @RequestParam String email,
            @RequestParam String password,
            @RequestParam String role,
            Model model) {

        String response = userService.requestAccount(name.trim(), username.trim(), email.trim(), password, role);
        if (response.contains("already")) {
            model.addAttribute("error", response);
        } else {
            model.addAttribute("success", response);
        }
        return "signup";
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/forget-password")
    public String showForgotPasswordPage() {
        return "forget-password";
    }

    @PostMapping("/forgot-password")
    public String processForgotPassword(@RequestParam("email") String email, Model model) {
        Optional<User> userOptional = userRepository.findByEmail(email.trim());

        if (userOptional.isPresent()) {
            User user = userOptional.get();
            // One token per user: requesting a second link used to fail with a database error.
            passwordTokenRepository.deleteByUserId(user.getId());

            String token = TokenUtil.generateResetToken();
            passwordTokenRepository.save(PasswordResetToken.builder()
                    .token(token)
                    .user(user)
                    .expiryDate(LocalDateTime.now().plusMinutes(15))
                    .build());

            if (emailService.sendPasswordResetEmail(user.getEmail(), token)) {
                model.addAttribute("success", "A reset link has been sent to your email. It expires in 15 minutes.");
            } else {
                model.addAttribute("error", "We couldn't send the email right now. Please try again in a few minutes.");
            }
        } else {
            model.addAttribute("error", "No account found with that email.");
        }
        return "forget-password";
    }

    @GetMapping("/reset-password")
    public String showResetPasswordPage(@RequestParam(value = "token", required = false) String token, Model model) {
        if (!isValid(token)) {
            model.addAttribute("error", "This reset link is invalid or has expired. Request a new one.");
            return "reset-password";
        }
        model.addAttribute("token", token);
        return "reset-password";
    }

    @PostMapping("/reset-password")
    public String handlePasswordReset(
            @RequestParam("token") String token,
            @RequestParam("password") String password,
            @RequestParam("confirmPassword") String confirmPassword,
            Model model) {

        if (!isValid(token)) {
            model.addAttribute("error", "This reset link is invalid or has expired. Request a new one.");
            return "reset-password";
        }
        if (!password.equals(confirmPassword)) {
            model.addAttribute("error", "Passwords do not match.");
            model.addAttribute("token", token);
            return "reset-password";
        }
        if (password.length() < 8) {
            model.addAttribute("error", "Use at least 8 characters.");
            model.addAttribute("token", token);
            return "reset-password";
        }

        PasswordResetToken resetToken = passwordTokenRepository.findByToken(token).orElseThrow();
        User user = resetToken.getUser();
        user.setPassword(passwordEncoder.encode(password));
        userRepository.save(user);
        passwordTokenRepository.delete(resetToken);
        log.info("Password reset for user id {}", user.getId());

        return "redirect:/login?reset";
    }

    private boolean isValid(String token) {
        if (token == null || token.isBlank()) return false;
        return passwordTokenRepository.findByToken(token)
                .map(t -> t.getExpiryDate().isAfter(LocalDateTime.now()))
                .orElse(false);
    }
}
