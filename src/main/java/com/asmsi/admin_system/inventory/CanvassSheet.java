package com.asmsi.admin_system.inventory;

import org.springframework.stereotype.Component;
import org.springframework.ui.Model;

import java.util.*;

/** Fills the model for the printed canvass sheet (form CBM-CD-006): four suppliers per page, side by side. */
@Component
public class CanvassSheet {

    public static final int SUPPLIERS_PER_PAGE = 4;

    private final PurchasingService purchasing;
    private final CanvassApprovalService approvals;
    private final Lookups lookups;

    public CanvassSheet(PurchasingService purchasing, CanvassApprovalService approvals, Lookups lookups) {
        this.purchasing = purchasing; this.approvals = approvals; this.lookups = lookups;
    }

    public void fill(Model model, Canvass c) {
        PurchasingService.Grid g = purchasing.grid(c);
        List<List<CanvassSupplier>> pages = new ArrayList<>();
        List<CanvassSupplier> sups = g.suppliers();
        for (int i = 0; i < Math.max(1, sups.size()); i += SUPPLIERS_PER_PAGE) {
            List<CanvassSupplier> page = new ArrayList<>(sups.subList(Math.min(i, sups.size()), Math.min(i + SUPPLIERS_PER_PAGE, sups.size())));
            while (page.size() < SUPPLIERS_PER_PAGE) page.add(null);       // blank columns, as on the paper form
            pages.add(page);
        }
        int blankRows = Math.max(0, 8 - g.items().size());
        model.addAttribute("c", c);
        model.addAttribute("g", g);
        model.addAttribute("pages", pages);
        model.addAttribute("blankRows", blankRows == 0 ? List.of() : Collections.nCopies(blankRows, 0));
        model.addAttribute("supplierMap", lookups.supplierMap());
        model.addAttribute("job", c.jobOrderId == null ? null : lookups.jobMap().get(c.jobOrderId));
        model.addAttribute("so", approvals.signoff(c.id));
        model.addAttribute("headerColors", List.of("#f8cbad", "#cfe2f3", "#ead1f5", "#d9ead3"));
    }
}
