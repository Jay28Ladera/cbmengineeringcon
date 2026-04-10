// src/main/java/com/asmsi/admin_system/controller/ChatController.java
package com.asmsi.admin_system.controller;

import com.asmsi.admin_system.dto.ChatMessage;
import com.asmsi.admin_system.dto.SeenEvent;
import com.asmsi.admin_system.dto.TypingEvent;
import com.asmsi.admin_system.entity.Message;
import com.asmsi.admin_system.repository.MessageRepository;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;

import java.security.Principal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Controller
public class ChatController {

    private final SimpMessagingTemplate messagingTemplate;
    private final MessageRepository messageRepository;

    private final DateTimeFormatter formatter =
            DateTimeFormatter.ofPattern("MMM d, HH:mm");

    public ChatController(SimpMessagingTemplate messagingTemplate,
                          MessageRepository messageRepository) {
        this.messagingTemplate = messagingTemplate;
        this.messageRepository = messageRepository;
    }

    // client sends to /app/chat.private
    @MessageMapping("/chat.private")
    public void sendPrivate(Principal principal, ChatMessage message) {
        String sender = principal.getName();   // trust backend
        String recipient = message.getTo();

        // 1) Save to DB
        Message m = new Message();
        m.setSender(sender);
        m.setRecipient(recipient);
        m.setContent(message.getContent());
        m.setTimestamp(LocalDateTime.now());
        m.setSeen(false);

        m = messageRepository.save(m);

        // 2) fill DTO for client
        message.setFrom(sender);
        message.setTimestamp(m.getTimestamp().format(formatter));

        // 3) send to recipient
        messagingTemplate.convertAndSendToUser(
                recipient,
                "/queue/messages",
                message
        );
    }

    @MessageMapping("/chat.typing")
    public void typing(TypingEvent event) {
        messagingTemplate.convertAndSendToUser(
                event.getTo(),
                "/queue/typing",
                event
        );
    }

    @MessageMapping("/chat.seen")
    @Transactional
    public void seen(SeenEvent event) {
        // mark messages from "to" → "from" as seen
        messageRepository.markAsSeen(event.getTo(), event.getFrom());

        messagingTemplate.convertAndSendToUser(
                event.getTo(),
                "/queue/seen",
                event
        );
    }
}