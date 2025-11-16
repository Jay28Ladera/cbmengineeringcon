package com.asmsi.admin_system.repository;

import com.asmsi.admin_system.entity.SchoolItem;
import com.asmsi.admin_system.entity.Shop;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ShopRepository extends JpaRepository<Shop, Long> {
   
}
