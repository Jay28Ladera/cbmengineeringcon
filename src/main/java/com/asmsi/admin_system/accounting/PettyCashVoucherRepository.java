package com.asmsi.admin_system.accounting;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PettyCashVoucherRepository extends JpaRepository<PettyCashVoucher, Long> {
    List<PettyCashVoucher> findByFundIdAndStatusAndReplenishmentIdIsNullOrderByIdAsc(Long fundId, String status);
    List<PettyCashVoucher> findTop200ByOrderByIdDesc();
    List<PettyCashVoucher> findByReplenishmentIdOrderByIdAsc(Long replenishmentId);
}
