package com.asmsi.admin_system.config;

import com.asmsi.admin_system.entity.User;
import com.asmsi.admin_system.repository.MessageRepository;
import com.asmsi.admin_system.repository.UserRepository;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.security.Principal;
import java.util.Optional;

@ControllerAdvice
public class GlobalModelAttributes {

    private final UserRepository userRepository;
    private final MessageRepository messageRepository;

    public GlobalModelAttributes(UserRepository userRepository,
                                 MessageRepository messageRepository) {
        this.userRepository = userRepository;
        this.messageRepository = messageRepository;
    }

    @ModelAttribute("username")
    public String username(Principal principal) {
        if (principal == null) {
            return null;
        }

        return principal.getName();
    }

    @ModelAttribute("displayName")
    public String displayName(Principal principal) {
        if (principal == null) {
            return null;
        }

        Optional<User> userOptional = userRepository.findByUsername(principal.getName());

        if (userOptional.isEmpty()) {
            return principal.getName();
        }

        User user = userOptional.get();

        if (user.getName() == null || user.getName().trim().isEmpty()) {
            return principal.getName();
        }

        return user.getName();
    }

    @ModelAttribute("profileImageUrl")
    public String profileImageUrl(Principal principal) {
        if (principal == null) {
            return null;
        }

        Optional<User> userOptional = userRepository.findByUsername(principal.getName());

        if (userOptional.isEmpty()) {
            return null;
        }

        User user = userOptional.get();

        if (user.getProfileImageUrl() == null || user.getProfileImageUrl().trim().isEmpty()) {
            return null;
        }

        return user.getProfileImageUrl();
    }

    @ModelAttribute("role")
    public String role(Principal principal) {
        if (principal == null) {
            return "user";
        }

        Optional<User> userOptional = userRepository.findByUsername(principal.getName());

        if (userOptional.isEmpty()) {
            return "user";
        }

        User user = userOptional.get();

        if (user.getRole() == null || user.getRole().trim().isEmpty()) {
            return "user";
        }

        return user.getRole();
    }

    @ModelAttribute("unreadCount")
    public Long unreadCount(Principal principal) {
        if (principal == null) {
            return 0L;
        }

        String currentUser = principal.getName();

        if (currentUser == null || currentUser.trim().isEmpty()) {
            return 0L;
        }

        return messageRepository.countByRecipientAndSeenFalse(currentUser);
    }
}