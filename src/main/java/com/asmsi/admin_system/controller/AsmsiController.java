package com.asmsi.admin_system.controller;

import com.asmsi.admin_system.entity.AsmsiAlumni;
import com.asmsi.admin_system.service.AsmsiAlumniService;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import com.asmsi.admin_system.dto.AsmsiRowDTO;

import jakarta.servlet.http.HttpServletRequest;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/asmsi")
public class AsmsiController {

    private final AsmsiAlumniService service;

    public AsmsiController(AsmsiAlumniService service) {
        this.service = service;
    }

    // ========= DataTables server-side endpoint =========
    // URL used by your HTML: ajax: { url: '/asmsi/data', type:'GET' }
    @GetMapping("/data")
    @ResponseBody
    public Map<String, Object> listData(HttpServletRequest request) {

        // DataTables parameters
        int draw   = parseIntOrDefault(request.getParameter("draw"), 1);
        int start  = parseIntOrDefault(request.getParameter("start"), 0);
        int length = parseIntOrDefault(request.getParameter("length"), 10);
        String searchValue = request.getParameter("search[value]");

        int page = start / length;

        // Get page from service
        Page<AsmsiAlumni> pageResult = service.findPage(searchValue, page, length);

        long totalCount    = service.countAll();
        long filteredCount = (searchValue == null || searchValue.isBlank())
                ? totalCount
                : pageResult.getTotalElements();

        // Map entities → DTOs for the DataTable
        List<AsmsiRowDTO> dtoList = pageResult.getContent().stream()
                .map(this::toRowDTO)
                .collect(Collectors.toList());

        Map<String, Object> json = new HashMap<>();
        json.put("draw", draw);
        json.put("recordsTotal", totalCount);
        json.put("recordsFiltered", filteredCount);
        json.put("data", dtoList);

        return json;
    }

    private int parseIntOrDefault(String val, int def) {
        try {
            return (val == null || val.isBlank()) ? def : Integer.parseInt(val);
        } catch (NumberFormatException ex) {
            return def;
        }
    }

    private AsmsiRowDTO toRowDTO(AsmsiAlumni a) {
        AsmsiRowDTO dto = new AsmsiRowDTO();
        dto.setId(a.getId());

        dto.setIdNumber(nvl(a.getIdNumber()));
        dto.setBatchNumber(nvl(a.getBatchNumber()));
        dto.setLastName(nvl(a.getLastName()));
        dto.setFirstName(nvl(a.getFirstName()));
        dto.setMiddleName(nvl(a.getMiddleName()));

        // OPTIONAL FIELDS – used by the right-side preview
        if (a.getDateOfBirth() != null) {
            dto.setDateOfBirth(a.getDateOfBirth().format(DateTimeFormatter.ISO_DATE));
        } else {
            dto.setDateOfBirth("");
        }

        dto.setAddress(nvl(a.getAddress()));
        dto.setFather(nvl(a.getFather()));
        dto.setMother(nvl(a.getMother()));
        dto.setHighestEducationalAttainment(nvl(a.getHighestEducationalAttainment()));
        dto.setEmployerName(nvl(a.getEmployerName()));
        dto.setSelfEmployed(nvl(a.getSelfEmployed()));
        dto.setEmployed(nvl(a.getEmployed()));
        dto.setContactNumber(nvl(a.getContactNumber()));
        dto.setEmail(nvl(a.getEmail()));
        dto.setFbAccount(nvl(a.getFbAccount()));

        // ⚠️ Adjust this depending on your entity field name
        // e.g. if you have getImagePath(), change to that.
        dto.setImageUrl(nvl(a.getImageUrl()));

        return dto;
    }

    private String nvl(String s) {
        return (s == null) ? "" : s;
    }

    // ========= Create =========
    @PostMapping("/add")
    public String add(AsmsiAlumni form,
                      @RequestParam(value = "imageFile", required = false) MultipartFile imageFile,
                      RedirectAttributes ra) {
        try {
            service.add(form, imageFile);
            ra.addFlashAttribute("success", "Alumni saved.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/asmsimanager";
    }

    // ========= Edit =========
    @PostMapping("/edit")
    public String edit(AsmsiAlumni form,
                       @RequestParam(value = "imageFile", required = false) MultipartFile imageFile,
                       RedirectAttributes ra) {
        try {
            service.update(form, imageFile);
            ra.addFlashAttribute("success", "Alumni updated.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/asmsimanager";
    }

    // ========= Delete =========
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

    // ========= CSV upload =========
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