package com.asmsi.admin_system.repository;

import com.asmsi.admin_system.entity.JobOrder;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;

public interface JobOrderRepository extends JpaRepository<JobOrder, Long> {
    /** Loads quotation, client and line items in the same query (the list shows totals). */
    @EntityGraph(attributePaths = {"quotation", "quotation.inquiry", "quotation.lineItems"})
    List<JobOrder> findAllByOrderByCreatedAtDesc();

    @EntityGraph(attributePaths = {"quotation", "quotation.inquiry", "quotation.lineItems"})
    Optional<JobOrder> findWithDetailsById(Long id);

    Optional<JobOrder> findByQuotationId(Long quotationId);

    /** Rows of [status, count]. */
    @Query("select j.status, count(j) from JobOrder j group by j.status")
    List<Object[]> countByStatusGrouped();
}
