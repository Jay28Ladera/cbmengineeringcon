package com.asmsi.admin_system.service;

import com.asmsi.admin_system.entity.ElibraryBooking;
import com.asmsi.admin_system.entity.User;
import com.asmsi.admin_system.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class ELibraryBookingNotificationService {

    private final UserRepository userRepository;
    private final EmailService emailService;

    public ELibraryBookingNotificationService(UserRepository userRepository,
                                              EmailService emailService) {
        this.userRepository = userRepository;
        this.emailService = emailService;
    }

    public void sendApprovedEmail(ElibraryBooking booking) {
        System.out.println("========================================");
        System.out.println("ELibrary APPROVED EMAIL FLOW START");
        System.out.println("Booking No: " + safe(booking != null ? booking.getBookingNo() : null));
        logBookingIdentity(booking);

        String targetEmail = resolveRequesterEmail(booking);
        System.out.println("Resolved requester email = [" + targetEmail + "]");

        if (targetEmail.isEmpty()) {
            System.out.println("No requester email found. Approved email not sent.");
            System.out.println("========================================");
            return;
        }

        String fullName = resolveRequesterName(booking);

        String subject = "E-Library Booking Approved - " + safe(booking.getBookingNo());

        String body =
                "Good day " + safe(fullName) + ",\n\n" +
                "Your E-Library booking request has been APPROVED.\n\n" +
                "Booking Details:\n" +
                "Request No.: " + safe(booking.getBookingNo()) + "\n" +
                "Requested By: " + safe(booking.getRequestedBy()) + "\n" +
                "Booking Date: " + safe(String.valueOf(booking.getBookingDate())) + "\n" +
                "Time: " + safe(String.valueOf(booking.getStartTime())) + " - " + safe(String.valueOf(booking.getEndTime())) + "\n" +
                "Room: " + safe(booking.getRoom() != null ? booking.getRoom().getRoomName() : "") + "\n" +
                "Computers Needed: " + safe(String.valueOf(booking.getComputersNeeded())) + "\n" +
                "Section / Department: " + safe(booking.getSectionName()) + "\n" +
                "Purpose: " + safe(booking.getPurpose()) + "\n\n" +
                "Please be guided accordingly.\n\n" +
                "Thank you.";

        try {
            emailService.sendPlainText(targetEmail, subject, body);
            System.out.println("Approved email sent successfully.");
        } catch (Exception e) {
            System.out.println("Approved email sending failed.");
            e.printStackTrace();
        }

        System.out.println("ELibrary APPROVED EMAIL FLOW END");
        System.out.println("========================================");
    }

    public void sendRejectedEmail(ElibraryBooking booking, String remarks) {
        System.out.println("========================================");
        System.out.println("ELibrary REJECTED EMAIL FLOW START");
        System.out.println("Booking No: " + safe(booking != null ? booking.getBookingNo() : null));
        logBookingIdentity(booking);

        String targetEmail = resolveRequesterEmail(booking);
        System.out.println("Resolved requester email = [" + targetEmail + "]");

        if (targetEmail.isEmpty()) {
            System.out.println("No requester email found. Rejected email not sent.");
            System.out.println("========================================");
            return;
        }

        String fullName = resolveRequesterName(booking);

        String subject = "E-Library Booking Rejected - " + safe(booking.getBookingNo());

        String body =
                "Good day " + safe(fullName) + ",\n\n" +
                "Your E-Library booking request has been REJECTED.\n\n" +
                "Booking Details:\n" +
                "Request No.: " + safe(booking.getBookingNo()) + "\n" +
                "Requested By: " + safe(booking.getRequestedBy()) + "\n" +
                "Booking Date: " + safe(String.valueOf(booking.getBookingDate())) + "\n" +
                "Time: " + safe(String.valueOf(booking.getStartTime())) + " - " + safe(String.valueOf(booking.getEndTime())) + "\n" +
                "Room: " + safe(booking.getRoom() != null ? booking.getRoom().getRoomName() : "") + "\n" +
                "Computers Needed: " + safe(String.valueOf(booking.getComputersNeeded())) + "\n" +
                "Section / Department: " + safe(booking.getSectionName()) + "\n" +
                "Purpose: " + safe(booking.getPurpose()) + "\n\n" +
                "Remarks:\n" + safe(remarks != null && !remarks.trim().isEmpty() ? remarks : "No remarks provided.") + "\n\n" +
                "You may submit a new request if needed.\n\n" +
                "Thank you.";

        try {
            emailService.sendPlainText(targetEmail, subject, body);
            System.out.println("Rejected email sent successfully.");
        } catch (Exception e) {
            System.out.println("Rejected email sending failed.");
            e.printStackTrace();
        }

        System.out.println("ELibrary REJECTED EMAIL FLOW END");
        System.out.println("========================================");
    }

    private String resolveRequesterEmail(ElibraryBooking booking) {
        if (booking == null) {
            return "";
        }

        if (booking.getRequesterEmail() != null && !booking.getRequesterEmail().trim().isEmpty()) {
            return booking.getRequesterEmail().trim();
        }

        String requestedByUsername = safe(booking.getRequestedByUsername()).trim();
        String requestedBy = safe(booking.getRequestedBy()).trim();

        if (!requestedByUsername.isEmpty()) {
            Optional<User> byRequestedByUsername = userRepository.findByUsername(requestedByUsername);
            if (byRequestedByUsername.isPresent()) {
                return safe(byRequestedByUsername.get().getEmail());
            }

            Optional<User> byRequestedByUsernameIgnoreCase = userRepository.findByUsernameIgnoreCase(requestedByUsername);
            if (byRequestedByUsernameIgnoreCase.isPresent()) {
                return safe(byRequestedByUsernameIgnoreCase.get().getEmail());
            }
        }

        if (!requestedBy.isEmpty()) {
            Optional<User> byRequestedBy = userRepository.findByUsername(requestedBy);
            if (byRequestedBy.isPresent()) {
                return safe(byRequestedBy.get().getEmail());
            }

            Optional<User> byRequestedByIgnoreCase = userRepository.findByUsernameIgnoreCase(requestedBy);
            if (byRequestedByIgnoreCase.isPresent()) {
                return safe(byRequestedByIgnoreCase.get().getEmail());
            }
        }

        return "";
    }

    private String resolveRequesterName(ElibraryBooking booking) {
        if (booking == null) {
            return "";
        }

        String requestedByUsername = safe(booking.getRequestedByUsername()).trim();
        String requestedBy = safe(booking.getRequestedBy()).trim();

        if (!requestedByUsername.isEmpty()) {
            Optional<User> byRequestedByUsername = userRepository.findByUsername(requestedByUsername);
            if (byRequestedByUsername.isPresent()) {
                return safe(byRequestedByUsername.get().getName());
            }

            Optional<User> byRequestedByUsernameIgnoreCase = userRepository.findByUsernameIgnoreCase(requestedByUsername);
            if (byRequestedByUsernameIgnoreCase.isPresent()) {
                return safe(byRequestedByUsernameIgnoreCase.get().getName());
            }
        }

        if (!requestedBy.isEmpty()) {
            Optional<User> byRequestedBy = userRepository.findByUsername(requestedBy);
            if (byRequestedBy.isPresent()) {
                return safe(byRequestedBy.get().getName());
            }

            Optional<User> byRequestedByIgnoreCase = userRepository.findByUsernameIgnoreCase(requestedBy);
            if (byRequestedByIgnoreCase.isPresent()) {
                return safe(byRequestedByIgnoreCase.get().getName());
            }
        }

        return safe(booking.getRequestedBy());
    }

    private void logBookingIdentity(ElibraryBooking booking) {
        if (booking == null) {
            System.out.println("Booking identity log skipped: booking is null.");
            return;
        }

        System.out.println("requestedBy = [" + safe(booking.getRequestedBy()) + "]");
        System.out.println("requestedByUsername = [" + safe(booking.getRequestedByUsername()) + "]");
        System.out.println("requesterEmail = [" + safe(booking.getRequesterEmail()) + "]");
        System.out.println("approvedByUsername = [" + safe(booking.getApprovedByUsername()) + "]");
        System.out.println("status = [" + String.valueOf(booking.getStatus()) + "]");
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}