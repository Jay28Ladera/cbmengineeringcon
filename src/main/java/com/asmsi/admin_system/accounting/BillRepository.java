package com.asmsi.admin_system.accounting;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Collection;
import java.util.List;

public interface BillRepository extends JpaRepository<Bill, Long> {
    List<Bill> findByStatusInOrderByDueDateAscIdAsc(Collection<String> statuses);
    List<Bill> findTop50ByStatusInOrderByIdDesc(Collection<String> statuses);
    List<Bill> findByPoIdOrderByIdAsc(Long poId);
    List<Bill> findBySupplierIdAndStatusInOrderByDueDateAscIdAsc(Long supplierId, Collection<String> statuses);
}
