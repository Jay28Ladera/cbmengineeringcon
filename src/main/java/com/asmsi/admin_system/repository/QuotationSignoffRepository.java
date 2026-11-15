package com.asmsi.admin_system.repository;

import com.asmsi.admin_system.entity.QuotationSignoff;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface QuotationSignoffRepository extends JpaRepository<QuotationSignoff, Long> {
    Optional<QuotationSignoff> findByPublicToken(String publicToken);
}
