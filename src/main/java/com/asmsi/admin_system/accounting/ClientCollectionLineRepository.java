package com.asmsi.admin_system.accounting;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ClientCollectionLineRepository extends JpaRepository<ClientCollectionLine, Long> {
    List<ClientCollectionLine> findByCollectionId(Long collectionId);
    List<ClientCollectionLine> findByBillingId(Long billingId);
}
