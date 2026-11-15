package com.asmsi.admin_system.accounting;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface JournalEntryRepository extends JpaRepository<JournalEntry, Long> {
    List<JournalEntry> findTop300ByOrderByEntryDateDescIdDesc();
    List<JournalEntry> findBySourceAndSourceIdOrderByIdAsc(String source, Long sourceId);
    Optional<JournalEntry> findFirstBySourceAndSourceIdAndStatusOrderByIdDesc(String source, Long sourceId, String status);
}
