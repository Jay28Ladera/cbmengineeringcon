package com.asmsi.admin_system.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** A user's saved signature (PNG), drawn or uploaded on their profile. Used when they approve a quotation. */
@Entity
@Table(name = "user_signature")
public class UserSignature {
    @Id
    @Column(name = "user_id")
    public Long userId;

    @Column(name = "image", nullable = false, columnDefinition = "bytea")
    public byte[] image;

    @Column(name = "content_type", nullable = false, length = 40)
    public String contentType = "image/png";

    @Column(name = "updated_at", nullable = false)
    public LocalDateTime updatedAt;

    public String dataUrl() {
        return "data:" + contentType + ";base64," + java.util.Base64.getEncoder().encodeToString(image);
    }
}
