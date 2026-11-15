package com.asmsi.admin_system.accounting;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Collection;
import java.util.List;

public interface PaymentVoucherRepository extends JpaRepository<PaymentVoucher, Long> {
    List<PaymentVoucher> findByStatusInOrderByIdDesc(Collection<String> statuses);
    List<PaymentVoucher> findTop100ByOrderByIdDesc();
    List<PaymentVoucher> findByStatusOrderByIdAsc(String status);
}
