package com.asmsi.admin_system.accounting;

import com.asmsi.admin_system.entity.JobOrder;
import com.asmsi.admin_system.repository.JobOrderRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;

/** Fills the model for a client billing page or printout (staff pages, Approvals and the client's link). */
@Component
public class BillingSheet {
    private final ClientBillingLineRepository lines;
    private final ClientBillingService service;
    private final JobOrderRepository jobs;

    public BillingSheet(ClientBillingLineRepository lines, ClientBillingService service, JobOrderRepository jobs) {
        this.lines = lines; this.service = service; this.jobs = jobs;
    }

    @Transactional(readOnly = true)
    public void fill(ClientBilling b, Model model) {
        JobContract c = service.contract(b.jobOrderId);
        JobOrder j = jobs.findById(b.jobOrderId).orElse(null);
        var inq = j != null && j.getQuotation() != null ? j.getQuotation().getInquiry() : null;
        model.addAttribute("b", b);
        model.addAttribute("c", c);
        model.addAttribute("lines", lines.findByBillingIdOrderBySortOrderAscIdAsc(b.id));
        model.addAttribute("jobNumber", j == null ? "" : (j.getJobOrderNumber() == null ? "JO #" + j.getId() : j.getJobOrderNumber()));
        model.addAttribute("project", inq == null ? "" : inq.getProjectName());
        model.addAttribute("location", inq == null ? "" : inq.getProjectLocation());
        model.addAttribute("quoteNumber", j == null || j.getQuotation() == null ? "" : j.getQuotation().getQuotationNumber());
        model.addAttribute("words", VoucherService.inWords(b.amountDue));
        model.addAttribute("vatable", c.isVat() ? b.amountDue.subtract(ClientBillingService.vatIn(b.amountDue)) : b.amountDue);
    }
}
