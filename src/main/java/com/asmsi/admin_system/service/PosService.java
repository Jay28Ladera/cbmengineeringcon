package com.asmsi.admin_system.service;

import com.asmsi.admin_system.entity.Item;
import com.asmsi.admin_system.entity.Sale;
import com.asmsi.admin_system.entity.SaleItem;
import com.asmsi.admin_system.repository.ItemRepository;
import com.asmsi.admin_system.repository.SaleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class PosService {

    public record CartLine(String itemNumber, int qty, BigDecimal priceFromClient) {}

    private final ItemRepository itemRepo;
    private final SaleRepository saleRepo;

    public PosService(ItemRepository itemRepo, SaleRepository saleRepo) {
        this.itemRepo = itemRepo;
        this.saleRepo = saleRepo;
    }

    @Transactional
    public Long checkout(List<CartLine> lines, BigDecimal cash, String cashierName) {
        if (lines == null || lines.isEmpty()) throw new IllegalArgumentException("Cart is empty.");

        Sale sale = new Sale();
        sale.setCashierName(cashierName == null ? "" : cashierName.trim());
        BigDecimal total = BigDecimal.ZERO;

        // Build items & compute total using DB price, decrement stocks
        for (CartLine line : lines) {
            Item item = itemRepo.findByItemNumber(line.itemNumber())
                    .orElseThrow(() -> new IllegalArgumentException("Item not found: " + line.itemNumber()));
            if (line.qty() <= 0) throw new IllegalArgumentException("Invalid qty for: " + line.itemNumber());
            if (item.getStocks() < line.qty()) {
                throw new IllegalStateException("Insufficient stocks for: " + item.getItemNumber());
            }

            BigDecimal unitPrice = item.getPrice(); // trust DB
            BigDecimal sub = unitPrice.multiply(BigDecimal.valueOf(line.qty()));

            SaleItem si = new SaleItem();
            si.setItem(item);
            si.setQuantity(line.qty());
            si.setPriceAtSale(unitPrice);
            si.setSubTotal(sub);
            sale.addItem(si);

            total = total.add(sub);

            // decrement stocks
            item.setStocks(item.getStocks() - line.qty());
            itemRepo.save(item);
        }

        sale.setTotal(total);
        if (cash == null) cash = BigDecimal.ZERO;
        if (cash.compareTo(total) < 0) throw new IllegalArgumentException("Cash is insufficient.");
        sale.setCash(cash);
        sale.setChangeDue(cash.subtract(total));

        // Save to get ID first
        sale = saleRepo.save(sale);

        // Generate receipt number using date + ID, then save again
        String datePart = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE); // YYYYMMDD
        String receiptNo = "POS-" + datePart + "-" + String.format("%06d", sale.getId());
        sale.setReceiptNo(receiptNo);
        saleRepo.save(sale);

        return sale.getId();
    }
}
