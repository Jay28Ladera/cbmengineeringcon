package com.asmsi.admin_system.inventory;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * The three sign-offs of a canvass sheet (form CBM-CD-006): Approved by (Office Engineer/QS),
 * Noted by (Project/Operations Manager) and Processed by (Finance). Each keeps the signer's name,
 * position, time and a copy of their signature as it was when they signed.
 */
@Entity
@Table(name = "pur_canvass_signoff")
public class CanvassSignoff {
    @Id @Column(name = "canvass_id") public Long canvassId;

    @Column(name = "submitted_by", length = 160) public String submittedBy;
    @Column(name = "submitted_by_id") public Long submittedById;
    @Column(name = "submitted_at") public LocalDateTime submittedAt;

    @Column(name = "approved_by_id") public Long approvedById;
    @Column(name = "approved_by", length = 160) public String approvedBy;
    @Column(name = "approved_title", length = 160) public String approvedTitle;
    @Column(name = "approved_at") public LocalDateTime approvedAt;
    @Column(name = "approved_sig", columnDefinition = "bytea") public byte[] approvedSig;
    @Column(name = "approved_sig_type", length = 40) public String approvedSigType;

    @Column(name = "noted_by_id") public Long notedById;
    @Column(name = "noted_by", length = 160) public String notedBy;
    @Column(name = "noted_title", length = 160) public String notedTitle;
    @Column(name = "noted_at") public LocalDateTime notedAt;
    @Column(name = "noted_sig", columnDefinition = "bytea") public byte[] notedSig;
    @Column(name = "noted_sig_type", length = 40) public String notedSigType;

    @Column(name = "processed_by_id") public Long processedById;
    @Column(name = "processed_by", length = 160) public String processedBy;
    @Column(name = "processed_title", length = 160) public String processedTitle;
    @Column(name = "processed_at") public LocalDateTime processedAt;
    @Column(name = "processed_sig", columnDefinition = "bytea") public byte[] processedSig;
    @Column(name = "processed_sig_type", length = 40) public String processedSigType;

    @Column(name = "return_note", length = 1000) public String returnNote;
    @Column(name = "returned_by", length = 160) public String returnedBy;
    @Column(name = "returned_at") public LocalDateTime returnedAt;

    public CanvassSignoff() {}
    public CanvassSignoff(Long canvassId) { this.canvassId = canvassId; }

    private static String data(String type, byte[] b) {
        return b == null ? null : "data:" + (type == null ? "image/png" : type) + ";base64," + java.util.Base64.getEncoder().encodeToString(b);
    }
    public String getApprovedSigUrl() { return data(approvedSigType, approvedSig); }
    public String getNotedSigUrl() { return data(notedSigType, notedSig); }
    public String getProcessedSigUrl() { return data(processedSigType, processedSig); }

    public void clearSignatures() {
        approvedById = null; approvedBy = null; approvedTitle = null; approvedAt = null; approvedSig = null; approvedSigType = null;
        notedById = null; notedBy = null; notedTitle = null; notedAt = null; notedSig = null; notedSigType = null;
        processedById = null; processedBy = null; processedTitle = null; processedAt = null; processedSig = null; processedSigType = null;
    }
}
