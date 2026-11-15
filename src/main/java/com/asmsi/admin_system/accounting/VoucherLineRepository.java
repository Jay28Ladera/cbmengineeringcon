package com.asmsi.admin_system.accounting;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Collection;
import java.util.List;

public interface VoucherLineRepository extends JpaRepository<VoucherLine, Long> {
    List<VoucherLine> findByVoucherId(Long voucherId);
    List<VoucherLine> findByBillId(Long billId);
    List<VoucherLine> findByBillIdIn(Collection<Long> billIds);
}
