package com.asmsi.admin_system.controller;

import com.asmsi.admin_system.entity.*;
import com.asmsi.admin_system.payroll.AttendanceService;
import com.asmsi.admin_system.payroll.PayrollService;
import com.asmsi.admin_system.repository.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.time.*;
import java.util.*;

import static com.asmsi.admin_system.controller.HrEmployeeController.*;

/** HR › Biometric Device and Time Logs. */
@Controller
@RequestMapping("/HR")
public class HrAttendanceController {

    private final AttendanceService attendance;
    private final PayrollService payroll;
    private final BiometricDeviceRepository devices;
    private final AttendanceLogRepository logs;
    private final BiometricUserRepository bioUsers;
    private final EmployeeRepository employees;
    private final PayrollPeriodRepository periods;
    private final HolidayRepository holidays;

    public HrAttendanceController(AttendanceService attendance, PayrollService payroll, BiometricDeviceRepository devices,
                                  AttendanceLogRepository logs, BiometricUserRepository bioUsers, EmployeeRepository employees,
                                  PayrollPeriodRepository periods, HolidayRepository holidays) {
        this.attendance = attendance; this.payroll = payroll; this.devices = devices; this.logs = logs;
        this.bioUsers = bioUsers; this.employees = employees; this.periods = periods; this.holidays = holidays;
    }

    // ================================================================ Biometric devices

    public record DeviceRow(BiometricDevice device, long stored) {}

    @Transactional
    @GetMapping("/BiometricDevice")
    public String devices(Model model, HttpServletRequest req) {
        List<DeviceRow> list = new ArrayList<>();
        Map<Long, Long> counts = new HashMap<>();
        for (Object[] r : logs.countPerDevice()) counts.put((Long) r[0], (Long) r[1]);
        for (BiometricDevice d : devices.findAllByOrderByApprovedDescNameAsc()) list.add(new DeviceRow(d, counts.getOrDefault(d.getId(), 0L)));
        URI base = URI.create(ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString());
        boolean https = "https".equalsIgnoreCase(base.getScheme());
        model.addAttribute("devices", list);
        model.addAttribute("serverHost", base.getHost());
        model.addAttribute("serverPort", base.getPort() > 0 ? base.getPort() : (https ? 443 : 80));
        model.addAttribute("serverHttps", https);
        model.addAttribute("waiting", list.stream().filter(r -> !r.device().isApproved()).count());
        model.addAttribute("activePage", "biometric-device");
        return "hr-devices";
    }

    @PostMapping("/BiometricDevice")
    @Transactional
    public String saveDevice(@RequestParam(required = false) Long id, @RequestParam String serialNumber, @RequestParam String name,
                             @RequestParam(required = false) String location, RedirectAttributes redirect) {
        String sn = cut(serialNumber, 64);
        if (sn == null || blank(name)) { redirect.addFlashAttribute("error", "Enter the serial number and a name."); return "redirect:/HR/BiometricDevice"; }
        BiometricDevice d = id == null ? devices.findBySerialNumber(sn).orElseGet(BiometricDevice::new) : devices.findById(id).orElse(null);
        if (d == null) return "redirect:/HR/BiometricDevice";
        Optional<BiometricDevice> other = devices.findBySerialNumber(sn);
        if (other.isPresent() && !other.get().getId().equals(d.getId())) {
            redirect.addFlashAttribute("error", "Another device already has serial number " + sn + ".");
            return "redirect:/HR/BiometricDevice";
        }
        d.setSerialNumber(sn); d.setName(cut(name, 120)); d.setLocation(cut(location, 160)); d.setApproved(true);
        devices.save(d);
        redirect.addFlashAttribute("success", d.getName() + " is approved. Its logs will come in on its next check-in.");
        return "redirect:/HR/BiometricDevice";
    }

    @PostMapping("/BiometricDevice/{id}/resend")
    @Transactional
    public String resend(@PathVariable Long id, RedirectAttributes redirect) {
        devices.findById(id).ifPresent(d -> {
            d.setAttStamp(null);
            d.setPendingCommand("CHECK");
            devices.save(d);
            redirect.addFlashAttribute("success", "Asked " + d.getName() + " to send all its logs again. Logs already saved are skipped.");
        });
        return "redirect:/HR/BiometricDevice";
    }

    @PostMapping("/BiometricDevice/{id}/disable")
    @Transactional
    public String disable(@PathVariable Long id, RedirectAttributes redirect) {
        devices.findById(id).ifPresent(d -> {
            d.setApproved(false); devices.save(d);
            redirect.addFlashAttribute("success", d.getName() + " is paused. It keeps its logs until you approve it again.");
        });
        return "redirect:/HR/BiometricDevice";
    }

    @PostMapping("/BiometricDevice/{id}/delete")
    @Transactional
    public String deleteDevice(@PathVariable Long id, RedirectAttributes redirect) {
        devices.findById(id).ifPresent(d -> {
            devices.delete(d);
            redirect.addFlashAttribute("success", "Removed " + d.getName() + ". Its logs already saved are kept.");
        });
        return "redirect:/HR/BiometricDevice";
    }

    // ================================================================ Time logs

    public record DailyRow(Employee employee, AttendanceService.Day day, boolean group, String groupName) {}
    public record PunchRow(AttendanceLog log, Employee employee, String deviceName, String pinName) {}
    public record PinRow(String pin, long count, LocalDateTime first, LocalDateTime last, String deviceName, Employee suggestion) {}

    @Transactional
    @GetMapping("/TimeLogs")
    public String timeLogs(@RequestParam(defaultValue = "daily") String view,
                           @RequestParam(required = false) String date,
                           @RequestParam(required = false) String from,
                           @RequestParam(required = false) String to,
                           @RequestParam(required = false) Long employee,
                           @RequestParam(required = false) String group,
                           Model model) {
        payroll.syncFromOrgChart();
        var settings = payroll.settings();
        List<Employee> active = payroll.sorted(employees.findByActiveTrue());
        model.addAttribute("activeEmployees", active);
        model.addAttribute("groups", payroll.groups());
        model.addAttribute("group", blank(group) ? null : group);
        model.addAttribute("view", view);
        model.addAttribute("settings", settings);
        List<PayrollPeriod> open = new ArrayList<>();
        for (PayrollPeriod p : periods.findAllByOrderByStartDateDesc()) if (!p.isLocked()) open.add(p);
        model.addAttribute("openPeriods", open);
        model.addAttribute("unmatchedCount", logs.unmatchedPins().size());
        model.addAttribute("activePage", "time-logs");

        if ("punches".equals(view)) {
            LocalDate t = Optional.ofNullable(parseDate(to)).orElse(LocalDate.now());
            LocalDate f = Optional.ofNullable(parseDate(from)).orElse(t.minusDays(6));
            if (f.isAfter(t)) { LocalDate x = f; f = t; t = x; }
            Map<String, Employee> pins = attendance.byPin();
            Map<Long, Employee> byId = new HashMap<>();
            for (Employee e : employees.findAll()) byId.put(e.getId(), e);
            Map<Long, String> devName = new HashMap<>();
            for (BiometricDevice d : devices.findAll()) devName.put(d.getId(), d.getName());
            Map<String, String> pinNames = new HashMap<>();
            for (BiometricUser u : bioUsers.findAll()) pinNames.put(u.getPin(), u.getName());
            List<PunchRow> rows = new ArrayList<>();
            for (AttendanceLog a : logs.findRange(f.atStartOfDay(), t.plusDays(1).atStartOfDay(), PageRequest.of(0, 2000))) {
                Long emp = AttendanceService.employeeOf(a, pins);
                if (employee != null && !employee.equals(emp)) continue;
                rows.add(new PunchRow(a, emp == null ? null : byId.get(emp), devName.get(a.getDeviceId()), a.getPin() == null ? null : pinNames.get(a.getPin())));
                if (rows.size() >= 500) break;
            }
            model.addAttribute("punches", rows);
            model.addAttribute("from", f.toString());
            model.addAttribute("to", t.toString());
            model.addAttribute("employeeId", employee);
            model.addAttribute("capped", rows.size() >= 500);
        } else if ("unmatched".equals(view)) {
            Map<String, BiometricUser> users = new HashMap<>();
            for (BiometricUser u : bioUsers.findAll()) users.put(u.getPin(), u);
            Map<Long, String> devName = new HashMap<>();
            for (BiometricDevice d : devices.findAll()) devName.put(d.getId(), d.getName());
            List<Employee> free = new ArrayList<>();
            for (Employee e : active) if (blank(e.getBiometricId())) free.add(e);
            List<PinRow> rows = new ArrayList<>();
            for (Object[] r : logs.unmatchedPins()) {
                String pin = (String) r[0];
                BiometricUser u = users.get(pin);
                rows.add(new PinRow(pin, ((Number) r[1]).longValue(), (LocalDateTime) r[2], (LocalDateTime) r[3],
                        u == null ? null : (u.getName() + (u.getDeviceId() != null && devName.containsKey(u.getDeviceId()) ? " · " + devName.get(u.getDeviceId()) : "")),
                        suggest(pin, u == null ? null : u.getName(), free)));
            }
            model.addAttribute("pins", rows);
            model.addAttribute("freeEmployees", free);
        } else {
            LocalDate d = Optional.ofNullable(parseDate(date)).orElse(LocalDate.now());
            Map<Long, Map<LocalDate, List<LocalDateTime>>> punches = attendance.punchesBy(d, d);
            Set<Long> shown = new HashSet<>();
            List<DailyRow> rows = new ArrayList<>();
            int present = 0, late = 0, incomplete = 0, none = 0, notEnrolled = 0;
            String last = null;
            List<Employee> list = new ArrayList<>(active);
            for (Long id : punches.keySet()) if (list.stream().noneMatch(e -> e.getId().equals(id))) employees.findById(id).ifPresent(list::add);
            for (Employee e : payroll.sorted(list)) {
                String g = blank(e.getPayGroup()) ? "No group" : e.getPayGroup().trim();
                if (group != null && !group.isBlank() && !group.equals(g)) continue;
                List<LocalDateTime> p = punches.getOrDefault(e.getId(), Map.of()).getOrDefault(d, List.of());
                AttendanceService.Day day = AttendanceService.compute(d, p, settings);
                switch (day.status()) {
                    case "PRESENT" -> present++;
                    case "LATE" -> { present++; late++; }
                    case "INCOMPLETE", "CHECK" -> incomplete++;
                    default -> { if (blank(e.getBiometricId())) notEnrolled++; else none++; }
                }
                rows.add(new DailyRow(e, day, !g.equals(last), g));
                last = g;
                shown.add(e.getId());
            }
            Holiday hol = holidays.findByDateBetweenOrderByDateAsc(d, d).stream().findFirst().orElse(null);
            model.addAttribute("date", d);
            model.addAttribute("prevDate", d.minusDays(1).toString());
            model.addAttribute("nextDate", d.plusDays(1).toString());
            model.addAttribute("isToday", d.equals(LocalDate.now()));
            model.addAttribute("isSunday", d.getDayOfWeek() == DayOfWeek.SUNDAY);
            model.addAttribute("holiday", hol);
            model.addAttribute("daily", rows);
            model.addAttribute("present", present);
            model.addAttribute("late", late);
            model.addAttribute("incomplete", incomplete);
            model.addAttribute("noLogs", none);
            model.addAttribute("notEnrolled", notEnrolled);
        }
        return "hr-timelogs";
    }

    private static Employee suggest(String pin, String deviceName, List<Employee> free) {
        for (Employee e : free) if (e.getEmployeeNo() != null && pin.equals(AttendanceService.cleanPin(e.getEmployeeNo()))) return e;
        if (deviceName == null || deviceName.isBlank()) return null;
        String dn = norm(deviceName);
        for (Employee e : free) if (norm(e.getFullName()).equals(dn)) return e;
        for (Employee e : free) {
            String n = norm(e.getFullName());
            String[] parts = dn.split(" ");
            if (parts.length >= 2 && n.contains(parts[0]) && n.contains(parts[parts.length - 1])) return e;
        }
        return null;
    }

    private static String norm(String s) { return s == null ? "" : s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9 ]", " ").replaceAll("\\s+", " ").trim(); }

    @PostMapping("/TimeLogs/import")
    public String importFile(@RequestParam("file") MultipartFile file, RedirectAttributes redirect) {
        if (file == null || file.isEmpty()) { redirect.addFlashAttribute("error", "Choose the file exported from the biometric machine."); return "redirect:/HR/TimeLogs"; }
        try {
            List<AttendanceService.Punch> punches = attendance.parse(file.getOriginalFilename(), file.getInputStream());
            if (punches.isEmpty()) {
                redirect.addFlashAttribute("error", "No time logs were found in " + file.getOriginalFilename()
                        + ". Use the attendance log export (attlog.dat, .txt, .csv or Excel) with the user ID and date/time of each punch.");
                return "redirect:/HR/TimeLogs";
            }
            AttendanceService.SaveResult r = attendance.save(punches, "IMPORT", null, currentUser());
            LocalDate first = punches.stream().map(p -> p.time().toLocalDate()).min(Comparator.naturalOrder()).orElse(LocalDate.now());
            LocalDate last = punches.stream().map(p -> p.time().toLocalDate()).max(Comparator.naturalOrder()).orElse(LocalDate.now());
            int unmatched = logs.unmatchedPins().size();
            redirect.addFlashAttribute("success", String.format("Imported %,d new punches from %s (%s to %s). %,d were already saved%s.%s",
                    r.added(), file.getOriginalFilename(), first, last, r.duplicates(),
                    r.skipped() > 0 ? String.format(", %,d unreadable lines skipped", r.skipped()) : "",
                    unmatched > 0 ? " " + unmatched + " device ID(s) aren't linked to an employee yet — see Unmatched IDs." : ""));
            return "redirect:/HR/TimeLogs?view=punches&from=" + first + "&to=" + last;
        } catch (Exception ex) {
            redirect.addFlashAttribute("error", "Couldn't read " + file.getOriginalFilename() + ": " + ex.getMessage());
            return "redirect:/HR/TimeLogs";
        }
    }

    @PostMapping("/TimeLogs/manual")
    @Transactional
    public String addManual(@RequestParam Long employeeId, @RequestParam String date, @RequestParam List<String> times,
                            @RequestParam(required = false) String note, RedirectAttributes redirect) {
        Employee e = employees.findById(employeeId).orElse(null);
        LocalDate d = parseDate(date);
        if (e == null || d == null) { redirect.addFlashAttribute("error", "Choose the employee and date."); return "redirect:/HR/TimeLogs"; }
        if (blank(note)) { redirect.addFlashAttribute("error", "Enter the reason (e.g. forgot to time out, on-site work)."); return "redirect:/HR/TimeLogs?date=" + d; }
        int added = 0;
        for (String t : times) {
            if (blank(t)) continue;
            LocalTime lt;
            try { lt = LocalTime.parse(t.trim()); } catch (Exception ex) { continue; }
            LocalDateTime at = d.atTime(lt);
            String pin = AttendanceService.cleanPin(e.getBiometricId());
            if (pin != null ? logs.existsByPinAndPunchTime(pin, at) : logs.existsByEmployeeIdAndPunchTime(e.getId(), at)) continue;
            AttendanceLog a = new AttendanceLog();
            a.setPin(pin); a.setEmployeeId(pin == null ? e.getId() : null); a.setPunchTime(at); a.setSource("MANUAL");
            a.setNote(cut(note, 200)); a.setCreatedBy(currentUser());
            logs.save(a);
            added++;
        }
        redirect.addFlashAttribute(added > 0 ? "success" : "error", added > 0 ? "Added " + added + " punch(es) for " + e.getFullName() + "." : "Enter at least one time.");
        return "redirect:/HR/TimeLogs?date=" + d;
    }

    @PostMapping("/TimeLogs/{id}/delete")
    @Transactional
    public String deletePunch(@PathVariable Long id, @RequestParam(defaultValue = "punches") String back,
                              @RequestParam(required = false) String from, @RequestParam(required = false) String to,
                              RedirectAttributes redirect) {
        AttendanceLog a = logs.findById(id).orElse(null);
        if (a != null) { logs.delete(a); redirect.addFlashAttribute("success", "Punch at " + a.getPunchTime().toString().replace('T', ' ') + " removed."); }
        if ("daily".equals(back) && a != null) return "redirect:/HR/TimeLogs?date=" + a.getPunchTime().toLocalDate();
        return "redirect:/HR/TimeLogs?view=punches" + (from != null ? "&from=" + from : "") + (to != null ? "&to=" + to : "");
    }

    @PostMapping("/TimeLogs/link")
    @Transactional
    public String linkPin(@RequestParam String pin, @RequestParam Long employeeId, RedirectAttributes redirect) {
        String p = AttendanceService.cleanPin(pin);
        Employee e = employees.findById(employeeId).orElse(null);
        if (p == null || e == null) return "redirect:/HR/TimeLogs?view=unmatched";
        Optional<Employee> other = employees.findByBiometricId(p);
        if (other.isPresent() && !other.get().getId().equals(e.getId())) {
            redirect.addFlashAttribute("error", "Device ID " + p + " already belongs to " + other.get().getFullName() + ".");
        } else {
            e.setBiometricId(p); employees.save(e);
            redirect.addFlashAttribute("success", "Device ID " + p + " is now " + e.getFullName() + ". Their punches show under Time Logs.");
        }
        return "redirect:/HR/TimeLogs?view=unmatched";
    }

    @PostMapping("/TimeLogs/link-auto")
    @Transactional
    public String linkAuto(RedirectAttributes redirect) {
        Map<String, BiometricUser> users = new HashMap<>();
        for (BiometricUser u : bioUsers.findAll()) users.put(u.getPin(), u);
        List<Employee> free = new ArrayList<>();
        for (Employee e : employees.findByActiveTrue()) if (blank(e.getBiometricId())) free.add(e);
        int n = 0;
        for (Object[] r : logs.unmatchedPins()) {
            String pin = (String) r[0];
            BiometricUser u = users.get(pin);
            Employee s = suggest(pin, u == null ? null : u.getName(), free);
            if (s == null) continue;
            s.setBiometricId(pin); employees.save(s); free.remove(s); n++;
        }
        redirect.addFlashAttribute(n > 0 ? "success" : "error", n > 0 ? "Linked " + n + " device ID(s) by employee ID or name. Check the rest by hand." : "No device ID matched an employee ID or name.");
        return "redirect:/HR/TimeLogs?view=unmatched";
    }

    @PostMapping("/TimeLogs/apply")
    public String apply(@RequestParam Long periodId, @RequestParam(defaultValue = "fill") String mode,
                        @RequestParam(required = false) String group, @RequestParam(defaultValue = "timelogs") String back,
                        RedirectAttributes redirect) {
        PayrollPeriod p = periods.findById(periodId).orElse(null);
        if (p == null) { redirect.addFlashAttribute("error", "Choose a payroll period."); return "redirect:/HR/TimeLogs"; }
        try {
            AttendanceService.ApplyResult r = attendance.applyToTimesheet(p, "replace".equals(mode), group);
            StringBuilder msg = new StringBuilder(String.format("Time sheet %s: wrote %d day(s) for %d employee(s) from the time logs.", p.getLabel(), r.daysWritten(), r.employees()));
            if (r.daysKept() > 0) msg.append(String.format(" Kept %d day(s) already typed in.", r.daysKept()));
            if (r.daysIncomplete() > 0) msg.append(String.format(" %d day(s) have a missing time-in/out and were skipped — add the missing punch under Time Logs.", r.daysIncomplete()));
            if (r.unmatchedPins() > 0) msg.append(String.format(" %d device ID(s) aren't linked to anyone.", r.unmatchedPins()));
            redirect.addFlashAttribute("success", msg.toString());
        } catch (IllegalStateException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
        }
        return "timesheet".equals(back) ? "redirect:/HR/TimeSheets?period=" + periodId + (blank(group) ? "" : "&group=" + java.net.URLEncoder.encode(group, java.nio.charset.StandardCharsets.UTF_8)) : "redirect:/HR/TimeLogs";
    }
}
