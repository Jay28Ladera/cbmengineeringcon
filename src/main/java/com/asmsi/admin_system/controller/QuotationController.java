package com.asmsi.admin_system.controller;

import com.asmsi.admin_system.repository.QuotationRepository;
import com.asmsi.admin_system.repository.JobOrderRepository;
import com.asmsi.admin_system.entity.Quotation;
import com.asmsi.admin_system.entity.QuotationLineItem;
import com.asmsi.admin_system.entity.JobOrder;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Controller
@RequestMapping({"/Quotations", "/quotations"})
public class QuotationController {
    private final QuotationRepository quotations;
    private final JobOrderRepository jobOrders;
    public QuotationController(QuotationRepository quotations, JobOrderRepository jobOrders) {
        this.quotations = quotations;
        this.jobOrders = jobOrders;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("quotations", quotations.findAllByOrderByCreatedAtDesc());
        model.addAttribute("activePage", "quotations");
        return "quotations";
    }

    @GetMapping("/{id}")
    public String view(@PathVariable Long id, Model model) {
        model.addAttribute("quotation", quotations.findWithDetailsById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Quotation not found")));
        model.addAttribute("jobOrder", jobOrders.findByQuotationId(id).orElse(null));
        model.addAttribute("activePage", "quotations");
        return "quotation-detail";
    }

    @PostMapping("/{id}/items")
    @Transactional
    public String saveItems(@PathVariable Long id,
            @RequestParam(name="description", required=false) List<String> descriptions,
            @RequestParam(name="quantity", required=false) List<String> quantities,
            @RequestParam(name="unit", required=false) List<String> units,
            @RequestParam(name="unitPrice", required=false) List<String> prices,
            @RequestParam(defaultValue="0") BigDecimal taxRate,
            @RequestParam(defaultValue="0") BigDecimal discountAmount,
            @RequestParam(required=false) String terms,
            RedirectAttributes redirect) {
        Quotation quote = quotations.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Quotation not found"));
        if (!"DRAFT".equals(quote.getStatus())) {
            redirect.addFlashAttribute("error", "Only a draft quotation can be edited.");
            return "redirect:/Quotations/" + id;
        }
        try {
            if (taxRate.signum() < 0 || taxRate.compareTo(new BigDecimal("100")) > 0 || discountAmount.signum() < 0)
                throw new IllegalArgumentException("Tax must be 0–100% and discount cannot be negative.");
            List<QuotationLineItem> pendingItems = new java.util.ArrayList<>();
            for (int i=0; descriptions != null && i<descriptions.size(); i++) {
                String description = descriptions.get(i) == null ? "" : descriptions.get(i).trim();
                if (description.isEmpty()) continue;
                BigDecimal quantity = new BigDecimal(quantities.get(i));
                BigDecimal price = new BigDecimal(prices.get(i));
                if (quantity.signum() <= 0 || price.signum() < 0) throw new IllegalArgumentException("Each item needs a positive quantity and a non-negative price.");
                QuotationLineItem item = new QuotationLineItem();
                item.setDescription(description);
                item.setQuantity(quantity);
                item.setUnit(units != null && i < units.size() && !units.get(i).isBlank() ? units.get(i).trim() : "unit");
                item.setUnitPrice(price);
                pendingItems.add(item);
            }
            if (pendingItems.isEmpty()) throw new IllegalArgumentException("Add at least one priced item.");
            BigDecimal pendingSubtotal = pendingItems.stream().map(QuotationLineItem::getLineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
            if (discountAmount.compareTo(pendingSubtotal) > 0) throw new IllegalArgumentException("Discount cannot be greater than the quotation subtotal.");
            quote.clearLineItems();
            pendingItems.forEach(quote::addLineItem);
            quote.setTaxRate(taxRate);
            quote.setDiscountAmount(discountAmount);
            quote.setTerms(terms == null ? null : terms.trim());
            quotations.save(quote);
            redirect.addFlashAttribute("success", "Quotation items and totals saved.");
        } catch (NumberFormatException | IndexOutOfBoundsException ex) {
            redirect.addFlashAttribute("error", "Enter a valid quantity and unit price for every item.");
        } catch (IllegalArgumentException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/Quotations/" + id;
    }

    @PostMapping("/{id}/send")
    @Transactional
    public String markSent(@PathVariable Long id, RedirectAttributes redirect) {
        Quotation quote = quotations.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Quotation not found"));
        if (!"DRAFT".equals(quote.getStatus()) || quote.getLineItems().isEmpty()) {
            redirect.addFlashAttribute("error", "Save at least one priced item before issuing this quotation.");
        } else {
            quote.setStatus("SENT");
            quote.setSentAt(LocalDateTime.now());
            quotations.save(quote);
            redirect.addFlashAttribute("success", "Quotation marked as sent.");
        }
        return "redirect:/Quotations/" + id;
    }

    @PostMapping("/{id}/decision")
    @Transactional
    public String decision(@PathVariable Long id, @RequestParam String decision, RedirectAttributes redirect) {
        Quotation quote = quotations.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Quotation not found"));
        String normalized = decision == null ? "" : decision.toUpperCase();
        if (!"SENT".equals(quote.getStatus()) || !(normalized.equals("ACCEPTED") || normalized.equals("REJECTED"))) {
            redirect.addFlashAttribute("error", "Only a sent quotation can be accepted or rejected.");
        } else {
            quote.setStatus(normalized);
            quote.setDecidedAt(LocalDateTime.now());
            quotations.save(quote);
            redirect.addFlashAttribute("success", "Client decision recorded: " + normalized + ".");
        }
        return "redirect:/Quotations/" + id;
    }

    @PostMapping("/{id}/job-order")
    @Transactional
    public String createJobOrder(@PathVariable Long id, RedirectAttributes redirect) {
        Quotation quote = quotations.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Quotation not found"));
        if (!"ACCEPTED".equals(quote.getStatus())) {
            redirect.addFlashAttribute("error", "Only an accepted quotation can become a job order.");
            return "redirect:/Quotations/" + id;
        }
        JobOrder order = jobOrders.findByQuotationId(id).orElseGet(() -> {
            JobOrder created = new JobOrder();
            created.setQuotation(quote);
            JobOrder saved = jobOrders.saveAndFlush(created);
            saved.setJobOrderNumber("JO-%06d".formatted(saved.getId()));
            return jobOrders.save(saved);
        });
        redirect.addFlashAttribute("success", "Job order created.");
        return "redirect:/JobOrders/" + order.getId();
    }
}
