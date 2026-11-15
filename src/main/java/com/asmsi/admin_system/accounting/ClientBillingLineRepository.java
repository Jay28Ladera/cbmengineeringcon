package com.asmsi.admin_system.accounting;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ClientBillingLineRepository extends JpaRepository<ClientBillingLine, Long> {
    List<ClientBillingLine> findByBillingIdOrderBySortOrderAscIdAsc(Long billingId);
}
