package com.asmsi.admin_system.controller;

import com.asmsi.admin_system.dto.ElibraryReportBookingDto;
import com.asmsi.admin_system.entity.ElibraryRoom;
import com.asmsi.admin_system.repository.ElibraryRoomRepository;
import com.asmsi.admin_system.service.ElibraryBookingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.util.List;

@Controller
public class ElibraryReportController {

    @Autowired
    private ElibraryBookingService elibraryBookingService;

    @Autowired
    private ElibraryRoomRepository elibraryRoomRepository;

    @GetMapping("/elibrary/report")
    public String showElibraryReport(
            @RequestParam(value = "dateFrom", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,

            @RequestParam(value = "dateTo", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,

            @RequestParam(value = "room", required = false) String room,
            @RequestParam(value = "requestedBy", required = false) String requestedBy,
            Model model,
            Authentication authentication
    ) {
        String username = authentication.getName();
        String role = authentication.getAuthorities().stream()
                .findFirst()
                .map(GrantedAuthority::getAuthority)
                .orElse("ROLE_USER");

        List<ElibraryRoom> rooms = elibraryRoomRepository.findAllByOrderByRoomNameAsc();
        List<ElibraryReportBookingDto> approvedBookings =
                elibraryBookingService.findApprovedBookings(dateFrom, dateTo, room, requestedBy);

        long approvedBookingsCount = approvedBookings.size();
        long approvedAssignedPcCount = approvedBookings.stream()
                .mapToLong(b -> b.getPcAssignments() != null ? b.getPcAssignments().size() : 0)
                .sum();

        model.addAttribute("username", username);
        model.addAttribute("role", role);
        model.addAttribute("activePage", "elibrary-myreport");

        model.addAttribute("rooms", rooms);
        model.addAttribute("approvedBookings", approvedBookings);
        model.addAttribute("approvedBookingsCount", approvedBookingsCount);
        model.addAttribute("approvedAssignedPcCount", approvedAssignedPcCount);

        model.addAttribute("dateFrom", dateFrom);
        model.addAttribute("dateTo", dateTo);
        model.addAttribute("room", room);
        model.addAttribute("requestedBy", requestedBy);

        model.addAttribute("studentInfoExpanded", false);
        model.addAttribute("asmsiExpanded", false);
        model.addAttribute("admissionExpanded", false);
        model.addAttribute("attendanceExpanded", false);
        model.addAttribute("eventsExpanded", false);
        model.addAttribute("familyDayExpanded", false);

        return "elibreport";
    }
}