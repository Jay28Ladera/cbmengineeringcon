package com.asmsi.admin_system.controller;

import com.asmsi.admin_system.dto.RequestDecisionDTO;
import com.asmsi.admin_system.entity.ItemRequest;
import com.asmsi.admin_system.repository.ItemRequestRepository;
import com.asmsi.admin_system.service.ItemRequestDecisionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

@Controller
public class ItemRequestController {

    @Autowired
    private ItemRequestRepository itemRequestRepository;

    @Autowired
    private ItemRequestDecisionService decisionService;

    @GetMapping("/inventory/requests")
    public String viewRequests(Model model) {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        boolean loggedIn = auth != null
                && auth.isAuthenticated()
                && !(auth instanceof AnonymousAuthenticationToken);

        String username = loggedIn ? auth.getName() : "Guest";

        String role = "ROLE_USER";
        if (loggedIn && auth.getAuthorities() != null && !auth.getAuthorities().isEmpty()) {
            role = auth.getAuthorities().iterator().next().getAuthority();
        }

        model.addAttribute("username", username);
        model.addAttribute("role", role);
        model.addAttribute("activePage", "inventory-requests");

        List<ItemRequest> requests;

        if ("ROLE_ADMIN".equalsIgnoreCase(role)) {
            requests = itemRequestRepository.findAll();
            requests.sort(Comparator
                    .comparing(ItemRequest::getDateRequested, Comparator.nullsLast(Comparator.naturalOrder()))
                    .reversed());
        } else {
            requests = itemRequestRepository.findByRequestedByIgnoreCaseOrderByDateRequestedDesc(username);
        }

        model.addAttribute("requests", requests);
        return "inventory-requests";
    }

    @PostMapping(value = "/inventory/requests/decision", consumes = "application/json")
    @ResponseBody
    public ResponseEntity<?> decision(@RequestBody RequestDecisionDTO dto, Principal principal) {

        String username = (principal != null) ? principal.getName() : "Unknown";

        try {
            decisionService.applyDecision(dto, username);
            return ResponseEntity.ok().build();
        } catch (RuntimeException ex) {
            return ResponseEntity.badRequest().body(ex.getMessage());
        }
    }

    @PostMapping("/inventory/requests/save")
    public String saveRequest(
            @RequestParam("department") String department,
            @RequestParam("dateNeeded") String dateNeeded,
            @RequestParam("itemName") String itemName,
            @RequestParam("quantity") Integer quantity,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "purpose", required = false) String purpose
    ) {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        boolean loggedIn = auth != null
                && auth.isAuthenticated()
                && !(auth instanceof AnonymousAuthenticationToken);

        String username = loggedIn ? auth.getName() : "Guest";

        ItemRequest r = new ItemRequest();
        r.setRequestedBy(username);
        r.setDepartment(department);
        r.setDateRequested(LocalDate.now());
        r.setDateNeeded(LocalDate.parse(dateNeeded));
        r.setItemName(itemName);
        r.setQuantity(quantity == null ? 0 : quantity);
        r.setDescription(description);
        r.setPurpose(purpose);

        itemRequestRepository.save(r);

        return "redirect:/inventory/requests";
    }
}
