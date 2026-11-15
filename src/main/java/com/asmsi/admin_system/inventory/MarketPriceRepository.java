package com.asmsi.admin_system.inventory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;

public interface MarketPriceRepository extends JpaRepository<MarketPrice, Long> {
    List<MarketPrice> findAllByOrderByPriceDateDescIdDesc();
    List<MarketPrice> findByMaterialIdOrderByPriceDateDescIdDesc(Long materialId);
    boolean existsBySourceAndRefNoAndMaterialIdAndSupplierId(String source, String refNo, Long materialId, Long supplierId);
    boolean existsBySourceAndRefNoAndMaterialIdIsNullAndDescriptionAndSupplierId(String source, String refNo, String description, Long supplierId);
    Optional<MarketPrice> findFirstBySourceAndRefNoAndMaterialIdAndSupplierId(String source, String refNo, Long materialId, Long supplierId);
    Optional<MarketPrice> findFirstBySourceAndRefNoAndMaterialIdIsNullAndDescriptionAndSupplierId(String source, String refNo, String description, Long supplierId);
    @Query("select p from MarketPrice p where p.priceDate >= ?1 order by p.priceDate desc, p.id desc")
    List<MarketPrice> since(java.time.LocalDate from);
}
