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
        return principal != null ? principal.getName() : null;
    }

    @ModelAttribute("displayName")
    public String displayName(Principal principal) {
        if (principal == null) return null;
        Optional<User> user = userRepository.findByUsername(principal.getName());
        return user.map(User::getName).orElse(null);
    }

    @ModelAttribute("profileImageUrl")
    public String profileImageUrl(Principal principal) {
        if (principal == null) return null;
        return userRepository.findByUsername(principal.getName())
                .map(User::getProfileImageUrl)
                .orElse(null);
    }

    @ModelAttribute("unreadCount")
    public Long unreadCount(Principal principal) {
        if (principal == null) return 0L;
        return messageRepository.countByRecipientAndSeenFalse(principal.getName());
    }
}