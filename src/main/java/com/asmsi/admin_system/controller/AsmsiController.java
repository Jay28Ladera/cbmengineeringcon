package com.asmsi.admin_system.controller;

import com.asmsi.admin_system.entity.AsmsiAlumni;
import com.asmsi.admin_system.service.AsmsiAlumniService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/asmsi")
public class AsmsiController {

    private final AsmsiAlumniService service;

    public AsmsiController(AsmsiAlumniService service) { this.service = service; }

    // Create
   @PostMapping("/add")
public String add(AsmsiAlumni form,
                  @RequestParam(value="imageFile", required=false) MultipartFile imageFile,
                  RedirectAttributes ra) {
    try {
        service.add(form, imageFile);
        ra.addFlashAttribute("success", "Alumni saved.");
    } catch (Exception e) {
        ra.addFlashAttribute("error", e.getMessage());
    }
    return "redirect:/asmsimanager";
}

@PostMapping("/edit")
public String edit(AsmsiAlumni form,
                   @RequestParam(value="imageFile", required=false) MultipartFile imageFile,
                   RedirectAttributes ra) {
    try {
        service.update(form, imageFile);
        ra.addFlashAttribute("success", "Alumni updated.");
    } catch (Exception e) {
        ra.addFlashAttribute("error", e.getMessage());
    }
    return "redirect:/asmsimanager";
}
    // Delete
    @PostMapping("/delete")
    public String delete(@RequestParam("id") Long id, RedirectAttributes ra) {
        try {
            service.delete(id);
            ra.addFlashAttribute("success", "Alumni deleted.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/asmsimanager";
    }

    // CSV upload
    @PostMapping("/upload-csv")
    public String uploadCsv(@RequestParam("file") MultipartFile file, RedirectAttributes ra) {
        try {
            int n = service.importCsv(file);
            ra.addFlashAttribute("success", "CSV processed. Upserts: " + n);
        } catch (Exception e) {
            ra.addFlashAttribute("error", "CSV error: " + e.getMessage());
        }
        return "redirect:/asmsimanager";
    }
}
