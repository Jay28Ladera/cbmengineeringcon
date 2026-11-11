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
}
