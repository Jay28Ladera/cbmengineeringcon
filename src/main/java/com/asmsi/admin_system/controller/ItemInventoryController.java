package com.asmsi.admin_system.controller;

import com.asmsi.admin_system.dto.InventoryEntryDto;
import com.asmsi.admin_system.dto.InventorySessionDto;
import com.asmsi.admin_system.entity.InventoryLog;
import com.asmsi.admin_system.entity.SchoolItem;
import com.asmsi.admin_system.entity.Shop;
import com.asmsi.admin_system.repository.InventoryLogRepository;
import com.asmsi.admin_system.repository.SchoolItemRepository;
import com.asmsi.admin_system.repository.ShopRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Set;
import java.util.Map;
import java.util.stream.Collectors;







import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;


@Controller
public class ItemInventoryController {

    @Autowired
    private SchoolItemRepository schoolItemRepository;

    @Autowired
    private ShopRepository shopRepository;

    @Autowired
    private InventoryLogRepository inventoryLogRepository;

    // --------------------------------------------------
    // PAGE: School Items Inventory
    // --------------------------------------------------
    @GetMapping("/schoolitems")
    public String showSchoolItemsPage(
            @RequestParam(value = "shop", required = false) String shop,
            Model model) {

        // ------- auth / header -------
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth != null && auth.isAuthenticated()) {
            String username = auth.getName();
            String role = auth.getAuthorities().stream()
                    .findFirst()
                    .map(Object::toString)
                    .orElse("ROLE_USER");

            model.addAttribute("username", username);
            model.addAttribute("role", role);
        } else {
            model.addAttribute("username", "Guest");
            model.addAttribute("role", "Unknown");
        }

        // ------- items (filtered in Java instead of findByShops) -------
        List<SchoolItem> items = schoolItemRepository.findAll();

        if (shop != null && !shop.isBlank()) {
            String shopTrimmed = shop.trim();

            items = items.stream()
                    .filter(i -> {
                        String s = i.getShops();   // field name in SchoolItem
                        if (s == null) return false;
                        return shopTrimmed.equalsIgnoreCase(s.trim());
                    })
                    .collect(Collectors.toList());
        }

        // all shops for dropdown in schoolitems.html
        List<Shop> allShops = shopRepository.findAll();

        model.addAttribute("items", items);
        model.addAttribute("allShops", allShops);
        model.addAttribute("selectedShop", shop);   // optional if you want it
        model.addAttribute("activePage", "inventory-school-items");

        return "schoolitems";
    }

    // --------------------------------------------------
    // API: Save Physical Inventory Session
    // URL: POST /schoolitems/inventory/save
    // --------------------------------------------------
    @PostMapping("/schoolitems/inventory/save")
    @ResponseBody
    public ResponseEntity<?> saveInventory(@RequestBody InventorySessionDto payload) {
        if (payload.getEntries() == null || payload.getEntries().isEmpty()) {
            return ResponseEntity.badRequest().body("No entries to save.");
        }

        LocalDate sessionDate = null;
        try {
            if (payload.getSessionDate() != null && !payload.getSessionDate().isBlank()) {
                sessionDate = LocalDate.parse(payload.getSessionDate());
            }
        } catch (Exception ignored) { }

        List<InventoryLog> logs = new ArrayList<>();
        for (InventoryEntryDto e : payload.getEntries()) {
            InventoryLog log = new InventoryLog();
            log.setSessionDate(sessionDate);
            log.setInCharge(payload.getInCharge());
            log.setWitnesses(payload.getWitnesses());
            log.setItemCode(e.getItemCode());
            log.setItemName(e.getItemName());
            log.setQuantity(e.getQuantity());
            log.setCondition(e.getCondition());
            log.setStatus(e.getStatus());
            log.setRemarks(e.getRemarks());
            log.setLoggedAt(LocalDateTime.now());
            logs.add(log);
        }

        inventoryLogRepository.saveAll(logs);

        return ResponseEntity.ok().build();
    }

    // --------------------------------------------------
// PAGE: Inventory Report (logs) with Year filter
// URL: /schoolitems/inventory/report
// --------------------------------------------------
@GetMapping("/schoolitems/inventory/report")
public String showInventoryReport(
        @RequestParam(value = "year", required = false) Integer year,
        Model model) {

    // ------- auth / header -------
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth != null && auth.isAuthenticated()) {
        String username = auth.getName();
        String role = auth.getAuthorities().stream()
                .findFirst()
                .map(Object::toString)
                .orElse("ROLE_USER");

        model.addAttribute("username", username);
        model.addAttribute("role", role);
    } else {
        model.addAttribute("username", "Guest");
        model.addAttribute("role", "Unknown");
    }

    int currentYear = LocalDate.now().getYear();
    int selectedYear = (year != null) ? year : currentYear;

    LocalDate start = LocalDate.of(selectedYear, 1, 1);
    LocalDate end   = LocalDate.of(selectedYear, 12, 31);

    // all items (rows)
    List<SchoolItem> items = schoolItemRepository.findAll();

    // all logs for that year
    List<InventoryLog> logs =
            inventoryLogRepository.findBySessionDateBetween(start, end);

    // Map<itemCode, Map<month(1-12), InventoryLog>>
    Map<String, Map<Integer, InventoryLog>> itemMonthLogs = new HashMap<>();

    for (InventoryLog log : logs) {
        if (log.getItemCode() == null || log.getSessionDate() == null) {
            continue;
        }
        String code = log.getItemCode().trim();
        int month = log.getSessionDate().getMonthValue(); // 1..12

        Map<Integer, InventoryLog> monthMap =
                itemMonthLogs.computeIfAbsent(code, c -> new HashMap<>());

        InventoryLog existing = monthMap.get(month);
        if (existing == null) {
            monthMap.put(month, log);
        } else {
            // keep the *latest* log for that month
            LocalDateTime existingTime = existing.getLoggedAt();
            LocalDateTime newTime = log.getLoggedAt();
            if (existingTime == null || (newTime != null && newTime.isAfter(existingTime))) {
                monthMap.put(month, log);
            }
        }
    }

    // Years for dropdown (based on logs)
    Set<Integer> years = inventoryLogRepository.findAll().stream()
            .filter(l -> l.getSessionDate() != null)
            .map(l -> l.getSessionDate().getYear())
            .collect(Collectors.toSet());
    years.add(currentYear); // ensure current year is present

    List<Integer> yearList = years.stream()
            .sorted(Comparator.reverseOrder())
            .collect(Collectors.toList());

    model.addAttribute("items", items);
    model.addAttribute("itemMonthLogs", itemMonthLogs);
    model.addAttribute("years", yearList);
    model.addAttribute("selectedYear", selectedYear);
    model.addAttribute("activePage", "inventory-report");

    return "schoolitems-inventory-report";
}

}
