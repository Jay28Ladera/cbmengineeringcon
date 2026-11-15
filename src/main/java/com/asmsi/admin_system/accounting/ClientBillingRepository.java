package com.asmsi.admin_system.accounting;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ClientBillingRepository extends JpaRepository<ClientBilling, Long> {
    List<ClientBilling> findByJobOrderIdOrderByIdAsc(Long jobOrderId);
    List<ClientBilling> findByStatusInOrderByDueDateAscIdAsc(Collection<String> statuses);
    List<ClientBilling> findByStatusOrderByIdAsc(String status);
    List<ClientBilling> findTop100ByOrderByIdDesc();
    List<ClientBilling> findByJobOrderIdAndStatusInOrderByIdAsc(Long jobOrderId, Collection<String> statuses);
    Optional<ClientBilling> findByPublicToken(String token);
}
