package com.asmsi.admin_system.controller;

import com.asmsi.admin_system.repository.SchoolItemRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/schoolitems/inventory-wizard")
public class InventoryWizardController {

    private final SchoolItemRepository repo;

    public InventoryWizardController(SchoolItemRepository repo) {
        this.repo = repo;
    }

    @GetMapping("/ids")
    public Map<String, Object> ids(
            @RequestParam(required = false) String shop,         // locked shop (?shop=...)
            @RequestParam(required = false) String filterShop,   // dropdown
            @RequestParam(required = false) String filterCategory,
            @RequestParam(required = false) String filterStatus,
            @RequestParam(required = false) String search
    ) {

        // locked shop has priority
        String effectiveShop = norm(shop) != null ? norm(shop) : norm(filterShop);
String s = norm(search);
String searchLike = (s == null) ? null : "%" + s.toLowerCase() + "%";

List<Long> ids = repo.findIdsForInventoryWizard(
    effectiveShop,
    norm(filterCategory),
    norm(filterStatus),
    searchLike
);
        return Map.of("ok", true, "ids", ids);
    }

    private String norm(String s) {
        if (s == null) return null;
        s = s.trim();
        return s.isEmpty() ? null : s;
    }
}
