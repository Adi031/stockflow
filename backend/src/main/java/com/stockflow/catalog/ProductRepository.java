package com.stockflow.catalog;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface ProductRepository extends JpaRepository<Product, Long> {

    List<Product> findBySellerId(Long sellerId);

    @Query("""
        SELECT p FROM Product p
        WHERE p.status = 'ACTIVE'
          AND (:category IS NULL OR p.category = :category)
          AND (:q IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', :q, '%')))
        """)
    Page<Product> search(@Param("q") String query, @Param("category") String category, Pageable pageable);
}
