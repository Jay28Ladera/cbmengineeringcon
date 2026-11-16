package com.asmsi.admin_system.accounting;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface BankReconRepository extends JpaRepository<BankRecon, Long> {
    List<BankRecon> findByBankIdOrderByStatementDateDescIdDesc(Long bankId);
    Optional<BankRecon> findFirstByBankIdAndStatusOrderByStatementDateDescIdDesc(Long bankId, String status);
    Optional<BankRecon> findFirstByBankIdAndStatus(Long bankId, String status);
}
