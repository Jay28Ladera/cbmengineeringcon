package com.asmsi.admin_system.service;

import lombok.RequiredArgsConstructor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    public void sendApprovalEmail(String toEmail, String fullName) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(toEmail);
        message.setSubject("Account Approved - Sisters of Mary School Portal");
        message.setText("Dear " + fullName + ",\n\nYour account has been approved. You may now log in to the portal.\n\nRegards,\nSMS Team");

        mailSender.send(message);
    }

    public void sendPasswordResetEmail(String toEmail, String token) {
        String resetLink = "http://localhost:8080/reset-password?token=" + token;

        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(toEmail);
        message.setSubject("Password Reset Request - SMS Portal");
        message.setText("Dear user,\n\nYou have requested to reset your password.\nClick the link below to set a new password (valid for 15 minutes):\n\n" + resetLink + "\n\nIf you did not request this, please ignore this email.\n\nRegards,\nSMS Team");

        mailSender.send(message);
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
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(toEmail);
        message.setSubject("Elibrary Booking Approved - " + safe(bookingNo));
        message.setText(
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
                "Regards,\nSMS Team"
        );

        mailSender.send(message);
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
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(toEmail);
        message.setSubject("Elibrary Booking Rejected - " + safe(bookingNo));
        message.setText(
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
                "Regards,\nSMS Team"
        );

        mailSender.send(message);
    }

    public void sendPlainText(String toEmail, String subject, String body) {
        if (toEmail == null || toEmail.trim().isEmpty()) {
            System.out.println("Email skipped: recipient is blank.");
            return;
        }

        try {
            System.out.println("Sending email to: " + toEmail);
            System.out.println("Subject: " + subject);

            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(toEmail.trim());
            message.setSubject(subject);
            message.setText(body);

            mailSender.send(message);

            System.out.println("Email sent successfully to: " + toEmail);
        } catch (Exception e) {
            System.out.println("Email sending failed to: " + toEmail);
            e.printStackTrace();
            throw e;
        }
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}