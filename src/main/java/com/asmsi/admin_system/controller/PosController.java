package com.asmsi.admin_system.controller;

import com.asmsi.admin_system.entity.Sale;
import com.asmsi.admin_system.repository.ItemRepository;
import com.asmsi.admin_system.repository.SaleRepository;
import com.asmsi.admin_system.service.PosService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Controller
public class PosController {

    private final ItemRepository itemRepo;
    private final SaleRepository saleRepo;
    private final PosService posService;
    private final ObjectMapper mapper;

    public PosController(ItemRepository itemRepo,
                         SaleRepository saleRepo,
                         PosService posService,
                         ObjectMapper mapper) {
        this.itemRepo = itemRepo;
        this.saleRepo = saleRepo;
        this.posService = posService;
        this.mapper = mapper;
    }

    /* --------------------------- PAGE --------------------------- */

    @GetMapping("/pos")
    public String pos(Model model,
                      @RequestParam(value = "saleId", required = false) Long saleId,
                      @RequestParam(value = "error", required = false) String error) {

        model.addAttribute("activePage", "pos");
        model.addAttribute("merchandiseExpanded", true);
        model.addAttribute("items", itemRepo.findAll());

        if (saleId != null) {
            model.addAttribute("saleId", saleId);
            model.addAttribute("recentSale", saleRepo.findById(saleId).orElse(null));
        }
        if (error != null && !error.isBlank()) {
            // keep error out of any headers; show it only on the page
            model.addAttribute("error", error);
        }
        return "pos";
    }

    /* ------------------------- CHECKOUT ------------------------- */

    @PostMapping("/pos/checkout")
    public String checkout(@RequestParam String payload,
                           @RequestParam BigDecimal cash,
                           RedirectAttributes ra) {
        try {
            List<PosService.CartLine> lines = parseLines(payload);
            if (lines.isEmpty()) {
                ra.addFlashAttribute("error", "Cart is empty.");
                return "redirect:/pos";
            }

            Long saleId = posService.checkout(lines, cash == null ? BigDecimal.ZERO : cash);

            // SAFE redirect; Spring builds a proper Location header (no CR/LF)
            ra.addAttribute("saleId", saleId);
            return "redirect:/pos";

        } catch (Exception e) {
            // Don’t echo raw payload; show message on page only
            ra.addFlashAttribute("error", e.getMessage());
            return "redirect:/pos";
        }
    }

    /* --------------------- PRINTABLE RECEIPT -------------------- */

    @GetMapping("/pos/receipt/{id}")
    public String receipt(@PathVariable Long id, Model model) {
        Sale sale = saleRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Sale not found"));
        model.addAttribute("sale", sale);
        return "pos-receipt";
    }

    /* --------------------------- HELPERS ------------------------ */

    private List<PosService.CartLine> parseLines(String payloadJson) throws Exception {
        List<PosService.CartLine> out = new ArrayList<>();
        if (payloadJson == null || payloadJson.isBlank()) return out;

        JsonNode root = mapper.readTree(payloadJson);
        JsonNode arr = root != null ? root.get("items") : null;
        if (arr == null || !arr.isArray()) return out;

        for (JsonNode n : arr) {
            String rawCode = n.hasNonNull("itemNumber") ? n.get("itemNumber").asText() : "";
            String code = sanitizeCode(rawCode);
            int qty = n.hasNonNull("qty") ? Math.max(1, n.get("qty").asInt()) : 1;

            // priceFromClient is ignored/trusted from DB; pass ZERO
            out.add(new PosService.CartLine(code, qty, BigDecimal.ZERO));
        }
        return out;
    }

    private String sanitizeCode(String s) {
        if (s == null) return "";
        // remove CR/LF and trim to keep data out of headers/logs
        return s.replace("\r", "").replace("\n", "").trim();
    }
}
