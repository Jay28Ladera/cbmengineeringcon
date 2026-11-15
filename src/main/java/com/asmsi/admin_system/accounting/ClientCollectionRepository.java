package com.asmsi.admin_system.accounting;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ClientCollectionRepository extends JpaRepository<ClientCollection, Long> {
    List<ClientCollection> findTop100ByOrderByIdDesc();
    List<ClientCollection> findByJobOrderIdOrderByIdAsc(Long jobOrderId);
}
