package com.template.accessories.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "product_image",
        indexes = {
                @Index(name = "idx_product_image_product_id", columnList = "product_id"),
                @Index(name = "idx_product_image_primary", columnList = "product_id, is_primary")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class ProductImageEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private ProductEntity product;

    @Column(name = "image_url", nullable = false, length = 1000)
    private String imageUrl;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;

    @Column(name = "is_primary", nullable = false)
    private boolean isPrimary = false;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public ProductImageEntity(ProductEntity product, String imageUrl, int sortOrder, boolean isPrimary) {
        this.product = product;
        this.imageUrl = imageUrl;
        this.sortOrder = sortOrder;
        this.isPrimary = isPrimary;
        this.createdAt = LocalDateTime.now();
    }
}
