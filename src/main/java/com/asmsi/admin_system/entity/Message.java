// src/main/java/com/asmsi/admin_system/entity/Message.java
package com.asmsi.admin_system.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "messages")
@Data
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String sender;      // username, e.g. "Engr. Jay"

    @Column(nullable = false)
    private String recipient;   // username, e.g. "jemay"

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    private LocalDateTime timestamp;

    @Column(nullable = false)
    private boolean seen = false;
}