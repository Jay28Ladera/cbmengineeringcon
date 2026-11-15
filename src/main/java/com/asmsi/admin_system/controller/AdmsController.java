package com.asmsi.admin_system.controller;

import com.asmsi.admin_system.entity.BiometricDevice;
import com.asmsi.admin_system.payroll.AttendanceService;
import com.asmsi.admin_system.repository.BiometricDeviceRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Receives logs pushed by ZKTeco (and compatible) attendance machines over the ADMS
 * "cloud server" protocol. On the machine: Comm. → Cloud Server Setting → server address = this
 * site's domain, port 443 with HTTPS on (or 80), "Enable domain name" on.
 *
 * <ul>
 *   <li>{@code GET /iclock/cdata?SN=…&options=all} — the machine asks for its settings.</li>
 *   <li>{@code POST /iclock/cdata?SN=…&table=ATTLOG} — punches, one per line: PIN, time, status, verify…</li>
 *   <li>{@code POST /iclock/cdata?SN=…&table=OPERLOG} — user list changes ("USER PIN=1 Name=…").</li>
 *   <li>{@code GET /iclock/getrequest?SN=…} — the machine checks for commands every few seconds.</li>
 * </ul>
 * Only machines HR has approved on the Biometric Device page are accepted; an unknown serial number
 * shows up there as "waiting for approval", and its logs stay on the machine until approved.
 */
@RestController
@RequestMapping("/iclock")
public class AdmsController {

    private static final Logger log = LoggerFactory.getLogger(AdmsController.class);
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final BiometricDeviceRepository devices;
    private final AttendanceService attendance;

    public AdmsController(BiometricDeviceRepository devices, AttendanceService attendance) {
        this.devices = devices; this.attendance = attendance;
    }

    @GetMapping({"/cdata", "/cdata.aspx"})
    @Transactional
    public ResponseEntity<String> options(@RequestParam(name = "SN", required = false) String sn,
                                          @RequestParam(required = false) String pushver,
                                          HttpServletRequest req) {
        BiometricDevice d = touch(sn, req);
        if (d == null) return text(400, "ERROR: no serial number");
        if (pushver != null && d.getFirmware() == null) d.setFirmware("push " + pushver);
        String body = "GET OPTION FROM: " + d.getSerialNumber() + "\n"
                + "ATTLOGStamp=" + stamp(d.getAttStamp()) + "\n"
                + "OPERLOGStamp=" + stamp(d.getOpStamp()) + "\n"
                + "ATTPHOTOStamp=None\n"
                + "ErrorDelay=30\n"
                + "Delay=10\n"
                + "TransTimes=00:00;14:05\n"
                + "TransInterval=1\n"
                + "TransFlag=TransData AttLog OpLog EnrollUser\n"
                + "TimeZone=8\n"
                + "Realtime=1\n"
                + "Encrypt=None\n";
        return text(200, body);
    }

    @PostMapping({"/cdata", "/cdata.aspx"})
    @Transactional
    public ResponseEntity<String> data(HttpServletRequest req) throws java.io.IOException {
        // Read the raw body first: some machines send it as form data, which would otherwise be
        // swallowed as request parameters. Parameters are taken from the query string.
        String body = new String(req.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        Map<String, String> q = query(req.getQueryString());
        String sn = q.get("SN"), table = q.get("table"), stamp = q.get("Stamp"), opStamp = q.get("OpStamp");
        BiometricDevice d = touch(sn, req);
        if (d == null) return text(400, "ERROR: no serial number");
        if (!d.isApproved()) return text(403, "ERROR: device not approved");
        String t = table == null ? "" : table.trim().toUpperCase(Locale.ROOT);
        List<String> lines = body == null ? List.of() : body.lines().filter(l -> !l.isBlank()).toList();

        if (t.equals("ATTLOG")) {
            List<AttendanceService.Punch> punches = new ArrayList<>();
            for (String line : lines) {
                String[] f = line.split("\t");
                if (f.length < 2) continue;
                LocalDateTime time;
                try { time = LocalDateTime.parse(f[1].trim(), TS); } catch (Exception e) { continue; }
                Integer state = null;
                if (f.length > 2) try { state = Integer.valueOf(f[2].trim()); } catch (NumberFormatException ignore) { }
                punches.add(new AttendanceService.Punch(f[0], time, state, null));
            }
            AttendanceService.SaveResult r = attendance.save(punches, "DEVICE", d.getId(), null);
            if (stamp != null && !stamp.isBlank()) d.setAttStamp(stamp.trim());
            if (r.added() > 0) d.setLastLogAt(LocalDateTime.now());
            devices.save(d);
            log.info("Device {} sent {} punches ({} new)", d.getSerialNumber(), punches.size(), r.added());
            return text(200, "OK: " + lines.size());
        }
        if (t.equals("OPERLOG")) {
            for (String line : lines) {
                if (!line.startsWith("USER ")) continue;
                Map<String, String> kv = new HashMap<>();
                for (String part : line.substring(5).split("\t")) {
                    int eq = part.indexOf('=');
                    if (eq > 0) kv.put(part.substring(0, eq).trim(), part.substring(eq + 1).trim());
                }
                attendance.rememberUser(kv.get("PIN"), kv.get("Name"), d.getId());
            }
            String s = opStamp != null ? opStamp : stamp;
            if (s != null && !s.isBlank()) d.setOpStamp(s.trim());
            devices.save(d);
            return text(200, "OK: " + lines.size());
        }
        // Photos, fingerprint templates, options and anything else: acknowledge so the machine moves on.
        return text(200, "OK: " + lines.size());
    }

    @GetMapping({"/getrequest", "/getrequest.aspx"})
    @Transactional
    public ResponseEntity<String> getRequest(@RequestParam(name = "SN", required = false) String sn,
                                             @RequestParam(name = "INFO", required = false) String info,
                                             HttpServletRequest req) {
        BiometricDevice d = touch(sn, req);
        if (d == null) return text(400, "ERROR: no serial number");
        if (info != null && !info.isBlank()) {
            // INFO=firmware,users,fingerprints,logs,ip,...
            String[] f = info.split(",");
            if (f.length > 0 && !f[0].isBlank()) d.setFirmware(cut(f[0].trim(), 120));
            if (f.length > 1) d.setUserCount(intOrNull(f[1]));
            if (f.length > 3) d.setLogCount(intOrNull(f[3]));
        }
        String cmd = d.isApproved() ? d.getPendingCommand() : null;
        if (cmd != null && !cmd.isBlank()) {
            d.setPendingCommand(null);
            devices.save(d);
            return text(200, "C:" + (System.currentTimeMillis() % 100000) + ":" + cmd + "\n");
        }
        devices.save(d);
        return text(200, "OK");
    }

    @PostMapping({"/devicecmd", "/devicecmd.aspx"})
    @Transactional
    public ResponseEntity<String> commandResult(@RequestParam(name = "SN", required = false) String sn, HttpServletRequest req) {
        touch(sn, req);
        return text(200, "OK");
    }

    /** Records the check-in; an unknown serial number is added as "waiting for approval". */
    private BiometricDevice touch(String sn, HttpServletRequest req) {
        if (sn == null || sn.isBlank() || sn.length() > 64) return null;
        String serial = sn.trim();
        BiometricDevice d = devices.findBySerialNumber(serial).orElseGet(() -> {
            BiometricDevice n = new BiometricDevice();
            n.setSerialNumber(serial);
            n.setName("New device " + serial);
            n.setApproved(false);
            log.info("Unknown attendance device {} connected; waiting for approval", serial);
            return n;
        });
        d.setLastSeenAt(LocalDateTime.now());
        String ip = req.getHeader("X-Forwarded-For");
        d.setLastIp(cut(ip != null && !ip.isBlank() ? ip.split(",")[0].trim() : req.getRemoteAddr(), 64));
        return devices.save(d);
    }

    private static Map<String, String> query(String qs) {
        Map<String, String> m = new HashMap<>();
        if (qs == null) return m;
        for (String part : qs.split("&")) {
            int eq = part.indexOf('=');
            String k = java.net.URLDecoder.decode(eq < 0 ? part : part.substring(0, eq), java.nio.charset.StandardCharsets.UTF_8);
            String v = eq < 0 ? "" : java.net.URLDecoder.decode(part.substring(eq + 1), java.nio.charset.StandardCharsets.UTF_8);
            m.putIfAbsent(k, v);
        }
        return m;
    }

    private static String stamp(String s) { return s == null || s.isBlank() ? "0" : s; }
    private static Integer intOrNull(String s) { try { return Integer.valueOf(s.trim()); } catch (Exception e) { return null; } }
    private static String cut(String s, int n) { return s == null ? null : (s.length() > n ? s.substring(0, n) : s); }

    private static ResponseEntity<String> text(int status, String body) {
        return ResponseEntity.status(status).contentType(MediaType.TEXT_PLAIN).body(body);
    }
}
