// src/main/java/com/asmsi/admin_system/controller/MessageController.java
package com.asmsi.admin_system.controller;

import com.asmsi.admin_system.entity.Message;
import com.asmsi.admin_system.entity.User;
import com.asmsi.admin_system.repository.MessageRepository;
import com.asmsi.admin_system.repository.UserRepository;
import com.asmsi.admin_system.tracker.OnlineUserTracker;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Controller
public class MessageController {

    private final UserRepository userRepository;
    private final MessageRepository messageRepository;
    private final OnlineUserTracker tracker;

    public MessageController(UserRepository userRepository,
                             MessageRepository messageRepository,
                             OnlineUserTracker tracker) {
        this.userRepository = userRepository;
        this.messageRepository = messageRepository;
        this.tracker = tracker;
    }

    @GetMapping("/messages")
    public String messages(Model model, Principal principal) {
        String current = principal != null ? principal.getName() : null;

        if (current != null) {
            model.addAttribute("username", current); // used in header & JS
        }

        List<User> allUsers = userRepository.findAll();
        List<User> otherUsers = allUsers.stream()
                .filter(u -> !u.getUsername().equals(current))
                .collect(Collectors.toList());

        Set<String> onlineUsers = tracker.getOnlineUsers();

        model.addAttribute("users", otherUsers);
        model.addAttribute("onlineUsers", onlineUsers);

        return "messages"; // messages.html
    }

    @GetMapping("/messages/history/{other}")
    @ResponseBody
    public List<Message> history(@PathVariable("other") String otherUser,
                                 Principal principal) {
        String current = principal.getName(); // e.g. "Engr. Jay"
        return messageRepository.findConversation(current, otherUser);
    }
}