package com.asmsi.admin_system.accounting;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface DepreciationLineRepository extends JpaRepository<DepreciationLine, Long> {
    List<DepreciationLine> findByRunId(Long runId);
    List<DepreciationLine> findByAssetId(Long assetId);
    /** Depreciation posted per asset (runs not voided): [assetId, total, last month end]. */
    @Query("select l.assetId, sum(l.amount), max(r.monthEnd) from DepreciationLine l, DepreciationRun r where r.id = l.runId and r.status = 'POSTED' group by l.assetId")
    List<Object[]> postedByAsset();
}
