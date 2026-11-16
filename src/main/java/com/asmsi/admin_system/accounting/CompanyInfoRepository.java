package com.asmsi.admin_system.accounting;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CompanyInfoRepository extends JpaRepository<CompanyInfo, Long> {
    default CompanyInfo get() { return findById(1L).orElseGet(CompanyInfo::new); }
}
