package com.asmsi.admin_system.controller;

import com.asmsi.admin_system.entity.Faculty;
import com.asmsi.admin_system.service.FacultyService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/faculty")
public class FacultyCrudController {

    private final FacultyService facultyService;

    public FacultyCrudController(FacultyService facultyService) {
        this.facultyService = facultyService;
    }

    @PostMapping("/add")
    public String add(Faculty form,
                      @RequestParam(value = "imageFile", required = false) MultipartFile image,
                      RedirectAttributes ra) {
        try {
            facultyService.add(form, image);
            ra.addFlashAttribute("success", "Faculty saved.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/facultymanager";
    }

    @PostMapping("/edit")
    public String edit(Faculty form,
                       @RequestParam(value = "imageFile", required = false) MultipartFile image,
                       RedirectAttributes ra) {
        try {
            facultyService.update(form, image);
            ra.addFlashAttribute("success", "Faculty updated.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/facultymanager";
    }

    @PostMapping("/delete")
    public String delete(@RequestParam("id") Long id, RedirectAttributes ra) {
        try {
            facultyService.delete(id);
            ra.addFlashAttribute("success", "Faculty deleted.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/facultymanager";
    }

    @PostMapping("/upload-csv")
    public String uploadCsv(@RequestParam("file") MultipartFile file, RedirectAttributes ra) {
        try {
            int n = facultyService.importCsv(file);
            ra.addFlashAttribute("success", "CSV processed. Upserts: " + n);
        } catch (Exception e) {
            ra.addFlashAttribute("error", "CSV error: " + e.getMessage());
        }
        return "redirect:/facultymanager";
    }
}
