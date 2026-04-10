package com.asmsi.admin_system.repository;

import com.asmsi.admin_system.dto.SchoolItemDetailDTO;
import com.asmsi.admin_system.dto.SchoolItemLiteDTO;
import com.asmsi.admin_system.entity.SchoolItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;


import java.util.List;
import java.util.Optional;

@Repository
public interface SchoolItemRepository extends JpaRepository<SchoolItem, Long> {

    boolean existsByItemCode(String itemCode);

    // your existing methods
    List<SchoolItem> findByShops(String shops);
    List<SchoolItem> findByShopsContainingIgnoreCase(String shops);

    // =========================================================
    // ✅ FAST LIST for DataTables (Lite DTO)
    // =========================================================
    @Query("""
        select new com.asmsi.admin_system.dto.SchoolItemLiteDTO(
            s.id,
            s.imageUrl,
            s.itemCode,
            s.itemName,
            s.quantityOnHand,
            s.shops,
            s.status
        )
        from SchoolItem s
        where (:shop is null or :shop = '' or lower(s.shops) like lower(concat('%', :shop, '%')))
          and (
              :search is null or :search = '' or
              lower(s.itemCode) like lower(concat('%', :search, '%')) or
              lower(s.itemName) like lower(concat('%', :search, '%')) or
              lower(s.shops) like lower(concat('%', :search, '%')) or
              lower(s.status) like lower(concat('%', :search, '%'))
          )
    """)
    Page<SchoolItemLiteDTO> datatablesLite(
            @Param("shop") String shop,
            @Param("search") String search,
            Pageable pageable
    );

    @Query("""
        select count(s)
        from SchoolItem s
        where (:shop is null or :shop = '' or lower(s.shops) like lower(concat('%', :shop, '%')))
    """)
    long countAllFiltered(@Param("shop") String shop);

    @Query("""
        select count(s)
        from SchoolItem s
        where (:shop is null or :shop = '' or lower(s.shops) like lower(concat('%', :shop, '%')))
          and (
              :search is null or :search = '' or
              lower(s.itemCode) like lower(concat('%', :search, '%')) or
              lower(s.itemName) like lower(concat('%', :search, '%')) or
              lower(s.shops) like lower(concat('%', :search, '%')) or
              lower(s.status) like lower(concat('%', :search, '%'))
          )
    """)
    long countSearchFiltered(@Param("shop") String shop, @Param("search") String search);

    // =========================================================
    // ✅ DETAIL endpoint (Full DTO) — avoids LOB stream errors
    // =========================================================
    @Query("""
        select new com.asmsi.admin_system.dto.SchoolItemDetailDTO(
            s.id,
            s.imageUrl,
            s.itemCode,
            s.itemName,
            s.description,
            s.specification,
            s.brandModel,
            s.quantityOnHand,
            s.category,
            s.condition,
            s.status,
            s.shops,
            s.supplier,
            case when s.purchaseDate is null then null else cast(s.purchaseDate as string) end,
            s.purchaseCost
        )
        from SchoolItem s
        where s.id = :id
    """)
    Optional<SchoolItemDetailDTO> findDetailById(@Param("id") Long id);


      @Query("""
      SELECT s FROM SchoolItem s
      WHERE
        (:filterShop IS NULL OR :filterShop = '' OR LOWER(s.shops) = LOWER(:filterShop))
        AND (:filterCategory IS NULL OR :filterCategory = '' OR LOWER(s.category) = LOWER(:filterCategory))
        AND (:filterStatus IS NULL OR :filterStatus = '' OR LOWER(s.status) = LOWER(:filterStatus))
        AND (
          :search IS NULL OR :search = '' OR
          LOWER(s.itemCode) LIKE LOWER(CONCAT('%', :search, '%')) OR
          LOWER(s.itemName) LIKE LOWER(CONCAT('%', :search, '%')) OR
          LOWER(s.shops) LIKE LOWER(CONCAT('%', :search, '%')) OR
          LOWER(s.category) LIKE LOWER(CONCAT('%', :search, '%')) OR
          LOWER(s.status) LIKE LOWER(CONCAT('%', :search, '%')) OR
          LOWER(s.specification) LIKE LOWER(CONCAT('%', :search, '%')) OR
          LOWER(s.description) LIKE LOWER(CONCAT('%', :search, '%'))
        )
    """)
    Page<SchoolItem> findForDataTable(
            @Param("search") String search,
            @Param("filterShop") String filterShop,
            @Param("filterCategory") String filterCategory,
            @Param("filterStatus") String filterStatus,
            Pageable pageable
    );

      @Query("""
        SELECT s FROM SchoolItem s
        WHERE (:shop IS NULL OR :shop = '' OR LOWER(s.shops) = LOWER(:shop))
          AND (:category IS NULL OR :category = '' OR LOWER(s.category) = LOWER(:category))
          AND (:status IS NULL OR :status = '' OR LOWER(s.status) = LOWER(:status))
          AND (
                :q IS NULL OR :q = '' OR
                LOWER(s.itemCode) LIKE LOWER(CONCAT('%', :q, '%')) OR
                LOWER(s.itemName) LIKE LOWER(CONCAT('%', :q, '%')) OR
                LOWER(s.shops) LIKE LOWER(CONCAT('%', :q, '%')) OR
                LOWER(s.category) LIKE LOWER(CONCAT('%', :q, '%')) OR
                LOWER(s.status) LIKE LOWER(CONCAT('%', :q, '%')) OR
                LOWER(COALESCE(s.specification,'')) LIKE LOWER(CONCAT('%', :q, '%')) OR
                LOWER(COALESCE(s.description,'')) LIKE LOWER(CONCAT('%', :q, '%'))
          )
        """)
    Page<SchoolItem> searchFiltered(
        @Param("q") String q,
        @Param("shop") String shop,
        @Param("category") String category,
        @Param("status") String status,
        Pageable pageable
    );

    @Query("""
        SELECT COUNT(s) FROM SchoolItem s
        WHERE (:shop IS NULL OR :shop = '' OR LOWER(s.shops) = LOWER(:shop))
          AND (:category IS NULL OR :category = '' OR LOWER(s.category) = LOWER(:category))
          AND (:status IS NULL OR :status = '' OR LOWER(s.status) = LOWER(:status))
        """)
    long countFiltered(
        @Param("shop") String shop,
        @Param("category") String category,
        @Param("status") String status
    );


    Optional<SchoolItem> findByItemCode(String itemCode); // ✅ ADD (for upsert)
    Optional<SchoolItem> findByItemCodeIgnoreCase(String itemCode);


    @Query("""
  select s.id
  from SchoolItem s
  where (:shop is null or s.shops = :shop)
    and (:category is null or s.category = :category)
    and (:status is null or s.status = :status)
    and (
      :searchLike is null
      or lower(s.itemCode) like :searchLike
      or lower(s.itemName) like :searchLike
      or lower(coalesce(s.shops,'')) like :searchLike
      or lower(coalesce(s.category,'')) like :searchLike
      or lower(coalesce(s.status,'')) like :searchLike
      or lower(coalesce(s.description,'')) like :searchLike
      or lower(coalesce(s.specification,'')) like :searchLike
      or lower(coalesce(s.brandModel,'')) like :searchLike
      or lower(coalesce(s.supplier,'')) like :searchLike
    )
  order by s.itemName asc
""")
List<Long> findIdsForInventoryWizard(
    @Param("shop") String shop,
    @Param("category") String category,
    @Param("status") String status,
    @Param("searchLike") String searchLike
);


}
