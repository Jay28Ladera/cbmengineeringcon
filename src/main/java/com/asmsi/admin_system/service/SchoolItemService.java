package com.asmsi.admin_system.service;

import com.asmsi.admin_system.dto.CsvRowError;
import com.asmsi.admin_system.dto.SchoolItemDetailDTO;
import com.asmsi.admin_system.entity.SchoolItem;
import com.asmsi.admin_system.repository.SchoolItemRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;

@Service
public class SchoolItemService {

    private final SchoolItemRepository repo;
    private enum ImportMode { STRICT, UPSERT }

    public SchoolItemService(SchoolItemRepository repo) {
        this.repo = repo;
    }

    // =========================================================
    // ✅ Two public entry points (same import logic)
    // =========================================================
    @Transactional
    public Map<String, Object> importCsvStrict(MultipartFile file) throws IOException {
        return importCsvInternal(file, ImportMode.STRICT);
    }

    @Transactional
    public Map<String, Object> importCsvUpsert(MultipartFile file) throws IOException {
        return importCsvInternal(file, ImportMode.UPSERT);
    }

    // =========================================================
    // ✅ DataTables (server-side) — MATCHES YOUR CONTROLLER SIGNATURE
    // =========================================================
    @Transactional(readOnly = true)
    public Map<String, Object> datatables(
            int draw,
            int start,
            int length,
            String searchValue,
            String orderColumn,
            String orderDir,
            String filterShop,
            String filterCategory,
            String filterStatus
    ) {

        int safeLength = (length <= 0) ? 10 : length;
        int page = start / safeLength;

        String sortField = switch (orderColumn) {
            case "itemCode" -> "itemCode";
            case "itemName" -> "itemName";
            case "quantityOnHand" -> "quantityOnHand";
            case "shops" -> "shops";
            case "status" -> "status";
            default -> "id";
        };

        Sort sort = "desc".equalsIgnoreCase(orderDir)
                ? Sort.by(sortField).descending()
                : Sort.by(sortField).ascending();

        Pageable pageable = PageRequest.of(page, safeLength, sort);

        String q = (searchValue == null) ? "" : searchValue.trim();

        Page<SchoolItem> result = repo.findForDataTable(
                q,
                normalize(filterShop),
                normalize(filterCategory),
                normalize(filterStatus),
                pageable
        );

        List<Map<String, Object>> rows = new ArrayList<>();
        for (SchoolItem s : result.getContent()) {
            Map<String, Object> r = new HashMap<>();
            r.put("id", s.getId());
            r.put("imageUrl", s.getImageUrl());
            r.put("itemCode", s.getItemCode());
            r.put("itemName", s.getItemName());
            r.put("quantityOnHand", s.getQuantityOnHand());
            r.put("shops", s.getShops());
            r.put("status", s.getStatus());
            rows.add(r);
        }

        Map<String, Object> out = new HashMap<>();
        out.put("draw", draw);
        out.put("recordsTotal", repo.count());
        out.put("recordsFiltered", result.getTotalElements());
        out.put("data", rows);
        return out;
    }

    private String normalize(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    // =========================================================
    // ✅ Detail for preview card
    // =========================================================
    @Transactional(readOnly = true)
    public SchoolItemDetailDTO getDetail(Long id) {
        return repo.findDetailById(id)
                .orElseThrow(() -> new RuntimeException("School item not found: " + id));
    }

    // =========================================================
    // ✅ IMPORT CSV
    //   - UPSERT: updates if itemCode exists, inserts if not
    //   - STRICT: rejects rows if itemCode already exists in DB
    //   - ALSO rejects duplicate itemCode inside the same CSV
    // =========================================================
    @Transactional
    private Map<String, Object> importCsvInternal(MultipartFile file, ImportMode mode) throws IOException {

        if (file == null || file.isEmpty()) {
            return Map.of("ok", false, "message", "No file uploaded.");
        }

        String filename = Optional.ofNullable(file.getOriginalFilename()).orElse("").toLowerCase();
        if (!filename.endsWith(".csv")) {
            return Map.of("ok", false, "message", "Please upload a .csv file.");
        }

        int inserted = 0;
        int updated = 0;
        int skipped = 0;
        int rejected = 0;

        List<CsvRowError> errors = new ArrayList<>();
        Set<String> seenCodesInFile = new HashSet<>();

        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8)
        )) {
            String headerLine = br.readLine();
            if (headerLine == null) {
                return Map.of("ok", false, "message", "CSV is empty.");
            }

            // Read header
            List<String> header = parseCsvLine(headerLine);
            Map<String, Integer> col = new HashMap<>();
            for (int i = 0; i < header.size(); i++) {
                col.put(clean(header.get(i)).toLowerCase(), i);
            }

            // REQUIRED columns
            if (!col.containsKey("itemcode") && !col.containsKey("item_code") && !col.containsKey("item code")) {
                return Map.of("ok", false, "message", "Missing required column: itemCode");
            }

            String line;
            int rowNum = 1; // header = 1

            while ((line = br.readLine()) != null) {
                rowNum++;

                if (line.trim().isEmpty()) {
                    skipped++;
                    continue;
                }

                List<String> vals = parseCsvLine(line);

                // -----------------------------
                // 1) Required fields validation
                // -----------------------------
                String itemCode = clean(getCol(vals, col, "itemcode", "item_code", "item code"));
                if (itemCode.isEmpty()) {
                    rejected++;
                    errors.add(new CsvRowError(rowNum, "itemCode", "", "Missing required itemCode"));
                    continue;
                }

                // Duplicate inside CSV file (case-insensitive)
                String normalizedCode = itemCode.trim().toLowerCase();
                if (!seenCodesInFile.add(normalizedCode)) {
                    rejected++;
                    errors.add(new CsvRowError(rowNum, "itemCode", itemCode, "Duplicate itemCode found inside CSV file"));
                    continue;
                }

                String itemName = clean(getCol(vals, col, "itemname", "item_name", "item name"));
                if (itemName.isEmpty()) {
                    rejected++;
                    errors.add(new CsvRowError(rowNum, "itemName", "", "Missing required itemName"));
                    continue;
                }

                String shops = clean(getCol(vals, col, "shops", "shop"));
                if (shops.isEmpty()) {
                    rejected++;
                    errors.add(new CsvRowError(rowNum, "shops", "", "Missing required shops"));
                    continue;
                }

                // quantityOnHand validation (if provided, must be integer)
                String qtyRaw = clean(getCol(vals, col, "quantityonhand", "quantity_on_hand", "qty on hand", "quantity"));
                Integer qtyParsed = parseIntSafe(qtyRaw);
                if (qtyRaw != null && !qtyRaw.isBlank() && qtyParsed == null) {
                    rejected++;
                    errors.add(new CsvRowError(rowNum, "quantityOnHand", qtyRaw, "Invalid integer quantity"));
                    continue;
                }

                // -----------------------------
                // 2) STRICT/UPSERT behavior
                // -----------------------------
                Optional<SchoolItem> existingOpt = repo.findByItemCodeIgnoreCase(itemCode);

                // STRICT: if already exists in DB -> reject (do NOT update)
                if (mode == ImportMode.STRICT && existingOpt.isPresent()) {
                    rejected++;
                    errors.add(new CsvRowError(
                            rowNum,
                            "itemCode",
                            itemCode,
                            "Duplicate itemCode already exists (STRICT import: not allowed)"
                    ));
                    continue;
                }

                // Choose entity:
                // - UPSERT: update existing OR create new
                // - STRICT: always create new
                SchoolItem item = (mode == ImportMode.UPSERT)
                        ? existingOpt.orElseGet(SchoolItem::new)
                        : new SchoolItem();

                boolean isNew = (item.getId() == null);

                // -----------------------------
                // 3) Map fields
                // -----------------------------
                item.setItemCode(itemCode);

                item.setImageUrl(clean(getCol(vals, col, "imageurl", "image_url", "image url")));
                item.setItemName(itemName); // validated
                item.setDescription(clean(getCol(vals, col, "description")));
                item.setSpecification(clean(getCol(vals, col, "specification")));
                item.setBrandModel(clean(getCol(vals, col, "brandmodel", "brand_model", "brand / model", "brand")));
                item.setCategory(clean(getCol(vals, col, "category")));
                item.setCondition(clean(getCol(vals, col, "condition")));
                item.setStatus(clean(getCol(vals, col, "status")));
                item.setSupplier(clean(getCol(vals, col, "supplier")));
                item.setShops(shops); // validated

                // Qty
                Integer qty = qtyParsed;
                item.setQuantityOnHand(qty == null ? 0 : qty);

                // purchaseDate (expects yyyy-MM-dd)
                String pd = clean(getCol(vals, col, "purchasedate", "purchase_date", "purchase date"));
                item.setPurchaseDate(parseDateSafe(pd));

                // purchaseCost
                BigDecimal cost = parseDecimalSafe(clean(getCol(vals, col, "purchasecost", "purchase_cost", "purchase cost")));
                item.setPurchaseCost(cost == null ? BigDecimal.ZERO : cost);

                // -----------------------------
                // 4) Save + counts
                // -----------------------------
                try {
                    repo.save(item);

                    if (isNew) inserted++;
                    else updated++;

                } catch (DataIntegrityViolationException ex) {
                    // If DB unique constraint triggers (race / unexpected)
                    rejected++;
                    errors.add(new CsvRowError(
                            rowNum,
                            "itemCode",
                            itemCode,
                            "Duplicate itemCode already exists (unique constraint)"
                    ));
                }
            }
        }

        int success = inserted + updated;

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("ok", true);
        resp.put("mode", mode.name().toLowerCase()); // optional: helps frontend show mode
        resp.put("inserted", inserted);
        resp.put("updated", updated);
        resp.put("success", success);
        resp.put("skipped", skipped);
        resp.put("rejected", rejected);
        resp.put("errors", errors);
        resp.put("message", "Import finished.");
        return resp;
    }

    // ----------------- helpers -----------------

    private static String getCol(List<String> vals, Map<String, Integer> col, String... keys) {
        for (String k : keys) {
            Integer idx = col.get(k.toLowerCase());
            if (idx != null && idx >= 0 && idx < vals.size()) return vals.get(idx);
        }
        return "";
    }

    private static String clean(String s) {
        if (s == null) return "";
        String t = s.trim();
        if (t.length() >= 2 && t.startsWith("\"") && t.endsWith("\"")) {
            t = t.substring(1, t.length() - 1);
        }
        return t.replace("\uFEFF", "").trim(); // remove BOM if present
    }

    private static Integer parseIntSafe(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return null;
        }
    }

    private static BigDecimal parseDecimalSafe(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return new BigDecimal(s.trim().replace(",", ""));
        } catch (Exception e) {
            return null;
        }
    }

    private static LocalDate parseDateSafe(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return LocalDate.parse(s.trim()); // yyyy-MM-dd
        } catch (Exception e) {
            return null;
        }
    }

    // CSV parser that supports commas inside quotes
    private static List<String> parseCsvLine(String line) {
        List<String> out = new ArrayList<>();
        if (line == null) return out;

        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);

            if (c == '"') {
                // Handle escaped quote ""
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    cur.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                out.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        out.add(cur.toString());
        return out;
    }
}
