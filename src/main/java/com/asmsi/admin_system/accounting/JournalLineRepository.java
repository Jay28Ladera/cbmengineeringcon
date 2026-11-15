package com.asmsi.admin_system.accounting;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface JournalLineRepository extends JpaRepository<JournalLine, Long> {
    List<JournalLine> findByJournalIdOrderBySortOrderAscIdAsc(Long journalId);
    List<JournalLine> findByJournalIdIn(Collection<Long> journalIds);

    /** [accountId, total debit, total credit] up to and including a date (posted and void entries both count: voids are cancelled by their reversals). */
    @Query("select l.accountId, sum(l.debit), sum(l.credit) from JournalLine l, JournalEntry e where e.id = l.journalId and e.entryDate <= ?1 group by l.accountId")
    List<Object[]> totalsAsOf(LocalDate asOf);

    /** Same, between two dates (for income statement periods). */
    @Query("select l.accountId, sum(l.debit), sum(l.credit) from JournalLine l, JournalEntry e where e.id = l.journalId and e.entryDate between ?1 and ?2 group by l.accountId")
    List<Object[]> totalsBetween(LocalDate from, LocalDate to);

    /** Lines of one account with their entry, oldest first. */
    @Query("select l, e from JournalLine l, JournalEntry e where e.id = l.journalId and l.accountId = ?1 and e.entryDate between ?2 and ?3 order by e.entryDate, e.id, l.id")
    List<Object[]> ledger(Long accountId, LocalDate from, LocalDate to);

    @Query("select coalesce(sum(l.debit), 0) - coalesce(sum(l.credit), 0) from JournalLine l, JournalEntry e where e.id = l.journalId and l.accountId = ?1 and e.entryDate < ?2")
    java.math.BigDecimal netBefore(Long accountId, LocalDate before);
}
