package com.asmsi.admin_system.controller;

import com.asmsi.admin_system.dto.ElibraryBookingForm;
import com.asmsi.admin_system.dto.RoomLiveAvailabilityDto;
import com.asmsi.admin_system.entity.BookingStatus;
import com.asmsi.admin_system.entity.DayCode;
import com.asmsi.admin_system.entity.ElibraryBooking;
import com.asmsi.admin_system.entity.ElibraryOpenDaySetting;
import com.asmsi.admin_system.entity.User;
import com.asmsi.admin_system.repository.UserRepository;
import com.asmsi.admin_system.repository.ElibraryBookingRepository;
import com.asmsi.admin_system.service.ElibraryBookingService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Controller
public class ElibraryBookingController {

    private final ElibraryBookingService bookingService;
    private final UserRepository userRepository;
    private final ElibraryBookingRepository bookingRepository;

    public ElibraryBookingController(
            ElibraryBookingService bookingService,
            UserRepository userRepository,
            ElibraryBookingRepository bookingRepository
    ) {
        this.bookingService = bookingService;
        this.userRepository = userRepository;
        this.bookingRepository = bookingRepository;
    }

    private void loadBase(Model model, Authentication authentication, String activePage) {
        bookingService.initializeDefaultsIfNeeded();

        String username = authentication != null ? authentication.getName() : "anonymous";
        String role = "user";

        if (authentication != null && authentication.getAuthorities() != null) {
            boolean isAdmin = authentication.getAuthorities().stream()
                    .anyMatch(a ->
                            "ROLE_ADMIN".equalsIgnoreCase(a.getAuthority()) ||
                            "ADMIN".equalsIgnoreCase(a.getAuthority())
                    );
            if (isAdmin) {
                role = "admin";
            }
        }

        model.addAttribute("username", username);
        model.addAttribute("role", role);
        model.addAttribute("activePage", activePage);

        model.addAttribute("studentInfoExpanded", false);
        model.addAttribute("asmsiExpanded", false);
        model.addAttribute("admissionExpanded", false);
        model.addAttribute("attendanceExpanded", false);
        model.addAttribute("eventsExpanded", false);
        model.addAttribute("familyDayExpanded", false);
    }

    private String resolveRole(Authentication authentication) {
        if (authentication != null && authentication.getAuthorities() != null) {
            boolean isAdmin = authentication.getAuthorities().stream()
                    .anyMatch(a ->
                            "ROLE_ADMIN".equalsIgnoreCase(a.getAuthority()) ||
                            "ADMIN".equalsIgnoreCase(a.getAuthority())
                    );
            if (isAdmin) {
                return "admin";
            }
        }
        return "user";
    }

    private void loadSettingsData(Model model) {
        model.addAttribute("rooms", bookingService.getRooms());
        model.addAttribute("bookingRule", bookingService.getBookingRule());
        model.addAttribute("roomPcMap", bookingService.getRoomPcMap());

        Map<DayCode, ElibraryOpenDaySetting> dayMap = bookingService.getDayMap();
        model.addAttribute("dayMap", dayMap);

        model.addAttribute("monSetting", dayMap.get(DayCode.MON));
        model.addAttribute("tueSetting", dayMap.get(DayCode.TUE));
        model.addAttribute("wedSetting", dayMap.get(DayCode.WED));
        model.addAttribute("thuSetting", dayMap.get(DayCode.THU));
        model.addAttribute("friSetting", dayMap.get(DayCode.FRI));
        model.addAttribute("satSetting", dayMap.get(DayCode.SAT));
        model.addAttribute("sunSetting", dayMap.get(DayCode.SUN));
    }

    @GetMapping("/pending")
    public String pending(Model model, Authentication authentication) {
        loadBase(model, authentication, "elibrary-pending");

        List<ElibraryBooking> pendingBookings = bookingService.getPendingBookings();
        Map<Long, String> requesterEmailMap = new LinkedHashMap<>();

        for (ElibraryBooking booking : pendingBookings) {
            requesterEmailMap.put(booking.getId(), resolveRequesterEmail(booking));
        }

        model.addAttribute("pendingBookings", pendingBookings);
        model.addAttribute("requesterEmailMap", requesterEmailMap);

        return "elibpending";
    }

    @GetMapping("/approved")
    public String approved(
            Model model,
            Authentication authentication
    ) {

        loadBase(
                model,
                authentication,
                "elibrary-approved"
        );

        // Do NOT load all approved bookings here.
        // The page will fetch only one page of records through AJAX.
        return "elibapproved";
    }


    @GetMapping("/approved/page")
    @ResponseBody
    public Map<String, Object> approvedPage(

            @RequestParam(defaultValue = "0")
            int page,

            @RequestParam(defaultValue = "10")
            int size

    ) {

        int safePage =
                Math.max(
                        page,
                        0
                );

        int safeSize =
                Math.min(
                        Math.max(
                                size,
                                10
                        ),
                        100
                );


        Pageable pageable =
                PageRequest.of(
                        safePage,
                        safeSize
                );


        Page<ElibraryBooking> result =
                bookingRepository
                        .findByStatusOrderByBookingDateDescStartTimeDescCreatedAtDesc(
                                BookingStatus.APPROVED,
                                pageable
                        );


        List<Map<String, Object>> rows =
                result
                        .getContent()
                        .stream()
                        .map(booking -> {

                            Map<String, Object> row =
                                    new LinkedHashMap<>();

                            row.put(
                                    "id",
                                    booking.getId()
                            );

                            row.put(
                                    "bookingNo",
                                    booking.getBookingNo()
                            );

                            row.put(
                                    "requestedBy",
                                    booking.getRequestedBy()
                            );

                            row.put(
                                    "sectionName",
                                    booking.getSectionName()
                            );

                            row.put(
                                    "bookingDate",
                                    booking.getBookingDate()
                            );

                            row.put(
                                    "startTime",
                                    booking.getStartTime()
                            );

                            row.put(
                                    "endTime",
                                    booking.getEndTime()
                            );

                            row.put(
                                    "computersNeeded",
                                    booking.getComputersNeeded()
                            );

                            row.put(
                                    "roomName",
                                    booking.getRoom() != null
                                            ? booking.getRoom().getRoomName()
                                            : ""
                            );

                            row.put(
                                    "approvedByUsername",
                                    booking.getApprovedByUsername()
                            );

                            row.put(
                                    "status",
                                    booking.getStatus() != null
                                            ? booking.getStatus().name()
                                            : ""
                            );

                            return row;
                        })
                        .toList();


        Map<String, Object> response =
                new LinkedHashMap<>();

        response.put(
                "content",
                rows
        );

        response.put(
                "page",
                result.getNumber()
        );

        response.put(
                "size",
                result.getSize()
        );

        response.put(
                "totalElements",
                result.getTotalElements()
        );

        response.put(
                "totalPages",
                result.getTotalPages()
        );

        response.put(
                "first",
                result.isFirst()
        );

        response.put(
                "last",
                result.isLast()
        );

        return response;
    }

    @GetMapping("/my-booking")
    public String myBooking(
            Model model,
            Authentication authentication
    ) {
        loadBase(
                model,
                authentication,
                "elibrary-mybooking"
        );

        // Do not load all bookings here anymore.
        // The page will request only one page of records via AJAX.
        return "elibmybooking";
    }


    @GetMapping("/my-booking/page")
    @ResponseBody
    public Map<String, Object> myBookingPage(

            @RequestParam(defaultValue = "0")
            int page,

            @RequestParam(defaultValue = "10")
            int size,

            @RequestParam(defaultValue = "")
            String status,

            Authentication authentication

    ) {

        int safePage =
                Math.max(
                        page,
                        0
                );

        int safeSize =
                Math.min(
                        Math.max(
                                size,
                                10
                        ),
                        100
                );


        String username =
                authentication != null
                        ? authentication.getName()
                        : "anonymous";

        String role =
                resolveRole(
                        authentication
                );


        BookingStatus statusFilter =
                null;


        if (
                status != null
                && !status.isBlank()
                && !"ALL".equalsIgnoreCase(
                        status.trim()
                )
        ) {

            try {

                statusFilter =
                        BookingStatus.valueOf(
                                status
                                        .trim()
                                        .toUpperCase()
                        );

            } catch (Exception ignored) {

                statusFilter =
                        null;
            }
        }


        Pageable pageable =
                PageRequest.of(
                        safePage,
                        safeSize
                );


        Page<ElibraryBooking> result;


        if (
                "admin".equalsIgnoreCase(
                        role
                )
        ) {

            if (statusFilter == null) {

                result =
                        bookingRepository
                                .findAllByOrderByCreatedAtDesc(
                                        pageable
                                );

            } else {

                result =
                        bookingRepository
                                .findByStatusOrderByCreatedAtDesc(
                                        statusFilter,
                                        pageable
                                );
            }

        } else {

            if (statusFilter == null) {

                result =
                        bookingRepository
                                .findByRequestedByUsernameOrderByCreatedAtDesc(
                                        username,
                                        pageable
                                );

            } else {

                result =
                        bookingRepository
                                .findByRequestedByUsernameAndStatusOrderByCreatedAtDesc(
                                        username,
                                        statusFilter,
                                        pageable
                                );
            }
        }


        List<Map<String, Object>> rows =
                result
                        .getContent()
                        .stream()
                        .map(booking -> {

                            Map<String, Object> row =
                                    new LinkedHashMap<>();

                            row.put(
                                    "id",
                                    booking.getId()
                            );

                            row.put(
                                    "bookingNo",
                                    booking.getBookingNo()
                            );

                            row.put(
                                    "requestedBy",
                                    booking.getRequestedBy()
                            );

                            row.put(
                                    "bookingDate",
                                    booking.getBookingDate()
                            );

                            row.put(
                                    "startTime",
                                    booking.getStartTime()
                            );

                            row.put(
                                    "endTime",
                                    booking.getEndTime()
                            );

                            row.put(
                                    "computersNeeded",
                                    booking.getComputersNeeded()
                            );

                            row.put(
                                    "roomName",
                                    booking.getRoom() != null
                                            ? booking.getRoom().getRoomName()
                                            : ""
                            );

                            row.put(
                                    "purpose",
                                    booking.getPurpose()
                            );

                            row.put(
                                    "status",
                                    booking.getStatus() != null
                                            ? booking.getStatus().name()
                                            : ""
                            );

                            row.put(
                                    "statusRemarks",
                                    booking.getStatusRemarks()
                            );

                            return row;
                        })
                        .toList();


        Map<String, Object> response =
                new LinkedHashMap<>();

        response.put(
                "content",
                rows
        );

        response.put(
                "page",
                result.getNumber()
        );

        response.put(
                "size",
                result.getSize()
        );

        response.put(
                "totalElements",
                result.getTotalElements()
        );

        response.put(
                "totalPages",
                result.getTotalPages()
        );

        response.put(
                "first",
                result.isFirst()
        );

        response.put(
                "last",
                result.isLast()
        );

        return response;
    }

    @GetMapping("/book-now")
    public String bookNow(Model model, Authentication authentication) {
        loadBase(model, authentication, "elibrary-booknow");
        loadSettingsData(model);
        model.addAttribute("bookingForm", new ElibraryBookingForm());
        return "elibbooknow";
    }

    @GetMapping("/settings")
    public String settings(Model model, Authentication authentication) {
        loadBase(model, authentication, "elibrary-settings");
        loadSettingsData(model);
        return "elibsettings";
    }

    @PostMapping("/save")
    public String saveBooking(@ModelAttribute("bookingForm") ElibraryBookingForm form,
                              Authentication authentication,
                              RedirectAttributes redirectAttributes) {
        try {
            String currentUsername = authentication != null ? authentication.getName() : "anonymous";

            User currentUser = userRepository.findByUsername(currentUsername)
                    .or(() -> userRepository.findByUsernameIgnoreCase(currentUsername))
                    .orElse(null);

            String requestedBy = currentUser != null ? safe(currentUser.getUsername()) : currentUsername;
            String requestedByUsername = currentUsername;
            String requesterEmail = currentUser != null ? safe(currentUser.getEmail()) : "";

            bookingService.createBooking(form, requestedBy, requestedByUsername, requesterEmail);

            redirectAttributes.addFlashAttribute("successMessage", "Booking created successfully.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/book-now";
    }

    @PostMapping("/approve/{id}")
    public String approveBooking(@PathVariable Long id,
                                 Authentication authentication,
                                 RedirectAttributes redirectAttributes) {
        try {
            String approvedBy = authentication != null ? authentication.getName() : "system";
            bookingService.approveBooking(id, approvedBy);
            redirectAttributes.addFlashAttribute("successMessage", "Booking approved successfully.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/pending";
    }

    @PostMapping("/reject/{id}")
    public String rejectBooking(@PathVariable Long id,
                                @RequestParam(name = "remarks", required = false) String remarks,
                                Authentication authentication,
                                RedirectAttributes redirectAttributes) {
        try {
            String approvedBy = authentication != null ? authentication.getName() : "system";
            bookingService.rejectBooking(id, approvedBy, remarks);
            redirectAttributes.addFlashAttribute("successMessage", "Booking rejected successfully.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/pending";
    }

    @GetMapping("/room-live-availability")
    @ResponseBody
    public List<RoomLiveAvailabilityDto> roomLiveAvailability(
            @RequestParam String bookingDate,
            @RequestParam String startTime,
            @RequestParam String endTime
    ) {
        return bookingService.getLiveRoomAvailability(
                LocalDate.parse(bookingDate),
                LocalTime.parse(startTime),
                LocalTime.parse(endTime)
        );
    }

    @PostMapping("/settings/save-pc-config")
    @ResponseBody
    public Map<String, Object> savePcConfig(
            @RequestParam Long roomId,
            @RequestParam Integer totalPcs,
            @RequestParam(name = "pcNumbers") List<Integer> pcNumbers,
            @RequestParam(name = "pcStatuses") List<String> pcStatuses
    ) {
        Map<String, Object> response = new LinkedHashMap<>();

        try {
            bookingService.savePcConfiguration(roomId, totalPcs, pcNumbers, pcStatuses);
            response.put("success", true);
            response.put("message", "PC configuration saved successfully.");
        } catch (Exception e) {
            e.printStackTrace();
            response.put("success", false);
            response.put("message", e.getMessage() != null ? e.getMessage() : "Unable to save PC configuration.");
        }

        return response;
    }

    @PostMapping("/settings/save")
    public String saveAllSettings(@RequestParam Map<String, String> params,
                                  @RequestParam(name = "roomIds", required = false) List<Long> roomIds,
                                  @RequestParam(name = "roomNames", required = false) List<String> roomNames,
                                  @RequestParam(name = "roomTotals", required = false) List<Integer> roomTotals,
                                  @RequestParam(name = "roomActiveFlags", required = false) List<String> roomActiveFlags,
                                  RedirectAttributes redirectAttributes) {
        try {
            bookingService.saveAllSettings(params, roomIds, roomNames, roomTotals, roomActiveFlags);
            redirectAttributes.addFlashAttribute("successMessage", "Settings saved successfully.");
        } catch (Exception e) {
            e.printStackTrace();
            redirectAttributes.addFlashAttribute("errorMessage",
                    e.getMessage() != null ? e.getMessage() : "Unable to save settings.");
        }
        return "redirect:/settings";
    }

    @GetMapping("/room-assignable-pcs")
    @ResponseBody
    public Map<String, Object> roomAssignablePcs(
            @RequestParam String roomName,
            @RequestParam String bookingDate,
            @RequestParam String startTime,
            @RequestParam String endTime,
            @RequestParam Integer computersNeeded
    ) {
        Map<String, Object> response = new LinkedHashMap<>();

        try {
            List<Integer> assignablePcNumbers = bookingService.getPreviewAssignablePcNumbers(
                    roomName,
                    LocalDate.parse(bookingDate),
                    LocalTime.parse(startTime),
                    LocalTime.parse(endTime),
                    computersNeeded
            );

            response.put("success", true);
            response.put("roomName", roomName);
            response.put("assignablePcNumbers", assignablePcNumbers);
            response.put("availableCount", assignablePcNumbers.size());
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", e.getMessage() != null ? e.getMessage() : "Unable to preview assignable PCs.");
            response.put("assignablePcNumbers", Collections.emptyList());
            response.put("availableCount", 0);
        }

        return response;
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

    private String safe(String value) {
        return value == null ? "" : value;
    }
}