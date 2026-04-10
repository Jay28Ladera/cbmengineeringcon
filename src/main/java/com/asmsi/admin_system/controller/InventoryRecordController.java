package com.asmsi.admin_system.controller;

import com.asmsi.admin_system.entity.InventoryRecord;
import com.asmsi.admin_system.entity.SchoolItem;
import com.asmsi.admin_system.repository.InventoryRecordRepository;
import com.asmsi.admin_system.repository.SchoolItemRepository;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/schoolitems/inventory-wizard")
public class InventoryRecordController {

    private final InventoryRecordRepository recordRepo;
    private final SchoolItemRepository schoolItemRepo;

    public InventoryRecordController(InventoryRecordRepository recordRepo,
                                     SchoolItemRepository schoolItemRepo) {
        this.recordRepo = recordRepo;
        this.schoolItemRepo = schoolItemRepo;
    }

    @PostMapping("/save")
    public Map<String, Object> save(@RequestBody Map<String, Object> body) {

        LocalDate inventoryDate = LocalDate.parse(String.valueOf(body.get("inventoryDate")));
        String preparedBy = String.valueOf(body.get("preparedBy")).trim();
        String managerName = String.valueOf(body.get("managerName")).trim();

        Long itemId = Long.valueOf(String.valueOf(body.get("itemId")));

        SchoolItem item = schoolItemRepo.findById(itemId)
                .orElseThrow(() -> new IllegalArgumentException("Item not found: " + itemId));

        InventoryRecord rec = recordRepo
                .findByInventoryDateAndPreparedByAndManagerNameAndSchoolItemId(
                        inventoryDate, preparedBy, managerName, itemId
                )
                .orElseGet(InventoryRecord::new);

        // Modal 1 fields
        rec.setInventoryDate(inventoryDate);
        rec.setPreparedBy(preparedBy);
        rec.setManagerName(managerName);
        rec.setShopScope(str(body.get("shopScope")));
        rec.setCategoryFilter(str(body.get("categoryFilter")));
        rec.setStatusFilter(str(body.get("statusFilter")));
        rec.setSearchTerm(str(body.get("searchTerm")));

        // Item snapshot
        rec.setSchoolItemId(item.getId());
        rec.setItemCode(item.getItemCode());
        rec.setItemName(item.getItemName());
        rec.setShops(item.getShops());
        rec.setQtyOnHand(item.getQuantityOnHand());

        // Modal 2 fields
        rec.setNewlyAcquired(int0(body.get("newlyAcquired")));
        rec.setDefectiveRepair(int0(body.get("defectiveRepair")));
        rec.setLost(int0(body.get("lost")));
        rec.setDiscarded(int0(body.get("discarded")));
        rec.setAvailableItems(int0(body.get("availableItems")));
        rec.setNotes(str(body.get("notes")));

        recordRepo.save(rec);

        return Map.of("ok", true);
    }

    private String str(Object o) {
        if (o == null) return null;
        String s = String.valueOf(o).trim();
        return s.isEmpty() ? null : s;
    }

    private Integer int0(Object o) {
        if (o == null) return 0;
        try { return Integer.parseInt(String.valueOf(o)); }
        catch (Exception e) { return 0; }
    }
}
