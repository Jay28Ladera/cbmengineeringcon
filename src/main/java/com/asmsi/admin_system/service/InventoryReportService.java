package com.asmsi.admin_system.service;

import com.asmsi.admin_system.dto.InventoryMonthlyCellDto;
import com.asmsi.admin_system.entity.InventoryRecord;
import com.asmsi.admin_system.repository.InventoryRecordRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class InventoryReportService {

    private final InventoryRecordRepository inventoryRecordRepository;

    public InventoryReportService(InventoryRecordRepository inventoryRecordRepository) {
        this.inventoryRecordRepository = inventoryRecordRepository;
    }

    private Integer nz(Integer v) { return v == null ? 0 : v; }

    // ✅ key = schoolItemId (ALWAYS present), NOT itemCode
    public Map<Long, Map<Integer, InventoryMonthlyCellDto>> buildItemMonthLogs(int year, String shop) {

        LocalDate start = LocalDate.of(year, 1, 1);
        LocalDate end   = LocalDate.of(year, 12, 31);

        List<InventoryRecord> records = inventoryRecordRepository.findForYearAndShop(start, end, shop);

        Map<Long, Map<Integer, InventoryMonthlyCellDto>> out = new HashMap<>();

        for (InventoryRecord r : records) {
            if (r.getInventoryDate() == null) continue;
            if (r.getSchoolItemId() == null) continue;

            int month = r.getInventoryDate().getMonthValue();
            Long key = r.getSchoolItemId();

            out.putIfAbsent(key, new HashMap<>());
            Map<Integer, InventoryMonthlyCellDto> monthMap = out.get(key);

            InventoryMonthlyCellDto existing = monthMap.get(month);
            if (existing != null && existing.getInventoryDate() != null) {
                if (!r.getInventoryDate().isAfter(existing.getInventoryDate())) continue;
            }

            InventoryMonthlyCellDto dto = new InventoryMonthlyCellDto();
            dto.setInventoryDate(r.getInventoryDate());
            dto.setPreparedBy(r.getPreparedBy());

            dto.setNewlyAcquired(nz(r.getNewlyAcquired()));
            dto.setDefectiveRepair(nz(r.getDefectiveRepair()));
            dto.setLost(nz(r.getLost()));
            dto.setDiscarded(nz(r.getDiscarded()));
            dto.setAvailableItems(nz(r.getAvailableItems()));

            // ✅ notes
            dto.setNotes(r.getNotes());

            monthMap.put(month, dto);
        }

        return out;
    }
}
