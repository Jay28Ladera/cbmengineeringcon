package com.asmsi.admin_system.accounting;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long> {
    Optional<Account> findByCode(String code);
    List<Account> findAllByOrderByCodeAsc();
    List<Account> findByActiveTrueOrderByCodeAsc();
    boolean existsByCode(String code);
}
