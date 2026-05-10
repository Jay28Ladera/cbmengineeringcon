package com.asmsi.admin_system.service;

import com.resend.Resend;
import com.resend.core.exception.ResendException;
import com.resend.services.emails.model.CreateEmailOptions;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmailService {

    @Value("${resend.api.key}")
    private String resendApiKey;

    @Value("${resend.from.email}")
    private String fromEmail;

    public void sendApprovalEmail(String toEmail, String fullName) {
        String subject = "Account Approved - Sisters of Mary School Portal";
        String body =
                "Dear " + safe(fullName) + ",\n\n" +
                "Your account has been approved. You may now log in to the portal.\n\n" +
                "Regards,\nSMS Team";

        sendPlainText(toEmail, subject, body);
    }

    public void sendPasswordResetEmail(String toEmail, String token) {
        String resetLink = "https://sistersofmaryschool.online/reset-password?token=" + token;
        // If still local:
        // String resetLink = "http://localhost:8080/reset-password?token=" + token;

        String subject = "Password Reset Request - SMS Portal";
        String body =
                "Dear user,\n\n" +
                "You have requested to reset your password.\n" +
                "Click the link below to set a new password (valid for 15 minutes):\n\n" +
                resetLink + "\n\n" +
                "If you did not request this, please ignore this email.\n\n" +
                "Regards,\nSMS Team";

        sendPlainText(toEmail, subject, body);
    }

    public void sendElibraryBookingApprovedEmail(String toEmail,
                                                 String fullName,
                                                 String bookingNo,
                                                 String bookingDate,
                                                 String startTime,
                                                 String endTime,
                                                 String roomName,
                                                 String computersNeeded,
                                                 String sectionName,
                                                 String purpose) {

        String subject = "Elibrary Booking Approved - " + safe(bookingNo);
        String body =
                "Dear " + safe(fullName) + ",\n\n" +
                "Your Elibrary booking request has been APPROVED.\n\n" +
                "Booking Details:\n" +
                "Request No.: " + safe(bookingNo) + "\n" +
                "Booking Date: " + safe(bookingDate) + "\n" +
                "Time: " + safe(startTime) + " - " + safe(endTime) + "\n" +
                "Room: " + safe(roomName) + "\n" +
                "Computers Needed: " + safe(computersNeeded) + "\n" +
                "Section / Department: " + safe(sectionName) + "\n" +
                "Purpose: " + safe(purpose) + "\n\n" +
                "Please be guided accordingly.\n\n" +
                "Regards,\nSMS Team";

        sendPlainText(toEmail, subject, body);
    }

    public void sendElibraryBookingRejectedEmail(String toEmail,
                                                 String fullName,
                                                 String bookingNo,
                                                 String bookingDate,
                                                 String startTime,
                                                 String endTime,
                                                 String roomName,
                                                 String computersNeeded,
                                                 String sectionName,
                                                 String purpose,
                                                 String remarks) {

        String subject = "Elibrary Booking Rejected - " + safe(bookingNo);
        String body =
                "Dear " + safe(fullName) + ",\n\n" +
                "Your Elibrary booking request has been REJECTED.\n\n" +
                "Booking Details:\n" +
                "Request No.: " + safe(bookingNo) + "\n" +
                "Booking Date: " + safe(bookingDate) + "\n" +
                "Time: " + safe(startTime) + " - " + safe(endTime) + "\n" +
                "Room: " + safe(roomName) + "\n" +
                "Computers Needed: " + safe(computersNeeded) + "\n" +
                "Section / Department: " + safe(sectionName) + "\n" +
                "Purpose: " + safe(purpose) + "\n\n" +
                "Remarks:\n" + safe(remarks) + "\n\n" +
                "You may submit a new request if needed.\n\n" +
                "Regards,\nSMS Team";

        sendPlainText(toEmail, subject, body);
    }

    public void sendPlainText(String toEmail, String subject, String body) {
        if (toEmail == null || toEmail.trim().isEmpty()) {
            System.out.println("Email skipped: recipient is blank.");
            return;
        }

        try {
            System.out.println("Sending email to: " + toEmail);
            System.out.println("Subject: " + subject);

            Resend resend = new Resend(resendApiKey);

            CreateEmailOptions params = CreateEmailOptions.builder()
                    .from(fromEmail)
                    .to(toEmail.trim())
                    .subject(subject)
                    .text(body)
                    .build();

            resend.emails().send(params);

            System.out.println("Email sent successfully to: " + toEmail);
        } catch (ResendException e) {
            System.out.println("Email sending failed to: " + toEmail);
            e.printStackTrace();
            throw new RuntimeException("Failed to send email via Resend: " + e.getMessage(), e);
        } catch (Exception e) {
            System.out.println("Email sending failed to: " + toEmail);
            e.printStackTrace();
            throw new RuntimeException("Unexpected email error: " + e.getMessage(), e);
        }
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}