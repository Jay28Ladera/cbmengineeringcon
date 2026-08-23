package com.asmsi.admin_system.controller;

import com.asmsi.admin_system.entity.BookingStatus;
import com.asmsi.admin_system.entity.ElibraryRoom;
import com.asmsi.admin_system.entity.Student;

import com.asmsi.admin_system.repository.ElibraryBookingPcUserRepository;
import com.asmsi.admin_system.repository.ElibraryRoomRepository;
import com.asmsi.admin_system.repository.StudentRepository;

import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import org.springframework.format.annotation.DateTimeFormat;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

import org.springframework.stereotype.Controller;

import org.springframework.ui.Model;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.time.LocalDate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Controller
public class ElibraryReportController {

    private static final LocalDate REPORT_MIN_DATE =
            LocalDate.of(1900, 1, 1);

    private static final LocalDate REPORT_MAX_DATE =
            LocalDate.of(2999, 12, 31);

    @Autowired
    private ElibraryRoomRepository elibraryRoomRepository;

    @Autowired
    private ElibraryBookingPcUserRepository elibraryBookingPcUserRepository;

    @Autowired
    private StudentRepository studentRepository;


    // ============================================================
    // REPORT PAGE
    //
    // IMPORTANT:
    // We DO NOT call findApprovedBookings() here anymore.
    //
    // Initial page load now loads only:
    // - logged-in user
    // - room dropdown
    // - page shell
    //
    // The actual report is loaded through AJAX only when needed.
    // ============================================================

    @GetMapping("/elibrary/report")
    public String showElibraryReport(
            Model model,
            Authentication authentication
    ) {

        String username =
                authentication != null
                        ? authentication.getName()
                        : "anonymous";

        String role =
                authentication != null
                        ? authentication.getAuthorities()
                                .stream()
                                .findFirst()
                                .map(GrantedAuthority::getAuthority)
                                .orElse("ROLE_USER")
                        : "ROLE_USER";


        List<ElibraryRoom> rooms =
                elibraryRoomRepository
                        .findAllByOrderByRoomNameAsc();


        model.addAttribute("username", username);
        model.addAttribute("role", role);
        model.addAttribute("activePage", "elibrary-myreport");

        model.addAttribute("rooms", rooms);

        model.addAttribute("studentInfoExpanded", false);
        model.addAttribute("asmsiExpanded", false);
        model.addAttribute("admissionExpanded", false);
        model.addAttribute("attendanceExpanded", false);
        model.addAttribute("eventsExpanded", false);
        model.addAttribute("familyDayExpanded", false);

        return "elibreport";
    }


    // ============================================================
    // PC SUMMARY
    //
    // Loads counts only.
    //
    // Example:
    // Room 1 · PC1 = 245
    // Room 1 · PC2 = 198
    //
    // No full booking list is loaded.
    // ============================================================

    @GetMapping("/elibrary/report/pc-summary")
    @ResponseBody
    public Map<String, Object> getPcSummary(

            @RequestParam(value = "dateFrom", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate dateFrom,

            @RequestParam(value = "dateTo", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate dateTo,

            @RequestParam(value = "room", defaultValue = "")
            String room,

            @RequestParam(value = "requestedBy", defaultValue = "")
            String requestedBy,

            @RequestParam(value = "studentId", defaultValue = "")
            String studentId

    ) {

        String cleanRoom =
                clean(room);

        String cleanRequestedBy =
                clean(requestedBy);

        String cleanStudentId =
                clean(studentId);


        List<ElibraryBookingPcUserRepository.PcUsageCountProjection> counts =
                elibraryBookingPcUserRepository
                        .findApprovedPcUsageCounts(
                                BookingStatus.APPROVED,
                                normalizeFrom(dateFrom),
                                normalizeTo(dateTo),
                                cleanRoom,
                                cleanRequestedBy,
                                cleanStudentId
                        );


        List<Map<String, Object>> pcs =
                new ArrayList<>();

        long totalUsage =
                0L;


        for (
                ElibraryBookingPcUserRepository.PcUsageCountProjection item
                : counts
        ) {

            Map<String, Object> row =
                    new LinkedHashMap<>();

            row.put(
                    "roomName",
                    item.getRoomName()
            );

            row.put(
                    "pcNumber",
                    item.getPcNumber()
            );

            row.put(
                    "usageCount",
                    item.getUsageCount()
            );

            pcs.add(row);


            if (item.getUsageCount() != null) {
                totalUsage += item.getUsageCount();
            }
        }


        Map<String, Object> response =
                new LinkedHashMap<>();

        response.put(
                "pcs",
                pcs
        );

        response.put(
                "pcCount",
                pcs.size()
        );

        response.put(
                "totalUsage",
                totalUsage
        );

        return response;
    }


    // ============================================================
    // SELECTED PC USAGE
    //
    // Server-side pagination.
    // Default = 20 rows only.
    //
    // The student names are resolved in ONE bulk Student query,
    // not one query for every row.
    // ============================================================

    @GetMapping("/elibrary/report/pc-usage")
    @ResponseBody
    public Map<String, Object> getPcUsage(

            @RequestParam(value = "room", defaultValue = "")
            String room,

            @RequestParam(value = "pcNumber", required = false)
            Integer pcNumber,

            @RequestParam(value = "dateFrom", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate dateFrom,

            @RequestParam(value = "dateTo", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate dateTo,

            @RequestParam(value = "requestedBy", defaultValue = "")
            String requestedBy,

            @RequestParam(value = "studentId", defaultValue = "")
            String studentId,

            @RequestParam(value = "page", defaultValue = "0")
            int page,

            @RequestParam(value = "size", defaultValue = "20")
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


        String cleanRoom =
                clean(room);

        String cleanRequestedBy =
                clean(requestedBy);

        String cleanStudentId =
                clean(studentId);


        Pageable pageable =
                PageRequest.of(
                        safePage,
                        safeSize
                );


        Page<ElibraryBookingPcUserRepository.PcUsageRowProjection> result =
                elibraryBookingPcUserRepository
                        .findApprovedPcUsagePage(
                                BookingStatus.APPROVED,
                                normalizeFrom(dateFrom),
                                normalizeTo(dateTo),
                                cleanRoom,
                                pcNumber,
                                cleanRequestedBy,
                                cleanStudentId,
                                pageable
                        );


        // --------------------------------------------------------
        // Collect IDs from only this page.
        // Usually only 20 IDs.
        // --------------------------------------------------------

        Set<String> studentIds =
                result.getContent()
                        .stream()
                        .map(
                                ElibraryBookingPcUserRepository
                                        .PcUsageRowProjection
                                        ::getStudentIdNumber
                        )
                        .filter(
                                id ->
                                        id != null
                                        && !id.isBlank()
                        )
                        .map(String::trim)
                        .collect(
                                Collectors.toSet()
                        );


        // --------------------------------------------------------
        // ONE database lookup for student names.
        // --------------------------------------------------------

        Map<String, String> studentNameMap =
                new HashMap<>();


        if (!studentIds.isEmpty()) {

            List<Student> students =
                    studentRepository
                            .findByIdNumberIn(
                                    studentIds
                            );


            for (Student student : students) {

                if (
                        student.getIdNumber() != null
                        && !student.getIdNumber().isBlank()
                ) {

                    studentNameMap.put(
                            student.getIdNumber().trim(),
                            student.getName()
                    );
                }
            }
        }


        List<Map<String, Object>> rows =
                new ArrayList<>();


        for (
                ElibraryBookingPcUserRepository.PcUsageRowProjection item
                : result.getContent()
        ) {

            Map<String, Object> row =
                    new LinkedHashMap<>();


            String studentIdNumber =
                    clean(
                            item.getStudentIdNumber()
                    );


            String studentName =
                    studentNameMap.getOrDefault(
                            studentIdNumber,
                            studentIdNumber
                    );


            row.put(
                    "assignmentId",
                    item.getAssignmentId()
            );

            row.put(
                    "bookingId",
                    item.getBookingId()
            );

            row.put(
                    "bookingNumber",
                    item.getBookingNumber()
            );

            row.put(
                    "bookingDate",
                    item.getBookingDate()
            );

            row.put(
                    "startTime",
                    item.getStartTime()
            );

            row.put(
                    "endTime",
                    item.getEndTime()
            );

            row.put(
                    "roomName",
                    item.getRoomName()
            );

            row.put(
                    "pcNumber",
                    item.getPcNumber()
            );

            row.put(
                    "studentId",
                    studentIdNumber
            );

            row.put(
                    "studentName",
                    studentName
            );

            row.put(
                    "teacher",
                    item.getTeacher()
            );

            row.put(
                    "teacherUsername",
                    item.getTeacherUsername()
            );

            rows.add(row);
        }


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


    private LocalDate normalizeFrom(
            LocalDate value
    ) {
        return value == null
                ? REPORT_MIN_DATE
                : value;
    }


    private LocalDate normalizeTo(
            LocalDate value
    ) {
        return value == null
                ? REPORT_MAX_DATE
                : value;
    }


    private String clean(
            String value
    ) {

        return value == null
                ? ""
                : value.trim();
    }
}