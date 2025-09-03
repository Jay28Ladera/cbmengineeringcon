package com.asmsi.admin_system.repository;

import com.asmsi.admin_system.entity.Item;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ItemRepository extends JpaRepository<Item, Long> {
    Optional<Item> findByItemNumber(String itemNumber);
    boolean existsByItemNumber(String itemNumber);
}
