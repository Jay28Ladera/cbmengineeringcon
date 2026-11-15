package com.asmsi.admin_system.accounting;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface SubconContractRepository extends JpaRepository<SubconContract, Long> {
    List<SubconContract> findAllByOrderByIdDesc();
    List<SubconContract> findByJobOrderIdOrderByIdAsc(Long jobOrderId);
    List<SubconContract> findBySupplierIdOrderByIdDesc(Long supplierId);
}
