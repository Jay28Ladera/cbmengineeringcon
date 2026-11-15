package com.asmsi.admin_system.accounting;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface BankAccountRepository extends JpaRepository<BankAccount, Long> {
    List<BankAccount> findByActiveTrueOrderByKindAscNameAsc();
    List<BankAccount> findAllByOrderByKindAscNameAsc();
    Optional<BankAccount> findFirstByAccountId(Long accountId);
}
