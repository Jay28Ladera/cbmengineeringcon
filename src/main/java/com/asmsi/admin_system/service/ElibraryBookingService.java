package com.asmsi.admin_system.service;

import com.asmsi.admin_system.dto.ElibraryBookingForm;
import com.asmsi.admin_system.dto.ElibraryReportBookingDto;
import com.asmsi.admin_system.dto.ElibraryReportPcAssignmentDto;
import com.asmsi.admin_system.dto.RoomLiveAvailabilityDto;
import com.asmsi.admin_system.entity.BookingStatus;
import com.asmsi.admin_system.entity.DayCode;
import com.asmsi.admin_system.entity.ElibraryBooking;
import com.asmsi.admin_system.entity.ElibraryBookingPcUser;
import com.asmsi.admin_system.entity.ElibraryBookingRule;
import com.asmsi.admin_system.entity.ElibraryOpenDaySetting;
import com.asmsi.admin_system.entity.ElibraryPc;
import com.asmsi.admin_system.entity.ElibraryRoom;
import com.asmsi.admin_system.entity.PcStatus;
import com.asmsi.admin_system.entity.Student;
import com.asmsi.admin_system.repository.ElibraryBookingPcUserRepository;
import com.asmsi.admin_system.repository.ElibraryBookingRepository;
import com.asmsi.admin_system.repository.ElibraryBookingRuleRepository;
import com.asmsi.admin_system.repository.ElibraryOpenDaySettingRepository;
import com.asmsi.admin_system.repository.ElibraryPcRepository;
import com.asmsi.admin_system.repository.ElibraryRoomRepository;
import com.asmsi.admin_system.repository.StudentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional
public class ElibraryBookingService {

    private final ElibraryRoomRepository roomRepository;
    private final ElibraryPcRepository pcRepository;
    private final ElibraryOpenDaySettingRepository daySettingRepository;
    private final ElibraryBookingRuleRepository bookingRuleRepository;
    private final ElibraryBookingRepository bookingRepository;
    private final ElibraryBookingPcUserRepository bookingPcUserRepository;
    private final ELibraryBookingNotificationService bookingNotificationService;
    private final StudentRepository studentRepository;

    public ElibraryBookingService(
            ElibraryRoomRepository roomRepository,
            ElibraryPcRepository pcRepository,
            ElibraryOpenDaySettingRepository daySettingRepository,
            ElibraryBookingRuleRepository bookingRuleRepository,
            ElibraryBookingRepository bookingRepository,
            ElibraryBookingPcUserRepository bookingPcUserRepository,
            ELibraryBookingNotificationService bookingNotificationService,
            StudentRepository studentRepository
    ) {
        this.roomRepository = roomRepository;
        this.pcRepository = pcRepository;
        this.daySettingRepository = daySettingRepository;
        this.bookingRuleRepository = bookingRuleRepository;
        this.bookingRepository = bookingRepository;
        this.bookingPcUserRepository = bookingPcUserRepository;
        this.bookingNotificationService = bookingNotificationService;
        this.studentRepository = studentRepository;
    }

    public List<ElibraryBooking> getPendingBookings() {
        return bookingRepository.findByStatusOrderByCreatedAtDesc(BookingStatus.PENDING);
    }

    public List<ElibraryBooking> getApprovedBookings() {
        return bookingRepository.findByStatusOrderByCreatedAtDesc(BookingStatus.APPROVED);
    }

    public List<ElibraryReportBookingDto> findApprovedBookings(LocalDate dateFrom,
                                                               LocalDate dateTo,
                                                               String roomName,
                                                               String requestedByUsername) {
        ElibraryRoom room = null;

        if (roomName != null && !roomName.isBlank()) {
            room = roomRepository.findByRoomName(roomName).orElse(null);

            if (room == null) {
                return Collections.emptyList();
            }
        }

        List<ElibraryBooking> bookings = bookingRepository.findApprovedReportBookings(
                BookingStatus.APPROVED,
                dateFrom,
                dateTo,
                room,
                requestedByUsername
        );

        List<ElibraryReportBookingDto> result = new ArrayList<>();

        for (ElibraryBooking booking : bookings) {
            ElibraryReportBookingDto dto = new ElibraryReportBookingDto();
            dto.setId(booking.getId());
            dto.setBookingNumber(booking.getBookingNo());
            dto.setBookingDate(booking.getBookingDate());
            dto.setStartTime(booking.getStartTime() != null ? booking.getStartTime().toString() : null);
            dto.setEndTime(booking.getEndTime() != null ? booking.getEndTime().toString() : null);
            dto.setRoomNumber(booking.getRoom() != null ? booking.getRoom().getRoomName() : null);
            dto.setRequestedBy(booking.getRequestedBy());

            List<ElibraryReportPcAssignmentDto> pcAssignments = new ArrayList<>();

            if (booking.getPcUsers() != null && !booking.getPcUsers().isEmpty()) {
                List<ElibraryBookingPcUser> sortedPcUsers = booking.getPcUsers().stream()
                        .sorted(Comparator.comparing(
                                ElibraryBookingPcUser::getPcNumber,
                                Comparator.nullsLast(Integer::compareTo)
                        ))
                        .toList();

                for (ElibraryBookingPcUser pcUser : sortedPcUsers) {
                    ElibraryReportPcAssignmentDto pcDto = new ElibraryReportPcAssignmentDto();
                    pcDto.setPcNumber(pcUser.getPcNumber());
                    pcDto.setStudentId(pcUser.getStudentIdNumber());

                    String studentName = getStudentNameByIdNumber(pcUser.getStudentIdNumber());
                    pcDto.setStudentName(studentName);

                    pcAssignments.add(pcDto);
                }
            }

            dto.setPcAssignments(pcAssignments);
            result.add(dto);
        }

        return result;
    }

    public List<ElibraryBooking> getBookingsForRole(String username, String role) {
        if ("admin".equalsIgnoreCase(role)) {
            return bookingRepository.findAllByOrderByCreatedAtDesc();
        }
        return bookingRepository.findByRequestedByUsernameOrderByCreatedAtDesc(username);
    }

    public List<ElibraryRoom> getRooms() {
        return roomRepository.findAllByOrderByRoomNameAsc();
    }

    public Map<DayCode, ElibraryOpenDaySetting> getDayMap() {
        Map<DayCode, ElibraryOpenDaySetting> map = new EnumMap<>(DayCode.class);
        for (ElibraryOpenDaySetting setting : daySettingRepository.findAll()) {
            map.put(setting.getDayCode(), setting);
        }
        return map;
    }

    public ElibraryBookingRule getBookingRule() {
        return bookingRuleRepository.findById(1L).orElseGet(() -> {
            ElibraryBookingRule rule = new ElibraryBookingRule();
            rule.setId(1L);
            return bookingRuleRepository.save(rule);
        });
    }

    public List<Integer> getPreviewAssignablePcNumbers(String roomName,
                                                       LocalDate bookingDate,
                                                       LocalTime startTime,
                                                       LocalTime endTime,
                                                       int computersNeeded) {
        if (roomName == null || roomName.isBlank()) {
            return Collections.emptyList();
        }
        if (bookingDate == null || startTime == null || endTime == null) {
            return Collections.emptyList();
        }
        if (computersNeeded < 1) {
            return Collections.emptyList();
        }

        ElibraryRoom room = roomRepository.findByRoomName(roomName)
                .orElseThrow(() -> new IllegalArgumentException("Selected room not found."));

        if (!room.isActive()) {
            return Collections.emptyList();
        }

        return getNextAvailablePcNumbers(
                room,
                bookingDate,
                startTime,
                endTime,
                computersNeeded,
                List.of(BookingStatus.PENDING, BookingStatus.APPROVED)
        );
    }

    public void initializeDefaultsIfNeeded() {
        if (roomRepository.count() == 0) {
            ElibraryRoom r1 = new ElibraryRoom();
            r1.setRoomName("E-Library Room 1");
            r1.setTotalPcs(40);
            r1.setActive(true);
            roomRepository.save(r1);
            syncRoomPcs(r1, 40, Collections.emptyMap());

            ElibraryRoom r2 = new ElibraryRoom();
            r2.setRoomName("E-Library Room 2");
            r2.setTotalPcs(35);
            r2.setActive(true);
            roomRepository.save(r2);
            syncRoomPcs(r2, 35, Collections.emptyMap());
        }

        if (daySettingRepository.count() == 0) {
            for (DayCode code : DayCode.values()) {
                ElibraryOpenDaySetting setting = new ElibraryOpenDaySetting();
                setting.setDayCode(code);

                if (code == DayCode.SAT || code == DayCode.SUN) {
                    setting.setEnabled(false);
                    setting.setBookable(false);
                } else {
                    setting.setEnabled(true);
                    setting.setBookable(true);
                    setting.setOpenTime(LocalTime.of(8, 0));
                    setting.setCloseTime(LocalTime.of(17, 0));
                    setting.setBreakStartTime(LocalTime.of(12, 0));
                    setting.setBreakEndTime(LocalTime.of(13, 0));
                }

                daySettingRepository.save(setting);
            }
        }

        getBookingRule();
    }

    public List<RoomLiveAvailabilityDto> getLiveRoomAvailability(LocalDate bookingDate, LocalTime startTime, LocalTime endTime) {
        List<ElibraryRoom> rooms = getRooms();

        return rooms.stream().map(room -> {
            int configuredAvailablePcs = getConfiguredAvailablePcCount(room);
            int usedPcs = getUsedPcsForSchedule(
                    room,
                    bookingDate,
                    startTime,
                    endTime,
                    List.of(BookingStatus.PENDING, BookingStatus.APPROVED)
            );

            int availablePcs = Math.max(0, configuredAvailablePcs - usedPcs);

            return new RoomLiveAvailabilityDto(
                    room.getRoomName(),
                    configuredAvailablePcs,
                    usedPcs,
                    availablePcs,
                    room.isActive()
            );
        }).toList();
    }

    public ElibraryBooking createBooking(ElibraryBookingForm form,
                                         String requestedBy,
                                         String requestedByUsername,
                                         String requesterEmail) {
        validateBookingForm(form);

        ElibraryRoom room = roomRepository.findByRoomName(form.getRoomNumber())
                .orElseThrow(() -> new IllegalArgumentException("Selected room not found."));

        validateBookingAgainstSettings(room, form);

        List<Integer> assignedPcNumbers = getNextAvailablePcNumbers(
                room,
                form.getBookingDate(),
                form.getStartTime(),
                form.getEndTime(),
                form.getComputersNeeded(),
                List.of(BookingStatus.PENDING, BookingStatus.APPROVED)
        );

        if (assignedPcNumbers.size() != form.getComputersNeeded()) {
            throw new IllegalArgumentException("Unable to assign enough continuous PCs for the selected room and time.");
        }

        ElibraryBookingRule rule = getBookingRule();
        BookingStatus initialStatus = rule.isApprovalRequired()
                ? BookingStatus.PENDING
                : BookingStatus.APPROVED;

        ElibraryBooking booking = new ElibraryBooking();
        booking.setBookingNo(generateBookingNo());
        booking.setRoom(room);
        booking.setBookingDate(form.getBookingDate());
        booking.setStartTime(form.getStartTime());
        booking.setEndTime(form.getEndTime());
        booking.setComputersNeeded(form.getComputersNeeded());
        booking.setRequestedBy(requestedBy != null && !requestedBy.isBlank() ? requestedBy : form.getRequestedBy());
        booking.setSectionName(form.getSectionName());
        booking.setPurpose(form.getPurpose());
        booking.setRemarks(form.getRemarks());
        booking.setRequestedByUsername(requestedByUsername);
        booking.setRequesterEmail(requesterEmail);
        booking.setStatus(initialStatus);

        List<ElibraryBookingPcUser> pcUsers = new ArrayList<>();
        for (int i = 0; i < form.getStudentPcUserIds().size(); i++) {
            ElibraryBookingPcUser pcUser = new ElibraryBookingPcUser();
            pcUser.setBooking(booking);
            pcUser.setPcNumber(assignedPcNumbers.get(i));
            pcUser.setStudentIdNumber(form.getStudentPcUserIds().get(i));
            pcUsers.add(pcUser);
        }
        booking.setPcUsers(pcUsers);

        return bookingRepository.save(booking);
    }

    public ElibraryBooking approveBooking(Long id, String approvedBy) {
        ElibraryBooking booking = bookingRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Booking not found."));

        if (booking.getStatus() != BookingStatus.PENDING) {
            throw new IllegalArgumentException("Only pending bookings can be approved.");
        }

        validateApprovalStillPossible(booking);

        booking.setStatus(BookingStatus.APPROVED);
        booking.setApprovedByUsername(approvedBy);
        booking.setStatusRemarks("Approved");

        ElibraryBooking saved = bookingRepository.save(booking);

        System.out.println("========================================");
        System.out.println("SERVICE APPROVE EMAIL TRIGGER");
        System.out.println("bookingNo = " + saved.getBookingNo());
        System.out.println("requestedBy = " + saved.getRequestedBy());
        System.out.println("requestedByUsername = " + saved.getRequestedByUsername());
        System.out.println("requesterEmail = " + saved.getRequesterEmail());
        System.out.println("========================================");

        try {
            bookingNotificationService.sendApprovedEmail(saved);
        } catch (Exception e) {
            System.out.println("Failed to send approved email from service.");
            e.printStackTrace();
        }

        return saved;
    }

    public ElibraryBooking rejectBooking(Long id, String approvedBy, String remarks) {
        ElibraryBooking booking = bookingRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Booking not found."));

        if (booking.getStatus() != BookingStatus.PENDING) {
            throw new IllegalArgumentException("Only pending bookings can be rejected.");
        }

        booking.setStatus(BookingStatus.REJECTED);
        booking.setApprovedByUsername(approvedBy);
        booking.setStatusRemarks((remarks == null || remarks.isBlank()) ? "Rejected" : remarks.trim());

        ElibraryBooking saved = bookingRepository.save(booking);

        System.out.println("========================================");
        System.out.println("SERVICE REJECT EMAIL TRIGGER");
        System.out.println("bookingNo = " + saved.getBookingNo());
        System.out.println("requestedBy = " + saved.getRequestedBy());
        System.out.println("requestedByUsername = " + saved.getRequestedByUsername());
        System.out.println("requesterEmail = " + saved.getRequesterEmail());
        System.out.println("statusRemarks = " + saved.getStatusRemarks());
        System.out.println("========================================");

        try {
            bookingNotificationService.sendRejectedEmail(saved, saved.getStatusRemarks());
        } catch (Exception e) {
            System.out.println("Failed to send rejected email from service.");
            e.printStackTrace();
        }

        return saved;
    }

    public void saveAllSettings(Map<String, String> params,
                                List<Long> roomIds,
                                List<String> roomNames,
                                List<Integer> roomTotals,
                                List<String> roomActiveFlags) {
        saveOpenDaySettings(params);
        saveRules(params);
        saveRoomsAndPcConfig(roomIds, roomNames, roomTotals, roomActiveFlags, params);
    }

    public void saveOpenDaySettings(Map<String, String> params) {
        for (DayCode code : DayCode.values()) {
            String prefix = code.name().toLowerCase();

            ElibraryOpenDaySetting setting = daySettingRepository.findByDayCode(code).orElseGet(() -> {
                ElibraryOpenDaySetting s = new ElibraryOpenDaySetting();
                s.setDayCode(code);
                return s;
            });

            setting.setOpenTime(parseTime(params.get(prefix + "Open")));
            setting.setCloseTime(parseTime(params.get(prefix + "Close")));
            setting.setBreakStartTime(parseTime(params.get(prefix + "BreakStart")));
            setting.setBreakEndTime(parseTime(params.get(prefix + "BreakEnd")));
            setting.setEnabled(params.containsKey(prefix + "Enabled"));
            setting.setBookable(params.containsKey(prefix + "Bookable"));

            daySettingRepository.save(setting);
        }
    }

    public void saveRoomsAndPcConfig(List<Long> roomIds,
                                     List<String> roomNames,
                                     List<Integer> roomTotals,
                                     List<String> roomActiveFlags,
                                     Map<String, String> params) {
        List<ElibraryRoom> existingRooms = roomRepository.findAll();
        Set<Long> submittedIds = new HashSet<>();

        if (roomNames == null || roomTotals == null) {
            return;
        }

        Set<String> activeRoomIdSet = roomActiveFlags == null
                ? Collections.emptySet()
                : new HashSet<>(roomActiveFlags);

        for (int i = 0; i < roomNames.size(); i++) {
            String roomName = roomNames.get(i) != null ? roomNames.get(i).trim() : "";
            Integer total = roomTotals.get(i);

            if (roomName.isBlank() || total == null || total < 1) {
                continue;
            }

            Long roomId = (roomIds != null && roomIds.size() > i) ? roomIds.get(i) : null;

            ElibraryRoom room;
            if (roomId != null) {
                room = roomRepository.findById(roomId).orElse(new ElibraryRoom());
                submittedIds.add(roomId);
            } else {
                room = new ElibraryRoom();
            }

            boolean active;
            if (room.getId() != null) {
                active = activeRoomIdSet.contains(String.valueOf(room.getId()));
            } else {
                active = true;
            }

            room.setRoomName(roomName);
            room.setTotalPcs(total);
            room.setActive(active);
            roomRepository.save(room);

            Map<Integer, PcStatus> incomingStatuses = new LinkedHashMap<>();
            for (int pcNo = 1; pcNo <= total; pcNo++) {
                String statusKey = "room_" + room.getId() + "_pc_" + pcNo + "_status";
                String rawStatus = params.get(statusKey);
                PcStatus status = parsePcStatus(rawStatus);
                incomingStatuses.put(pcNo, status);
            }

            syncRoomPcs(room, total, incomingStatuses);
        }

        for (ElibraryRoom existing : existingRooms) {
            if (existing.getId() != null && !submittedIds.contains(existing.getId())) {
                List<ElibraryPc> pcs = pcRepository.findByRoomOrderByPcNumberAsc(existing);
                for (ElibraryPc pc : pcs) {
                    pcRepository.delete(pc);
                }
                roomRepository.delete(existing);
            }
        }
    }

    public void savePcConfiguration(Long roomId,
                                    Integer totalPcs,
                                    List<Integer> pcNumbers,
                                    List<String> pcStatuses) {
        if (roomId == null) {
            throw new IllegalArgumentException("Room ID is required.");
        }
        if (totalPcs == null || totalPcs < 1) {
            throw new IllegalArgumentException("Total PCs must be at least 1.");
        }

        ElibraryRoom room = roomRepository.findById(roomId)
                .orElseThrow(() -> new IllegalArgumentException("Room not found."));

        room.setTotalPcs(totalPcs);
        roomRepository.save(room);

        Map<Integer, PcStatus> incomingStatuses = new LinkedHashMap<>();

        if (pcNumbers != null && pcStatuses != null) {
            int size = Math.min(pcNumbers.size(), pcStatuses.size());
            for (int i = 0; i < size; i++) {
                Integer pcNo = pcNumbers.get(i);
                String rawStatus = pcStatuses.get(i);

                if (pcNo != null && pcNo > 0) {
                    incomingStatuses.put(pcNo, parsePcStatus(rawStatus));
                }
            }
        }

        for (int pcNo = 1; pcNo <= totalPcs; pcNo++) {
            incomingStatuses.putIfAbsent(pcNo, PcStatus.AVAILABLE);
        }

        syncRoomPcs(room, totalPcs, incomingStatuses);
    }

    public void saveRules(Map<String, String> params) {
        ElibraryBookingRule rule = getBookingRule();
        rule.setMaxAdvanceDays(parseInt(params.get("maxAdvanceDays"), 30));
        rule.setMinBookingMinutes(parseInt(params.get("minBookingMinutes"), 60));
        rule.setMaxBookingMinutes(parseInt(params.get("maxBookingMinutes"), 180));
        rule.setMaxComputersPerBooking(parseInt(params.get("maxComputersPerBooking"), 40));
        rule.setApprovalRequired(params.containsKey("approvalRequired"));
        rule.setAllowSameDayBooking(params.containsKey("allowSameDayBooking"));
        bookingRuleRepository.save(rule);
    }

    public Map<String, List<ElibraryPc>> getRoomPcMap() {
        Map<String, List<ElibraryPc>> map = new LinkedHashMap<>();
        for (ElibraryRoom room : getRooms()) {
            map.put(room.getRoomName(), pcRepository.findByRoomOrderByPcNumberAsc(room));
        }
        return map;
    }

    private int getUsedPcsForSchedule(ElibraryRoom room,
                                      LocalDate bookingDate,
                                      LocalTime startTime,
                                      LocalTime endTime,
                                      List<BookingStatus> statuses) {
        List<ElibraryBooking> overlappingBookings =
                bookingRepository.findByRoomAndBookingDateAndStartTimeLessThanAndEndTimeGreaterThanAndStatusIn(
                        room,
                        bookingDate,
                        endTime,
                        startTime,
                        statuses
                );

        return overlappingBookings.stream()
                .filter(b -> b.getComputersNeeded() != null)
                .mapToInt(ElibraryBooking::getComputersNeeded)
                .sum();
    }

    private Set<Integer> getUsedPcNumberSetForSchedule(ElibraryRoom room,
                                                       LocalDate bookingDate,
                                                       LocalTime startTime,
                                                       LocalTime endTime,
                                                       List<BookingStatus> statuses) {
        List<ElibraryBooking> overlappingBookings =
                bookingRepository.findByRoomAndBookingDateAndStartTimeLessThanAndEndTimeGreaterThanAndStatusIn(
                        room,
                        bookingDate,
                        endTime,
                        startTime,
                        statuses
                );

        if (overlappingBookings.isEmpty()) {
            return Collections.emptySet();
        }

        return bookingPcUserRepository.findByBookingInOrderByPcNumberAsc(overlappingBookings).stream()
                .map(ElibraryBookingPcUser::getPcNumber)
                .filter(pcNo -> pcNo != null && pcNo > 0)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private List<Integer> getNextAvailablePcNumbers(ElibraryRoom room,
                                                    LocalDate bookingDate,
                                                    LocalTime startTime,
                                                    LocalTime endTime,
                                                    int needed,
                                                    List<BookingStatus> statuses) {
        if (needed <= 0) {
            return Collections.emptyList();
        }

        Set<Integer> configuredAvailablePcNumbers = getConfiguredAvailablePcNumberSet(room);
        if (configuredAvailablePcNumbers.isEmpty()) {
            return Collections.emptyList();
        }

        Set<Integer> usedPcNumbers = getUsedPcNumberSetForSchedule(room, bookingDate, startTime, endTime, statuses);

        List<Integer> assigned = new ArrayList<>();
        for (Integer pcNo : configuredAvailablePcNumbers) {
            if (!usedPcNumbers.contains(pcNo)) {
                assigned.add(pcNo);
                if (assigned.size() == needed) {
                    break;
                }
            }
        }

        return assigned;
    }

    private int getRemainingPcsForSchedule(ElibraryRoom room,
                                           LocalDate bookingDate,
                                           LocalTime startTime,
                                           LocalTime endTime,
                                           List<BookingStatus> statuses) {
        int configuredAvailablePcs = getConfiguredAvailablePcCount(room);
        int usedPcs = getUsedPcsForSchedule(room, bookingDate, startTime, endTime, statuses);
        return Math.max(0, configuredAvailablePcs - usedPcs);
    }

    private void validateBookingForm(ElibraryBookingForm form) {
        if (form.getBookingDate() == null) {
            throw new IllegalArgumentException("Booking date is required.");
        }
        if (form.getStartTime() == null) {
            throw new IllegalArgumentException("Start time is required.");
        }
        if (form.getEndTime() == null) {
            throw new IllegalArgumentException("End time is required.");
        }
        if (form.getRoomNumber() == null || form.getRoomNumber().isBlank()) {
            throw new IllegalArgumentException("Room is required.");
        }
        if (form.getRequestedBy() == null || form.getRequestedBy().isBlank()) {
            throw new IllegalArgumentException("Requested by is required.");
        }
        if (form.getComputersNeeded() == null || form.getComputersNeeded() < 1) {
            throw new IllegalArgumentException("Computers needed must be at least 1.");
        }
        if (!form.getEndTime().isAfter(form.getStartTime())) {
            throw new IllegalArgumentException("End time must be after start time.");
        }
        if (form.getStudentPcUserIds() == null || form.getStudentPcUserIds().size() != form.getComputersNeeded()) {
            throw new IllegalArgumentException("Student IDs must match the number of computers needed.");
        }

        for (String idNo : form.getStudentPcUserIds()) {
            if (idNo == null || !idNo.matches("\\d{12}")) {
                throw new IllegalArgumentException("Each student ID must be exactly 12 digits.");
            }
        }
    }

    private void validateBookingAgainstSettings(ElibraryRoom room, ElibraryBookingForm form) {
        if (!room.isActive()) {
            throw new IllegalArgumentException("Selected room is inactive.");
        }

        ElibraryBookingRule rule = getBookingRule();
        LocalDate today = LocalDate.now();
        long daysAhead = ChronoUnit.DAYS.between(today, form.getBookingDate());

        if (daysAhead < 0) {
            throw new IllegalArgumentException("Booking date cannot be in the past.");
        }
        if (!rule.isAllowSameDayBooking() && daysAhead == 0) {
            throw new IllegalArgumentException("Same day booking is not allowed.");
        }
        if (daysAhead > rule.getMaxAdvanceDays()) {
            throw new IllegalArgumentException("Booking date is beyond the allowed advance booking range.");
        }

        long minutes = ChronoUnit.MINUTES.between(form.getStartTime(), form.getEndTime());
        if (minutes < rule.getMinBookingMinutes()) {
            throw new IllegalArgumentException("Booking duration is below minimum allowed time.");
        }
        if (minutes > rule.getMaxBookingMinutes()) {
            throw new IllegalArgumentException("Booking duration exceeds maximum allowed time.");
        }

        int configuredAvailablePcs = getConfiguredAvailablePcCount(room);

        if (form.getComputersNeeded() > rule.getMaxComputersPerBooking()) {
            throw new IllegalArgumentException("Requested computers exceed the maximum allowed per booking.");
        }
        if (form.getComputersNeeded() > configuredAvailablePcs) {
            throw new IllegalArgumentException("Requested computers exceed the currently usable PCs configured for this room.");
        }

        DayCode code = mapDayOfWeek(form.getBookingDate().getDayOfWeek());
        ElibraryOpenDaySetting setting = daySettingRepository.findByDayCode(code)
                .orElseThrow(() -> new IllegalArgumentException("Day setting not found."));

        if (!setting.isEnabled() || !setting.isBookable()) {
            throw new IllegalArgumentException("The E-Library is not bookable on the selected day.");
        }
        if (setting.getOpenTime() != null && form.getStartTime().isBefore(setting.getOpenTime())) {
            throw new IllegalArgumentException("Booking start time is earlier than open time.");
        }
        if (setting.getCloseTime() != null && form.getEndTime().isAfter(setting.getCloseTime())) {
            throw new IllegalArgumentException("Booking end time is later than close time.");
        }

        if (setting.getBreakStartTime() != null && setting.getBreakEndTime() != null) {
            boolean overlapsBreak = form.getStartTime().isBefore(setting.getBreakEndTime())
                    && form.getEndTime().isAfter(setting.getBreakStartTime());
            if (overlapsBreak) {
                throw new IllegalArgumentException("Booking time overlaps with break time.");
            }
        }

        int remainingPcsForSchedule = getRemainingPcsForSchedule(
                room,
                form.getBookingDate(),
                form.getStartTime(),
                form.getEndTime(),
                List.of(BookingStatus.PENDING, BookingStatus.APPROVED)
        );

        if (form.getComputersNeeded() > remainingPcsForSchedule) {
            throw new IllegalArgumentException(
                    "Only " + remainingPcsForSchedule + " PC(s) are still available for this room at the selected time."
            );
        }
    }

    private void validateApprovalStillPossible(ElibraryBooking booking) {
        int configuredAvailablePcs = getConfiguredAvailablePcCount(booking.getRoom());

        if (booking.getComputersNeeded() > configuredAvailablePcs) {
            throw new IllegalArgumentException("Cannot approve. Requested computers exceed current usable configured PCs.");
        }

        int usedByOtherApprovedBookings = bookingRepository
                .findByRoomAndBookingDateAndStartTimeLessThanAndEndTimeGreaterThanAndStatusIn(
                        booking.getRoom(),
                        booking.getBookingDate(),
                        booking.getEndTime(),
                        booking.getStartTime(),
                        List.of(BookingStatus.APPROVED)
                ).stream()
                .filter(existing -> !existing.getId().equals(booking.getId()))
                .filter(existing -> existing.getComputersNeeded() != null)
                .mapToInt(ElibraryBooking::getComputersNeeded)
                .sum();

        int remainingPcs = Math.max(0, configuredAvailablePcs - usedByOtherApprovedBookings);

        if (booking.getComputersNeeded() > remainingPcs) {
            throw new IllegalArgumentException(
                    "Cannot approve. Only " + remainingPcs + " usable PC(s) remain available for this room and time."
            );
        }
    }

    private void syncRoomPcs(ElibraryRoom room, int total, Map<Integer, PcStatus> incomingStatuses) {
        List<ElibraryPc> existing = pcRepository.findByRoomOrderByPcNumberAsc(room);
        Map<Integer, ElibraryPc> existingMap = existing.stream()
                .collect(Collectors.toMap(ElibraryPc::getPcNumber, p -> p));

        for (int i = 1; i <= total; i++) {
            ElibraryPc pc = existingMap.getOrDefault(i, new ElibraryPc());
            pc.setRoom(room);
            pc.setPcNumber(i);
            pc.setActive(true);
            pc.setStatus(incomingStatuses.getOrDefault(i, pc.getId() == null ? PcStatus.AVAILABLE : pc.getStatus()));
            pcRepository.save(pc);
        }

        for (ElibraryPc pc : existing) {
            if (pc.getPcNumber() > total) {
                pcRepository.delete(pc);
            }
        }
    }

    private DayCode mapDayOfWeek(DayOfWeek dayOfWeek) {
        return switch (dayOfWeek) {
            case MONDAY -> DayCode.MON;
            case TUESDAY -> DayCode.TUE;
            case WEDNESDAY -> DayCode.WED;
            case THURSDAY -> DayCode.THU;
            case FRIDAY -> DayCode.FRI;
            case SATURDAY -> DayCode.SAT;
            case SUNDAY -> DayCode.SUN;
        };
    }

    private LocalTime parseTime(String value) {
        try {
            return (value == null || value.isBlank()) ? null : LocalTime.parse(value);
        } catch (Exception e) {
            return null;
        }
    }

    private int parseInt(String value, int defaultValue) {
        try {
            return (value == null || value.isBlank()) ? defaultValue : Integer.parseInt(value);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private PcStatus parsePcStatus(String rawStatus) {
        if (rawStatus == null || rawStatus.isBlank()) {
            return PcStatus.AVAILABLE;
        }

        String normalized = rawStatus.trim().toUpperCase();

        if ("AVAILABLE".equals(normalized)) {
            return PcStatus.AVAILABLE;
        }

        return PcStatus.IN_USE;
    }

    private String generateBookingNo() {
        return "ELIB-" + System.currentTimeMillis();
    }

    private Set<Integer> getConfiguredAvailablePcNumberSet(ElibraryRoom room) {
        return pcRepository.findByRoomAndActiveTrueAndStatusOrderByPcNumberAsc(room, PcStatus.AVAILABLE).stream()
                .map(ElibraryPc::getPcNumber)
                .filter(pcNo -> pcNo != null && pcNo > 0)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private int getConfiguredAvailablePcCount(ElibraryRoom room) {
        return getConfiguredAvailablePcNumberSet(room).size();
    }

    private String getStudentNameByIdNumber(String studentIdNumber) {
        if (studentIdNumber == null || studentIdNumber.isBlank()) {
            return null;
        }

        String cleanedId = studentIdNumber.trim();

        return studentRepository.findByIdNumberTrimmed(cleanedId)
                .map(Student::getName)
                .filter(name -> name != null && !name.isBlank())
                .orElse(cleanedId);
    }
}