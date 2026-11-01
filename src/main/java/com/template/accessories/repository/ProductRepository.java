package com.template.accessories.repository;

import com.template.accessories.entity.ProductEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductRepository extends JpaRepository<ProductEntity, Long> {

    Page<ProductEntity> findByNameContainingIgnoreCase(String name, Pageable pageable);

    Page<ProductEntity> findByCategoryIgnoreCase(String category, Pageable pageable);

    Page<ProductEntity> findByCategoryIgnoreCaseAndNameContainingIgnoreCase(String category, String name, Pageable pageable);

    @Query("SELECT DISTINCT p.category FROM ProductEntity p WHERE p.category IS NOT NULL AND TRIM(p.category) <> '' ORDER BY p.category ASC")
    List<String> findDistinctCategories();

    long countByInStockTrue();

    long countByInStockFalse();
}
