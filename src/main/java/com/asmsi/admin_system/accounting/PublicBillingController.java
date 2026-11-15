package com.asmsi.admin_system.accounting;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;

/** The client's view of an issued billing, opened from the emailed link (no login). */
@Controller
public class PublicBillingController {
    private final ClientBillingService service;
    private final BillingSheet sheet;

    public PublicBillingController(ClientBillingService service, BillingSheet sheet) { this.service = service; this.sheet = sheet; }

    @GetMapping("/sb/{token}")
    public String view(@PathVariable String token, Model model) {
        ClientBilling b = service.openForClient(token).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        sheet.fill(b, model);
        model.addAttribute("publicView", true);
        return "acc-billing-print";
    }
}
