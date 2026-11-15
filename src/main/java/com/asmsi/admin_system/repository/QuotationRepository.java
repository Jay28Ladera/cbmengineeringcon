package com.asmsi.admin_system.repository;

import com.asmsi.admin_system.entity.Quotation;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;

public interface QuotationRepository extends JpaRepository<Quotation, Long> {
    Optional<Quotation> findByInquiryId(Long inquiryId);

    /** Loads each quotation's client and line items in the same query (the list shows totals). */
    @EntityGraph(attributePaths = {"inquiry", "lineItems"})
    List<Quotation> findAllByOrderByCreatedAtDesc();

    @EntityGraph(attributePaths = {"inquiry", "lineItems"})
    Optional<Quotation> findWithDetailsById(Long id);

    /** All quotations with their inquiry loaded (used to link inquiries to their quotation). */
    @Query("select q from Quotation q join fetch q.inquiry")
    List<Quotation> findAllWithInquiryId();

    /** Rows of [status, count]. */
    @Query("select q.status, count(q) from Quotation q group by q.status")
    List<Object[]> countByStatusGrouped();

    /**
     * One round trip for the sidebar "Approvals" count:
     * [0] quotations waiting, [1] quote approvers set up, [2] this user is a quote approver,
     * [3..5] canvasses waiting for Approved / Noted / Processed, [6..8] signers set up per step, [9..11] this user signs that step,
     * [12] payment vouchers waiting, [13] payment approvers set up, [14] this user approves payments, [15] client billings waiting.
     */
    @org.springframework.data.jpa.repository.Query(value = "select (select count(*) from quotations where status = 'FOR_APPROVAL'), " +
            "(select count(*) from quote_approver), (select count(*) from quote_approver where user_id = ?1), " +
            "(select count(*) from pur_canvass c left join pur_canvass_signoff s on s.canvass_id = c.id where c.status = 'FOR_APPROVAL' and s.approved_at is null), " +
            "(select count(*) from pur_canvass c join pur_canvass_signoff s on s.canvass_id = c.id where c.status = 'FOR_APPROVAL' and s.approved_at is not null and s.noted_at is null), " +
            "(select count(*) from pur_canvass c join pur_canvass_signoff s on s.canvass_id = c.id where c.status in ('APPROVED','ORDERED') and s.noted_at is not null and s.processed_at is null), " +
            "(select count(*) from pur_canvass_signer where step = 'APPROVE'), (select count(*) from pur_canvass_signer where step = 'NOTE'), (select count(*) from pur_canvass_signer where step = 'PROCESS'), " +
            "(select count(*) from pur_canvass_signer where step = 'APPROVE' and user_id = ?1), (select count(*) from pur_canvass_signer where step = 'NOTE' and user_id = ?1), " +
            "(select count(*) from pur_canvass_signer where step = 'PROCESS' and user_id = ?1), " +
            "(select count(*) from acc_voucher where status = 'FOR_APPROVAL'), (select count(*) from acc_payment_approver), " +
            "(select count(*) from acc_payment_approver where user_id = ?1), " +
            "(select count(*) from acc_client_billing where status = 'FOR_APPROVAL')", nativeQuery = true)
    List<Object[]> approvalCounts(Long userId);

    @org.springframework.data.jpa.repository.Query("select distinct q from Quotation q left join fetch q.inquiry left join fetch q.lineItems where q.status = ?1 order by q.id")
    List<Quotation> findWithDetailsByStatus(String status);
}
