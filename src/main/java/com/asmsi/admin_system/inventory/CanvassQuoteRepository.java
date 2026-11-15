package com.asmsi.admin_system.inventory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;

public interface CanvassQuoteRepository extends JpaRepository<CanvassQuote, Long> {
    List<CanvassQuote> findByCanvassId(Long canvassId);
}
