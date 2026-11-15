package com.asmsi.admin_system.controller;

import com.asmsi.admin_system.service.QuoteWorkflowService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * The client's private quotation page, opened from the link in the email. No sign-in: the random link is the key.
 * The client can read and print the quotation, then accept it (typed name + drawn signature or an uploaded signed
 * copy) or decline it with a reason.
 */
@Controller
@RequestMapping("/q")
public class PublicQuoteController {

    private final QuoteWorkflowService workflow;

    public PublicQuoteController(QuoteWorkflowService workflow) { this.workflow = workflow; }

    @GetMapping("/{token}")
    public String view(@PathVariable String token, Model model, HttpServletResponse response) {
        response.setHeader("X-Robots-Tag", "noindex, nofollow");
        response.setHeader("Referrer-Policy", "no-referrer");
        var view = workflow.openForClient(token, true);
        if (view.isEmpty()) {
            response.setStatus(404);
            model.addAttribute("invalid", true);
            return "quote-public";
        }
        var q = view.get().quotation();
        var so = view.get().signoff();
        model.addAttribute("quotation", q);
        model.addAttribute("signoff", so);
        model.addAttribute("token", token);
        String st = q.getStatus();
        model.addAttribute("state", "SENT".equals(st) ? (so.isExpired() ? "expired" : "waiting")
                : "ACCEPTED".equals(st) ? "accepted" : "REJECTED".equals(st) ? "declined" : "closed");
        return "quote-public";
    }

    @PostMapping("/{token}/accept")
    public String accept(@PathVariable String token,
                         @RequestParam(required = false) String clientName,
                         @RequestParam(required = false) String clientTitle,
                         @RequestParam(required = false) String signatureData,
                         @RequestParam(required = false) MultipartFile signedCopy,
                         @RequestParam(defaultValue = "false") boolean agree,
                         HttpServletRequest request, RedirectAttributes redirect) {
        try {
            workflow.clientAccept(token, clientName, clientTitle, signatureData, signedCopy, agree, ip(request), request.getHeader("User-Agent"));
            redirect.addFlashAttribute("success", "Thank you. Your acceptance has been sent to CBM Engineering & Construction. We will contact you about the next steps.");
        } catch (IllegalArgumentException | IllegalStateException | java.io.IOException e) {
            redirect.addFlashAttribute("error", e.getMessage());
            redirect.addFlashAttribute("clientName", clientName);
            redirect.addFlashAttribute("clientTitle", clientTitle);
        }
        return "redirect:/q/" + token;
    }

    @PostMapping("/{token}/decline")
    public String decline(@PathVariable String token,
                          @RequestParam(required = false) String clientName,
                          @RequestParam(required = false) String reason,
                          HttpServletRequest request, RedirectAttributes redirect) {
        try {
            workflow.clientDecline(token, clientName, reason, ip(request), request.getHeader("User-Agent"));
            redirect.addFlashAttribute("success", "Thank you for letting us know. Your answer has been sent to CBM Engineering & Construction.");
        } catch (IllegalArgumentException | IllegalStateException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/q/" + token;
    }

    private static String ip(HttpServletRequest r) {
        String f = r.getHeader("X-Forwarded-For");
        return f != null && !f.isBlank() ? f.split(",")[0].trim() : r.getRemoteAddr();
    }
}
