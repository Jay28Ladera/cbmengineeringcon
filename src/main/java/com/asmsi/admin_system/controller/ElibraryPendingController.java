package com.asmsi.admin_system.controller;

import com.asmsi.admin_system.entity.BookingStatus;
import com.asmsi.admin_system.entity.ElibraryBooking;
import com.asmsi.admin_system.repository.ElibraryBookingRepository;
import com.asmsi.admin_system.service.ELibraryBookingNotificationService;
import com.asmsi.admin_system.service.EmailService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.util.List;

@Controller
@RequestMapping("/elibpending")
public class ElibraryPendingController {

    private final ElibraryBookingRepository elibraryBookingRepository;
    private final ELibraryBookingNotificationService bookingNotificationService;
    private final EmailService emailService;

    public ElibraryPendingController(ElibraryBookingRepository elibraryBookingRepository,
                                     ELibraryBookingNotificationService bookingNotificationService,
                                     EmailService emailService) {
        this.elibraryBookingRepository = elibraryBookingRepository;
        this.bookingNotificationService = bookingNotificationService;
        this.emailService = emailService;
    }

    @GetMapping
    public String showPendingBookings(Model model) {
        List<ElibraryBooking> pendingBookings =
                elibraryBookingRepository.findByStatusOrderByBookingDateAscStartTimeAsc(BookingStatus.PENDING);

        model.addAttribute("pendingBookings", pendingBookings);
        return "elibpending";
    }

    @PostMapping("/approve/{id}")
    public String approveBooking(@PathVariable Long id,
                                 RedirectAttributes redirectAttributes,
                                 Principal principal) {
        try {
            ElibraryBooking booking = elibraryBookingRepository.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Booking not found."));

            booking.setStatus(BookingStatus.APPROVED);
            booking.setApprovedByUsername(principal != null ? principal.getName() : null);
            booking.setStatusRemarks(null);

            elibraryBookingRepository.save(booking);

            System.out.println("========================================");
            System.out.println("APPROVE BUTTON CLICKED");
            System.out.println("About to call bookingNotificationService for bookingNo = " + booking.getBookingNo());
            System.out.println("requestedBy = " + booking.getRequestedBy());
            System.out.println("requestedByUsername = " + booking.getRequestedByUsername());
            System.out.println("requesterEmail = " + booking.getRequesterEmail());
            System.out.println("approvedByUsername = " + booking.getApprovedByUsername());
            System.out.println("status = " + booking.getStatus());
            System.out.println("========================================");

            try {
                bookingNotificationService.sendApprovedEmail(booking);
            } catch (Exception emailEx) {
                System.out.println("Exception while sending approved email.");
                emailEx.printStackTrace();
            }

            redirectAttributes.addFlashAttribute("successMessage", "Booking approved successfully.");
        } catch (Exception e) {
            e.printStackTrace();
            redirectAttributes.addFlashAttribute("errorMessage", "Failed to approve booking.");
        }

        return "redirect:/elibpending";
    }

    @PostMapping("/reject/{id}")
    public String rejectBooking(@PathVariable Long id,
                                @RequestParam(value = "remarks", required = false) String remarks,
                                RedirectAttributes redirectAttributes,
                                Principal principal) {
        try {
            ElibraryBooking booking = elibraryBookingRepository.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Booking not found."));

            booking.setStatus(BookingStatus.REJECTED);
            booking.setApprovedByUsername(principal != null ? principal.getName() : null);
            booking.setStatusRemarks(remarks);

            elibraryBookingRepository.save(booking);

            System.out.println("========================================");
            System.out.println("REJECT BUTTON CLICKED");
            System.out.println("About to call bookingNotificationService for bookingNo = " + booking.getBookingNo());
            System.out.println("requestedBy = " + booking.getRequestedBy());
            System.out.println("requestedByUsername = " + booking.getRequestedByUsername());
            System.out.println("requesterEmail = " + booking.getRequesterEmail());
            System.out.println("approvedByUsername = " + booking.getApprovedByUsername());
            System.out.println("status = " + booking.getStatus());
            System.out.println("statusRemarks = " + booking.getStatusRemarks());
            System.out.println("========================================");

            try {
                bookingNotificationService.sendRejectedEmail(booking, remarks);
            } catch (Exception emailEx) {
                System.out.println("Exception while sending rejected email.");
                emailEx.printStackTrace();
            }

            redirectAttributes.addFlashAttribute("successMessage", "Booking rejected successfully.");
        } catch (Exception e) {
            e.printStackTrace();
            redirectAttributes.addFlashAttribute("errorMessage", "Failed to reject booking.");
        }

        return "redirect:/elibpending";
    }

    @GetMapping("/test-elibrary-email")
    @ResponseBody
    public String testElibraryEmail() {
        emailService.sendPlainText(
                "engrjohnladera@gmail.com",
                "Test Email from Elibrary",
                "This is a direct SMTP test email from your Elibrary system."
        );
        return "Test email sent.";
    }
}