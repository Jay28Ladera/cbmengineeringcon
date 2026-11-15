package com.asmsi.admin_system.inventory;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/** What a material costs us, for pricing a quotation: the latest price we were quoted or paid, the lowest, and our stock cost. */
@Service
public class PriceHints {

    public record Hint(Material material, BigDecimal latest, String latestFrom, LocalDate latestOn,
                       BigDecimal low, String lowFrom, int priceCount) {
        /** The cost to start from: the latest market price, else what the stock cost us. */
        public BigDecimal suggested() {
            if (latest != null) return latest;
            return material.avgCost.signum() > 0 ? material.avgCost.setScale(2, java.math.RoundingMode.HALF_UP) : null;
        }
    }

    private final MaterialRepository materials;
    private final MarketPriceRepository prices;
    private final SupplierRepository suppliers;

    public PriceHints(MaterialRepository materials, MarketPriceRepository prices, SupplierRepository suppliers) {
        this.materials = materials; this.prices = prices; this.suppliers = suppliers;
    }

    /** Hints for every active material, in name order. Prices older than a year are ignored for "lowest". */
    @Transactional(readOnly = true)
    public List<Hint> activeMaterials() {
        Map<Long, String> names = new HashMap<>();
        for (Supplier s : suppliers.findAll()) names.put(s.id, s.name);
        Map<Long, List<MarketPrice>> byMaterial = new HashMap<>();
        for (MarketPrice p : prices.findAllByOrderByPriceDateDescIdDesc())   // newest first
            if (p.materialId != null) byMaterial.computeIfAbsent(p.materialId, k -> new ArrayList<>()).add(p);
        LocalDate yearAgo = LocalDate.now().minusYears(1);
        List<Hint> out = new ArrayList<>();
        for (Material m : materials.findByActiveTrueOrderByNameAsc()) {
            List<MarketPrice> ps = byMaterial.getOrDefault(m.id, List.of());
            MarketPrice latest = ps.isEmpty() ? null : ps.get(0);
            MarketPrice low = null;
            for (MarketPrice p : ps) if (!p.priceDate.isBefore(yearAgo) && (low == null || p.unitPrice.compareTo(low.unitPrice) < 0)) low = p;
            out.add(new Hint(m,
                    latest == null ? null : latest.unitPrice, latest == null ? null : names.get(latest.supplierId), latest == null ? null : latest.priceDate,
                    low == null ? null : low.unitPrice, low == null ? null : names.get(low.supplierId), ps.size()));
        }
        return out;
    }
}
