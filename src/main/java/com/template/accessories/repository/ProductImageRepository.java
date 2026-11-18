package com.template.accessories.repository;

import com.template.accessories.entity.ProductImageEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductImageRepository extends JpaRepository<ProductImageEntity, Long> {

    List<ProductImageEntity> findByProductIdOrderByIsPrimaryDescSortOrderAscIdAsc(Long productId);

    Optional<ProductImageEntity> findByIdAndProductId(Long id, Long productId);
}
