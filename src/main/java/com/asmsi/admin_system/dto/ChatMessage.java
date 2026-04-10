// src/main/java/com/asmsi/admin_system/dto/ChatMessage.java
package com.asmsi.admin_system.dto;

import lombok.Data;

@Data
public class ChatMessage {
    private String from;
    private String to;
    private String content;
    private String timestamp; // display string (optional)
}