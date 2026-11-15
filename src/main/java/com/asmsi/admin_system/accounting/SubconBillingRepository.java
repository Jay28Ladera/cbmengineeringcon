package com.asmsi.admin_system.accounting;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface SubconBillingRepository extends JpaRepository<SubconBilling, Long> {
    List<SubconBilling> findByContractIdOrderByIdAsc(Long contractId);
    List<SubconBilling> findTop200ByOrderByIdDesc();
    Optional<SubconBilling> findFirstByBillId(Long billId);
}
