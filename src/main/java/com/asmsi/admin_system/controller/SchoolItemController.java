package com.asmsi.admin_system.controller;

import com.asmsi.admin_system.dto.SchoolItemDetailDTO;
import com.asmsi.admin_system.entity.SchoolItem;
import com.asmsi.admin_system.repository.SchoolItemRepository;
import com.asmsi.admin_system.service.SchoolItemService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;


import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;


@Controller
public class SchoolItemController {

    private final SchoolItemRepository schoolItemRepository;
    private final SchoolItemService schoolItemService;

    public SchoolItemController(SchoolItemRepository schoolItemRepository,
                                SchoolItemService schoolItemService) {
        this.schoolItemRepository = schoolItemRepository;
        this.schoolItemService = schoolItemService;
    }

    // =========================================================
    // ✅ DATATABLES API (you already have)
    // =========================================================
    @GetMapping("/api/schoolitems/datatables")
    @ResponseBody
    public Map<String, Object> datatables(
            @RequestParam int draw,
            @RequestParam int start,
            @RequestParam int length,

            @RequestParam(value = "search[value]", required = false) String searchValue,

            @RequestParam(value = "order[0][column]", required = false, defaultValue = "0") int orderColumnIndex,
            @RequestParam(value = "order[0][dir]", required = false, defaultValue = "asc") String orderDir,

            @RequestParam(value = "filterShop", required = false) String filterShop,
            @RequestParam(value = "filterCategory", required = false) String filterCategory,
            @RequestParam(value = "filterStatus", required = false) String filterStatus
    ) {
        String orderColumn = switch (orderColumnIndex) {
            case 1 -> "itemCode";
            case 2 -> "itemName";
            case 3 -> "quantityOnHand";
            case 4 -> "shops";
            case 5 -> "status";
            default -> "id";
        };

        // call YOUR chosen datatables() method (keep consistent)
        return schoolItemService.datatables(
                draw, start, length,
                searchValue,
                orderColumn, orderDir,
                filterShop, filterCategory, filterStatus
        );
    }

    // =========================================================
    // ✅ PREVIEW DETAIL API
    // =========================================================
    @GetMapping("/api/schoolitems/{id}")
    @ResponseBody
    public SchoolItemDetailDTO detail(@PathVariable Long id) {
        return schoolItemService.getDetail(id);
    }

    // =========================================================
    // ✅ EXPORT FULL CSV (ALL DETAILS)
    // =========================================================
    @GetMapping("/schoolitems/export")
    public void exportAllItems(HttpServletResponse response) throws IOException {

        response.setContentType("text/csv");
        response.setHeader("Content-Disposition", "attachment; filename=school_items_full_export.csv");

        List<SchoolItem> items = schoolItemRepository.findAll();
        PrintWriter writer = response.getWriter();

        writer.println("id,imageUrl,itemCode,itemName,description,specification,brandModel,quantityOnHand,category,condition,status,supplier,shops,purchaseDate,purchaseCost");

        for (SchoolItem s : items) {
            writer.println(
                    safe(s.getId()) + "," +
                            safe(s.getImageUrl()) + "," +
                            safe(s.getItemCode()) + "," +
                            safe(s.getItemName()) + "," +
                            safe(s.getDescription()) + "," +
                            safe(s.getSpecification()) + "," +
                            safe(s.getBrandModel()) + "," +
                            safe(s.getQuantityOnHand()) + "," +
                            safe(s.getCategory()) + "," +
                            safe(s.getCondition()) + "," +
                            safe(s.getStatus()) + "," +
                            safe(s.getSupplier()) + "," +
                            safe(s.getShops()) + "," +
                            safe(s.getPurchaseDate()) + "," +
                            safe(s.getPurchaseCost())
            );
        }

        writer.flush();
        writer.close();
    }

    private String safe(Object obj) {
        if (obj == null) return "\"\"";
        String v = obj.toString().replace("\"", "\"\"");
        return "\"" + v + "\"";
    }

    // =========================================================
    // ✅ IMPORT CSV (BULK UPSERT)
    // =========================================================
  @PostMapping("/schoolitems/import")
@ResponseBody
public Map<String, Object> importCsv(
        @RequestParam("file") MultipartFile file,
        @RequestParam(value = "mode", defaultValue = "upsert") String mode
) {
    try {
        if ("strict".equalsIgnoreCase(mode)) {
            return schoolItemService.importCsvStrict(file);
        }
        return schoolItemService.importCsvUpsert(file); // default = bulk edit/upsert
    } catch (Exception e) {
        return Map.of(
                "ok", false,
                "message", "Import failed: " + e.getMessage(),
                "inserted", 0,
                "updated", 0,
                "success", 0,
                "skipped", 0,
                "rejected", 0,
                "errors", List.of()
        );
    }
}

@PostMapping("/schoolitems/import-strict")
@ResponseBody
public Map<String, Object> importStrict(@RequestParam("file") MultipartFile file) {
    try {
        return schoolItemService.importCsvStrict(file); // NEW METHOD
    } catch (Exception e) {
        return Map.of(
                "ok", false,
                "message", "Import failed: " + e.getMessage(),
                "inserted", 0,
                "updated", 0,
                "skipped", 0,
                "rejected", 0,
                "errors", List.of()
        );
    }
}

@PostMapping("/schoolitems/import-upsert")
@ResponseBody
public Map<String, Object> importUpsert(@RequestParam("file") MultipartFile file) {
    try {
        return schoolItemService.importCsvUpsert(file); // NEW METHOD
    } catch (Exception e) {
        return Map.of(
                "ok", false,
                "message", "Import failed: " + e.getMessage(),
                "inserted", 0,
                "updated", 0,
                "skipped", 0,
                "rejected", 0,
                "errors", List.of()
        );
    }
}




    // =========================================================
    // ✅ SAVE: Add / Edit
    // =========================================================
    @PostMapping("/schoolitems/save")
    public String saveSchoolItem(@ModelAttribute SchoolItem item) {

        if (item.getQuantityOnHand() == null) item.setQuantityOnHand(0);
        if (item.getPurchaseCost() == null) item.setPurchaseCost(BigDecimal.ZERO);

        if (item.getImageUrl() != null && item.getImageUrl().isBlank()) item.setImageUrl(null);

        schoolItemRepository.save(item);
        return "redirect:/schoolitems";
    }

    // =========================================================
    // ✅ DELETE
    // =========================================================
    @PostMapping("/schoolitems/delete/{id}")
    public String deleteSchoolItem(@PathVariable Long id) {
        schoolItemRepository.deleteById(id);
        return "redirect:/schoolitems";
    }
}
