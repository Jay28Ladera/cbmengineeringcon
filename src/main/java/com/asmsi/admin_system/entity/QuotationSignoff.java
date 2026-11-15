package com.asmsi.admin_system.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Approval and client sign-off for one quotation: who submitted it, who approved it (with a copy of their
 * signature as it was that day), when and to whom it was emailed, the client's private link, and the
 * client's acceptance (typed name, drawn signature or uploaded signed copy) or decline.
 */
@Entity
@Table(name = "quotation_signoff")
public class QuotationSignoff {
    @Id
    @Column(name = "quotation_id")
    public Long quotationId;

    // ---- internal approval
    @Column(name = "submitted_by", length = 160) public String submittedBy;
    @Column(name = "submitted_by_id") public Long submittedById;
    @Column(name = "submitted_at") public LocalDateTime submittedAt;
    @Column(name = "approver_id") public Long approverId;
    @Column(name = "approver_name", length = 160) public String approverName;
    @Column(name = "approver_title", length = 160) public String approverTitle;
    @Column(name = "approved_at") public LocalDateTime approvedAt;
    @Column(name = "approver_sig", columnDefinition = "bytea") public byte[] approverSig;
    @Column(name = "approver_sig_type", length = 40) public String approverSigType;
    @Column(name = "return_note", length = 1000) public String returnNote;
    @Column(name = "returned_by", length = 160) public String returnedBy;
    @Column(name = "returned_at") public LocalDateTime returnedAt;

    // ---- sending
    @Column(name = "public_token", unique = true, length = 64) public String publicToken;
    @Column(name = "sent_by", length = 160) public String sentBy;
    @Column(name = "sent_by_email", length = 200) public String sentByEmail;
    @Column(name = "sent_to", length = 400) public String sentTo;
    @Column(name = "sent_cc", length = 800) public String sentCc;
    @Column(name = "emailed") public Boolean emailed;
    @Column(name = "expires_at") public LocalDateTime expiresAt;
    @Column(name = "first_viewed_at") public LocalDateTime firstViewedAt;
    @Column(name = "last_viewed_at") public LocalDateTime lastViewedAt;
    @Column(name = "view_count") public Integer viewCount;

    // ---- client response
    @Column(name = "client_name", length = 160) public String clientName;
    @Column(name = "client_title", length = 160) public String clientTitle;
    @Column(name = "client_responded_at") public LocalDateTime clientRespondedAt;
    @Column(name = "client_sig", columnDefinition = "bytea") public byte[] clientSig;
    @Column(name = "client_file", columnDefinition = "bytea") public byte[] clientFile;
    @Column(name = "client_file_name", length = 200) public String clientFileName;
    @Column(name = "client_file_type", length = 100) public String clientFileType;
    @Column(name = "client_ip", length = 64) public String clientIp;
    @Column(name = "client_agent", length = 300) public String clientAgent;
    @Column(name = "decline_reason", length = 1000) public String declineReason;

    public QuotationSignoff() {}
    public QuotationSignoff(Long quotationId) { this.quotationId = quotationId; }

    private static String data(String type, byte[] b) {
        return b == null ? null : "data:" + (type == null ? "image/png" : type) + ";base64," + java.util.Base64.getEncoder().encodeToString(b);
    }
    public String getApproverSigUrl() { return data(approverSigType, approverSig); }
    public String getClientSigUrl() { return data("image/png", clientSig); }
    public boolean isApproved() { return approvedAt != null; }
    public boolean hasClientFile() { return clientFile != null && clientFile.length > 0; }
    public boolean isClientFileImage() { return clientFileType != null && clientFileType.startsWith("image/"); }
    public String getClientFileUrl() { return isClientFileImage() ? data(clientFileType, clientFile) : null; }
    public boolean isExpired() { return expiresAt != null && LocalDateTime.now().isAfter(expiresAt); }
}
