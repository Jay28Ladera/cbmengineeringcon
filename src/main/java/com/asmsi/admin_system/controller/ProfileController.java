// src/main/java/com/asmsi/admin_system/controller/ProfileController.java
package com.asmsi.admin_system.controller;

import com.asmsi.admin_system.entity.User;
import com.asmsi.admin_system.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.security.Principal;
import java.util.Optional;
import java.util.UUID;

@Controller
public class ProfileController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    // Folder where profile images will be stored on the server
    private static final String UPLOAD_DIR = "uploads/profile";

    public ProfileController(UserRepository userRepository,
                             PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    private void setCommon(Model model, User user) {
        if (user != null) {
            model.addAttribute("username", user.getUsername());
            model.addAttribute("displayName", user.getName());
            model.addAttribute("fullName", user.getName());
            model.addAttribute("email", user.getEmail());
            model.addAttribute("profileImageUrl", user.getProfileImageUrl());
            // you don't have phone in User, but HTML expects it
            model.addAttribute("phone", ""); // or store in a separate table later
        }
    }

    @GetMapping("/profile")
    public String profile(Model model, Principal principal) {
        if (principal == null) {
            return "redirect:/login";
        }

        Optional<User> optionalUser = userRepository.findByUsername(principal.getName());
        if (optionalUser.isEmpty()) {
            return "redirect:/login";
        }

        User user = optionalUser.get();
        setCommon(model, user);
        return "profile";   // loads profile.html
    }

    @GetMapping("/my-profile")
    public String legacyRoute() {
        return "redirect:/profile";
    }

    @PostMapping("/profile/update")
    public String updateProfile(
            Model model,
            Principal principal,
            @RequestParam String fullName,
            @RequestParam String username,
            @RequestParam String email,
            @RequestParam(required = false) String phone, // currently not saved
            @RequestParam(required = false) String currentPassword,
            @RequestParam(required = false) String newPassword,
            @RequestParam(required = false) String confirmPassword,
            @RequestParam(required = false) MultipartFile profileImage
    ) {
        if (principal == null) {
            return "redirect:/login";
        }

        Optional<User> optionalUser = userRepository.findByUsername(principal.getName());
        if (optionalUser.isEmpty()) {
            return "redirect:/login";
        }

        User user = optionalUser.get();

        // Validate username uniqueness if changed
        if (!username.equals(user.getUsername()) && userRepository.existsByUsername(username)) {
            model.addAttribute("error", "Username is already taken.");
            setCommon(model, user);
            return "profile";
        }

        // Validate email uniqueness if changed
        if (!email.equals(user.getEmail()) && userRepository.existsByEmail(email)) {
            model.addAttribute("error", "Email is already in use.");
            setCommon(model, user);
            return "profile";
        }

        // Update basic info
        user.setName(fullName);
        user.setUsername(username);
        user.setEmail(email);
        // (phone not saved yet – add a field in User if you want)

        // Handle password change (optional)
        boolean wantsPasswordChange = newPassword != null && !newPassword.isBlank();
        if (wantsPasswordChange) {
            if (currentPassword == null || currentPassword.isBlank()
                    || !passwordEncoder.matches(currentPassword, user.getPassword())) {
                model.addAttribute("error", "Current password is incorrect.");
                setCommon(model, user);
                return "profile";
            }

            if (!newPassword.equals(confirmPassword)) {
                model.addAttribute("error", "New password and confirmation do not match.");
                setCommon(model, user);
                return "profile";
            }

            user.setPassword(passwordEncoder.encode(newPassword));
        }

        // Handle profile image upload (optional)
        if (profileImage != null && !profileImage.isEmpty()) {
            try {
                // Validate file size (max 2MB)
                long maxSize = 2 * 1024 * 1024;
                if (profileImage.getSize() > maxSize) {
                    model.addAttribute("error", "Profile image must be 2 MB or smaller.");
                    setCommon(model, user);
                    return "profile";
                }

                // Validate content type (simple check)
                String contentType = profileImage.getContentType();
                if (contentType == null || !contentType.startsWith("image/")) {
                    model.addAttribute("error", "Invalid image type. Please upload a valid image file.");
                    setCommon(model, user);
                    return "profile";
                }

                // Ensure upload directory exists
                Path uploadPath = Paths.get(UPLOAD_DIR);
                if (!Files.exists(uploadPath)) {
                    Files.createDirectories(uploadPath);
                }

                // Build unique file name
                String originalFilename = profileImage.getOriginalFilename();
                String extension = "";
                if (originalFilename != null && originalFilename.contains(".")) {
                    extension = originalFilename.substring(originalFilename.lastIndexOf("."));
                }

                String fileName = "user-" + user.getId() + "-" + UUID.randomUUID() + extension;
                Path targetPath = uploadPath.resolve(fileName);

                // Save file to disk (replace existing)
                Files.copy(profileImage.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);

                // Public URL path (served via WebConfig)
                String imageUrl = "/uploads/profile/" + fileName;
                user.setProfileImageUrl(imageUrl);

            } catch (IOException e) {
                e.printStackTrace();
                model.addAttribute("error", "Failed to upload profile image. Please try again.");
                setCommon(model, user);
                return "profile";
            }
        }

        // Save user to DB
        userRepository.save(user);

        model.addAttribute("success", "Profile updated successfully.");
        setCommon(model, user);

        return "profile";
    }
}