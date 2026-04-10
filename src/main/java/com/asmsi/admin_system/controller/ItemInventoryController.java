package com.asmsi.admin_system.controller;

import com.asmsi.admin_system.dto.InventoryMonthlyCellDto;
import com.asmsi.admin_system.entity.InventoryRecord;
import com.asmsi.admin_system.entity.SchoolItem;
import com.asmsi.admin_system.entity.Shop;
import com.asmsi.admin_system.repository.InventoryRecordRepository;
import com.asmsi.admin_system.repository.SchoolItemRepository;
import com.asmsi.admin_system.repository.ShopRepository;
import com.asmsi.admin_system.service.InventoryReportService;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.oned.Code128Writer;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
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
import java.util.*;
import java.util.stream.Collectors;

@Controller
public class ItemInventoryController {

    @Autowired
    private SchoolItemRepository schoolItemRepository;

    @Autowired
    private ShopRepository shopRepository;

    @Autowired
    private InventoryRecordRepository inventoryRecordRepository;

    @Autowired
    private InventoryReportService inventoryReportService;

    // --------------------------------------------------
    // PAGE: School Items Inventory (FAST LOAD)
    // --------------------------------------------------
    @GetMapping("/schoolitems")
    public String showSchoolItemsPage(
            @RequestParam(value = "shop", required = false) String shop,
            Model model) {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth != null && auth.isAuthenticated()) {
            model.addAttribute("username", auth.getName());
            model.addAttribute("role", auth.getAuthorities().stream()
                    .findFirst().map(Object::toString).orElse("ROLE_USER"));
        } else {
            model.addAttribute("username", "Guest");
            model.addAttribute("role", "Unknown");
        }

        List<Shop> allShops;
        try {
            allShops = shopRepository.findAll();
        } catch (Exception ex) {
            ex.printStackTrace();
            allShops = Collections.emptyList();
        }

        model.addAttribute("allShops", allShops);
        model.addAttribute("selectedShop", shop);
        model.addAttribute("activePage", "inventory-school-items");

        return "schoolitems";
    }

    // --------------------------------------------------
    // API: AJAX fetch of items
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
    // ✅ PAGE: Inventory Report
    // --------------------------------------------------
    @GetMapping("/schoolitems/inventory/report")
    public String showInventoryReport(
            @RequestParam(value = "year", required = false) Integer year,
            @RequestParam(value = "shop", required = false) String shop,
            Model model) {

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

        // Items list (left columns)
        List<SchoolItem> items = schoolItemRepository.findAll();

        if (shop != null && !shop.isBlank()) {
            String s = shop.trim();
            items = items.stream()
                    .filter(i -> s.equalsIgnoreCase(Optional.ofNullable(i.getShops()).orElse("").trim()))
                    .collect(Collectors.toList());
        }

        // ✅ item.id -> month -> dto
        Map<Long, Map<Integer, InventoryMonthlyCellDto>> itemMonthLogs =
                inventoryReportService.buildItemMonthLogs(selectedYear, shop);

        // Years dropdown from inventory_records
        Set<Integer> years = inventoryRecordRepository.findAll().stream()
                .filter(r -> r.getInventoryDate() != null)
                .map(InventoryRecord::getInventoryDate)
                .map(LocalDate::getYear)
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
    // BARCODE PNG
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

        int width = 700;
        int height = 350;

        BufferedImage card = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = card.createGraphics();

        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            g.setColor(Color.WHITE);
            g.fillRect(0, 0, width, height);

            g.setColor(new Color(0xDDDDDD));
            g.fillRoundRect(5, 5, width - 10, height - 10, 25, 25);

            g.setColor(Color.WHITE);
            g.fillRoundRect(12, 12, width - 24, height - 24, 20, 20);

            int paddingLeft = 30;
            int paddingTop = 30;

            int pictureBoxWidth = 220;
            int pictureBoxHeight = 220;

            BufferedImage itemImage = null;
            try {
                if (item.getImageUrl() != null && !item.getImageUrl().isBlank()) {
                    itemImage = ImageIO.read(new URL(item.getImageUrl()));
                }
            } catch (Exception ignored) {}

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
}
