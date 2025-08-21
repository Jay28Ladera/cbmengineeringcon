// src/main/java/com/asmsi/admin_system/controller/NtsCrudController.java
package com.asmsi.admin_system.controller;

import com.asmsi.admin_system.entity.NonTeachingStaff;
import com.asmsi.admin_system.service.NtsService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/nts")
public class NtsCrudController {

    private final NtsService ntsService;

    public NtsCrudController(NtsService ntsService) {
        this.ntsService = ntsService;
    }

    @PostMapping("/add")
    public String add(NonTeachingStaff form,
                      @RequestParam(value = "imageFile", required = false) MultipartFile image,
                      RedirectAttributes ra) {
        try {
            ntsService.add(form, image);
            ra.addFlashAttribute("success", "Staff saved.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/ntsmanager";
    }

    @PostMapping("/edit")
    public String edit(NonTeachingStaff form,
                       @RequestParam(value = "imageFile", required = false) MultipartFile image,
                       RedirectAttributes ra) {
        try {
            ntsService.update(form, image);
            ra.addFlashAttribute("success", "Staff updated.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/ntsmanager";
    }

    @PostMapping("/delete")
    public String delete(@RequestParam("id") Long id, RedirectAttributes ra) {
        try {
            ntsService.delete(id);
            ra.addFlashAttribute("success", "Staff deleted.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/ntsmanager";
    }

    @PostMapping("/upload-csv")
    public String uploadCsv(@RequestParam("file") MultipartFile file, RedirectAttributes ra) {
        try {
            int n = ntsService.importCsv(file);
            ra.addFlashAttribute("success", "CSV processed. Upserts: " + n);
        } catch (Exception e) {
            ra.addFlashAttribute("error", "CSV error: " + e.getMessage());
        }
        return "redirect:/ntsmanager";
    }
}
