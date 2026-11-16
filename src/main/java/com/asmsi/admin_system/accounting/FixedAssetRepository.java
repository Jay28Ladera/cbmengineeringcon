package com.asmsi.admin_system.accounting;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FixedAssetRepository extends JpaRepository<FixedAsset, Long> {
    List<FixedAsset> findAllByOrderByCodeAsc();
}
