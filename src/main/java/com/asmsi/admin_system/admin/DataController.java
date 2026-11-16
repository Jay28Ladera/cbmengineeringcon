package com.asmsi.admin_system.admin;

import com.asmsi.admin_system.entity.User;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Admin › Data: import from CSV / Excel, backup download, and the access review. */
@Controller
public class DataController {

    private static final String SESSION = "import.rows.";

    private final ImportService imports;
    private final BackupService backups;
    private final AccessReviewService review;

    public DataController(ImportService imports, BackupService backups, AccessReviewService review) {
        this.imports = imports; this.backups = backups; this.review = review;
    }

    private static User me() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return a != null && a.getPrincipal() instanceof User u ? u : null;
    }

    private static String who() { User u = me(); return u == null ? "system" : u.getName(); }

    @GetMapping("/Admin/Data")
    public String page(@RequestParam(required = false) String kind, Model model) {
        model.addAttribute("kinds", ImportService.Kind.values());
        model.addAttribute("kind", ImportService.Kind.parse(kind == null ? "SUPPLIERS" : kind));
        model.addAttribute("tables", backups.tables());
        model.addAttribute("lastBackup", backups.lastBackup());
        model.addAttribute("today", LocalDate.now());
        model.addAttribute("activePage", "data");
        return "admin-data";
    }

    @GetMapping("/Admin/Data/template/{kind}")
    public ResponseEntity<byte[]> template(@PathVariable String kind) {
        ImportService.Kind k = ImportService.Kind.parse(kind);
        if (k == null) return ResponseEntity.notFound().build();
        return csv("template-" + k.name().toLowerCase() + ".csv", imports.template(k));
    }

    @PostMapping("/Admin/Data/import")
    public String preview(@RequestParam String kind, @RequestParam("file") MultipartFile file, HttpSession session, Model model, RedirectAttributes ra) {
        ImportService.Kind k = ImportService.Kind.parse(kind);
        if (k == null) return "redirect:/Admin/Data";
        try {
            if (file == null || file.isEmpty()) throw new IllegalArgumentException("Choose a CSV or Excel file.");
            List<Map<String, String>> rows = imports.read(k, file.getOriginalFilename(), file.getInputStream());
            session.setAttribute(SESSION + k.name(), new ArrayList<>(rows));
            ImportService.Preview p = imports.preview(k, rows);
            model.addAttribute("p", p);
            model.addAttribute("kind", k);
            model.addAttribute("fileName", file.getOriginalFilename());
            model.addAttribute("today", LocalDate.now());
            model.addAttribute("activePage", "data");
            return "admin-import-preview";
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage() == null ? "Couldn't read the file." : e.getMessage());
            return "redirect:/Admin/Data?kind=" + k.name();
        }
    }

    @PostMapping("/Admin/Data/import/{kind}/apply")
    @SuppressWarnings("unchecked")
    public String apply(@PathVariable String kind, @RequestParam(required = false) String date, HttpSession session, Model model, RedirectAttributes ra) {
        ImportService.Kind k = ImportService.Kind.parse(kind);
        if (k == null) return "redirect:/Admin/Data";
        List<Map<String, String>> rows = (List<Map<String, String>>) session.getAttribute(SESSION + k.name());
        if (rows == null) { ra.addFlashAttribute("error", "The checked file expired. Upload it again."); return "redirect:/Admin/Data?kind=" + k.name(); }
        try {
            LocalDate d = date == null || date.isBlank() ? null : LocalDate.parse(date);
            ImportService.Preview p = imports.apply(k, rows, d, who());
            session.removeAttribute(SESSION + k.name());
            ra.addFlashAttribute("success", k.label + " imported: " + p.count("NEW") + " added, " + p.count("UPDATE") + " updated"
                    + (p.count("ERROR") > 0 ? ", " + p.count("ERROR") + " rows with problems skipped" : "") + "."
                    + (p.warnings().isEmpty() ? "" : " " + String.join(" ", p.warnings())));
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Nothing was saved: " + e.getMessage());
        }
        return "redirect:/Admin/Data?kind=" + k.name();
    }

    @GetMapping("/Admin/Data/backup")
    public ResponseEntity<StreamingResponseBody> backup(@RequestParam(defaultValue = "false") boolean audit) {
        String name = backups.fileName();
        String by = who();
        backups.record(audit);
        StreamingResponseBody body = out -> {
            try { backups.write(out, audit, by); }
            catch (java.io.IOException e) { throw e; }
            catch (Exception e) { throw new java.io.IOException(e); }
        };
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + name + "\"")
                .contentType(MediaType.parseMediaType("application/zip")).body(body);
    }

    @GetMapping("/Admin/AccessReview")
    public String access(Model model) {
        model.addAttribute("r", review.review());
        model.addAttribute("modules", com.asmsi.admin_system.entity.AccessModule.values());
        model.addAttribute("activePage", "access-review");
        return "admin-access-review";
    }

    @GetMapping("/Admin/AccessReview.csv")
    public ResponseEntity<byte[]> accessCsv() {
        return csv("access-review-" + LocalDate.now() + ".csv", review.csv(review.review()));
    }

    private static ResponseEntity<byte[]> csv(String name, String body) {
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + name + "\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8)).body(("﻿" + body).getBytes(StandardCharsets.UTF_8));
    }
}
