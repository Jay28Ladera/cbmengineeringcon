package com.asmsi.admin_system.controller;

import com.asmsi.admin_system.dto.InventoryEntryDto;
import com.asmsi.admin_system.dto.InventorySessionDto;
import com.asmsi.admin_system.entity.InventoryLog;
import com.asmsi.admin_system.entity.SchoolItem;
import com.asmsi.admin_system.entity.Shop;
import com.asmsi.admin_system.repository.InventoryLogRepository;
import com.asmsi.admin_system.repository.SchoolItemRepository;
import com.asmsi.admin_system.repository.ShopRepository;
import com.google.zxing.BarcodeFormat;

import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.oned.Code128Writer;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URL;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Controller
public class ItemInventoryController {

    @Autowired
    private SchoolItemRepository schoolItemRepository;

    @Autowired
    private ShopRepository shopRepository;

    @Autowired
    private InventoryLogRepository inventoryLogRepository;

    // --------------------------------------------------
    // PAGE: School Items Inventory (FAST LOAD)
    // --------------------------------------------------
    @GetMapping("/schoolitems")
    public String showSchoolItemsPage(
            @RequestParam(value = "shop", required = false) String shop,
            Model model) {

        // ------- auth / header -------
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth != null && auth.isAuthenticated()) {
            model.addAttribute("username", auth.getName());
            model.addAttribute("role", auth.getAuthorities().stream()
                    .findFirst().map(Object::toString).orElse("ROLE_USER"));
        } else {
            model.addAttribute("username", "Guest");
            model.addAttribute("role", "Unknown");
        }

        // Only load shops (small data)
        List<Shop> allShops = shopRepository.findAll();

        model.addAttribute("allShops", allShops);
        model.addAttribute("selectedShop", shop);
        model.addAttribute("activePage", "inventory-school-items");

        // DO NOT load items here → page loads instantly
        return "schoolitems";
    }

    // --------------------------------------------------
    // API: AJAX fetch of items (Slow part moved here)
    // --------------------------------------------------
    @GetMapping("/schoolitems/data")
    @ResponseBody
    public List<SchoolItem> getSchoolItemsData(
            @RequestParam(value = "shop", required = false) String shop) {

        if (shop != null && !shop.isBlank()) {
            return schoolItemRepository.findByShopsContainingIgnoreCase(shop.trim());
        }

        return schoolItemRepository.findAll();
    }

    // --------------------------------------------------
    // API: Save Physical Inventory Session
    // (UNCHANGED)
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
        } catch (Exception ignored) {}

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
    // PAGE: Inventory Report (UNCHANGED)
    // --------------------------------------------------
    @GetMapping("/schoolitems/inventory/report")
    public String showInventoryReport(
            @RequestParam(value = "year", required = false) Integer year,
            @RequestParam(value = "shop", required = false) String shop,
            Model model) {

        // AUTH + BODY (same as your version, I did not modify)
        // --------------------------------------------------

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()) {
            model.addAttribute("username", auth.getName());
            model.addAttribute("role", auth.getAuthorities().stream()
                    .findFirst().map(Object::toString).orElse("ROLE_USER"));
        } else {
            model.addAttribute("username", "Guest");
            model.addAttribute("role", "Unknown");
        }

        int currentYear = LocalDate.now().getYear();
        int selectedYear = (year != null) ? year : currentYear;

        LocalDate start = LocalDate.of(selectedYear, 1, 1);
        LocalDate end = LocalDate.of(selectedYear, 12, 31);

        List<SchoolItem> items = schoolItemRepository.findAll();

        if (shop != null && !shop.isBlank()) {
            String s = shop.trim();
            items = items.stream()
                    .filter(i -> s.equalsIgnoreCase(Optional.ofNullable(i.getShops()).orElse("").trim()))
                    .collect(Collectors.toList());
        }

        List<InventoryLog> logs = inventoryLogRepository.findBySessionDateBetween(start, end);

        if (shop != null && !shop.isBlank()) {
            Set<String> validCodes = items.stream()
                    .map(SchoolItem::getItemCode)
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .collect(Collectors.toSet());

            logs = logs.stream()
                    .filter(l -> validCodes.contains(Optional.ofNullable(l.getItemCode()).orElse("").trim()))
                    .collect(Collectors.toList());
        }

        Map<String, Map<Integer, InventoryLog>> itemMonthLogs = new HashMap<>();

        for (InventoryLog log : logs) {
            if (log.getItemCode() == null || log.getSessionDate() == null) continue;

            String code = log.getItemCode().trim();
            int month = log.getSessionDate().getMonthValue();

            Map<Integer, InventoryLog> monthMap =
                    itemMonthLogs.computeIfAbsent(code, c -> new HashMap<>());

            InventoryLog existing = monthMap.get(month);
            if (existing == null ||
                    (log.getLoggedAt() != null &&
                            (existing.getLoggedAt() == null ||
                                    log.getLoggedAt().isAfter(existing.getLoggedAt())))) {
                monthMap.put(month, log);
            }
        }

        Set<Integer> years = inventoryLogRepository.findAll().stream()
                .filter(l -> l.getSessionDate() != null)
                .map(l -> l.getSessionDate().getYear())
                .collect(Collectors.toSet());
        years.add(currentYear);

        List<Integer> yearList = years.stream()
                .sorted(Comparator.reverseOrder())
                .collect(Collectors.toList());

        List<Shop> allShops = shopRepository.findAll();

        model.addAttribute("items", items);
        model.addAttribute("itemMonthLogs", itemMonthLogs);
        model.addAttribute("years", yearList);
        model.addAttribute("selectedYear", selectedYear);
        model.addAttribute("selectedShop", shop);
        model.addAttribute("allShops", allShops);
        model.addAttribute("activePage", "inventory-report");

        return "schoolitems-inventory-report";
    }

    // --------------------------------------------------
    // BARCODE PNG (UNCHANGED)
    // --------------------------------------------------
   @GetMapping(
        value = "/schoolitems/barcode/{id}",
        produces = org.springframework.http.MediaType.IMAGE_PNG_VALUE
)
public void downloadItemBarcodePng(@PathVariable Long id,
                                   HttpServletResponse response) throws IOException {

    SchoolItem item = schoolItemRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

    String itemCode = item.getItemCode() != null ? item.getItemCode().trim() : "";
    if (itemCode.isEmpty()) {
        throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Item has no item code for barcode."
        );
    }

    // === Card size (landscape calling-card style) ===
    int width = 700;   // pixels
    int height = 350;  // pixels

    BufferedImage card = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = card.createGraphics();

    try {
        // --- Rendering quality ---
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // --- Background ---
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, width, height);

        // light border
        g.setColor(new Color(0xDDDDDD));
        g.fillRoundRect(5, 5, width - 10, height - 10, 25, 25);

        // inner white area
        g.setColor(Color.WHITE);
        g.fillRoundRect(12, 12, width - 24, height - 24, 20, 20);

        int paddingLeft = 30;
        int paddingTop = 30;

        // === LEFT SIDE: picture ===
        int pictureBoxWidth = 220;
        int pictureBoxHeight = 220;

        BufferedImage itemImage = null;
        try {
            if (item.getImageUrl() != null && !item.getImageUrl().isBlank()) {
                itemImage = ImageIO.read(new URL(item.getImageUrl()));
            }
        } catch (Exception ignored) {
        }

        if (itemImage != null) {
            double scale = Math.min(
                    (double) pictureBoxWidth / itemImage.getWidth(),
                    (double) pictureBoxHeight / itemImage.getHeight());
            int imgW = (int) (itemImage.getWidth() * scale);
            int imgH = (int) (itemImage.getHeight() * scale);
            int imgX = paddingLeft + (pictureBoxWidth - imgW) / 2;
            int imgY = paddingTop + (pictureBoxHeight - imgH) / 2;
            g.drawImage(itemImage, imgX, imgY, imgW, imgH, null);
        } else {
            g.setColor(new Color(0xE5E7EB));
            g.fillRoundRect(paddingLeft, paddingTop, pictureBoxWidth, pictureBoxHeight, 18, 18);
            g.setColor(new Color(0x9CA3AF));
            g.setFont(new Font("SansSerif", Font.BOLD, 14));
            String noImg = "NO IMAGE";
            int textW = g.getFontMetrics().stringWidth(noImg);
            int textX = paddingLeft + (pictureBoxWidth - textW) / 2;
            int textY = paddingTop + pictureBoxHeight / 2;
            g.drawString(noImg, textX, textY);
        }

        // === RIGHT SIDE: text + barcode ===
        int rightX = paddingLeft + pictureBoxWidth + 30;

        String name = item.getItemName() != null ? item.getItemName() : "-";
        String brand = item.getBrandModel() != null ? item.getBrandModel() : "-";
        int qty = item.getQuantityOnHand() != null ? item.getQuantityOnHand() : 0;

        g.setColor(new Color(0x111827));
        g.setFont(new Font("SansSerif", Font.BOLD, 22));
        g.drawString(name, rightX, paddingTop + 25);

        g.setFont(new Font("SansSerif", Font.PLAIN, 14));
        int textY = paddingTop + 55;
        int lineGap = 20;

        g.setColor(new Color(0x374151));
        g.drawString("Item code: " + itemCode, rightX, textY);
        textY += lineGap;
        g.drawString("Brand: " + brand, rightX, textY);
        textY += lineGap;
        g.drawString("Qty on hand: " + qty, rightX, textY);
        textY += lineGap + 10;

        // === Barcode (Code128) ===
        try {
            int barcodeWidth = 350;
            int barcodeHeight = 70;

            Code128Writer writer = new Code128Writer();
            BitMatrix matrix = writer.encode(
                    itemCode,
                    BarcodeFormat.CODE_128,
                    barcodeWidth,
                    barcodeHeight
            );
            BufferedImage barcodeImage = MatrixToImageWriter.toBufferedImage(matrix);

            int barcodeX = rightX;
            int barcodeY = textY;

            g.drawImage(barcodeImage, barcodeX, barcodeY, barcodeWidth, barcodeHeight, null);

            g.setFont(new Font("SansSerif", Font.PLAIN, 14));
            int codeTextY = barcodeY + barcodeHeight + 20;
            int codeTextWidth = g.getFontMetrics().stringWidth(itemCode);
            int codeTextX = barcodeX + (barcodeWidth - codeTextWidth) / 2;
            g.drawString(itemCode, codeTextX, codeTextY);

        } catch (Exception e) {
            g.setColor(Color.RED);
            g.setFont(new Font("SansSerif", Font.PLAIN, 14));
            g.drawString("Barcode error", rightX, textY + 20);
        }

    } finally {
        g.dispose();
    }

    // === Write as real PNG bytes ===
    String safeCode = itemCode.replaceAll("[^a-zA-Z0-9_-]", "_");

    java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
    ImageIO.write(card, "png", baos);
    byte[] pngBytes = baos.toByteArray();

    response.setContentType(org.springframework.http.MediaType.IMAGE_PNG_VALUE);
    response.setHeader(
            "Content-Disposition",
            "attachment; filename=\"barcode-" + safeCode + ".png\""
    );
    response.setContentLength(pngBytes.length);

    response.getOutputStream().write(pngBytes);
    response.flushBuffer();
}

    // --------------------------------------------------
// API: Lightweight data for DataTables (AJAX)
// URL: GET /schoolitems/data?shop=... (optional)
// --------------------------------------------------

}
