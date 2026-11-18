package com.template.accessories.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
        name = "product",
        indexes = {
                @Index(name = "idx_product_category", columnList = "category"),
                @Index(name = "idx_product_in_stock", columnList = "in_stock")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class ProductEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "description", length = 3000)
    private String description;

    @Column(name = "price")
    private Double price;

    /**
     * Denormalized primary/cover image URL for fast catalog listing without N+1 queries.
     */
    @Column(name = "image_url", length = 1000)
    private String imageUrl;

    /**
     * Denormalized image count for displaying gallery badges in catalog cards without N+1 queries.
     */
    @Column(name = "image_count", nullable = false)
    private int imageCount = 0;

    @Column(name = "category", length = 100)
    private String category;

    @Column(name = "in_stock", nullable = false)
    private boolean inStock = true;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @JsonIgnore
    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("isPrimary DESC, sortOrder ASC, id ASC")
    private List<ProductImageEntity> images = new ArrayList<>();

    public void addImage(ProductImageEntity image) {
        image.setProduct(this);
        this.images.add(image);
        syncCoverAndCount();
    }

    public void removeImage(ProductImageEntity image) {
        this.images.remove(image);
        image.setProduct(null);
        syncCoverAndCount();
    }

    /**
     * Keeps imageUrl (cover) and imageCount synchronized with the product_image table.
     */
    public void syncCoverAndCount() {
        if (this.images == null || this.images.isEmpty()) {
            this.imageCount = (this.imageUrl != null && !this.imageUrl.isBlank() && !"/icon.png".equals(this.imageUrl)) ? 1 : 0;
            if (this.imageUrl == null || this.imageUrl.isBlank()) {
                this.imageUrl = "/icon.png";
            }
            return;
        }

        boolean hasPrimary = this.images.stream().anyMatch(ProductImageEntity::isPrimary);
        if (!hasPrimary) {
            this.images.get(0).setPrimary(true);
        }

        ProductImageEntity primary = this.images.stream()
                .filter(ProductImageEntity::isPrimary)
                .findFirst()
                .orElse(this.images.get(0));

        this.imageUrl = primary.getImageUrl();
        this.imageCount = this.images.size();
    }

    /**
     * Returns all gallery image URLs for display, falling back to legacy imageUrl if images table is empty.
     */
    @Transient
    @JsonIgnore
    public List<String> getGalleryImageUrls() {
        List<String> urls = new ArrayList<>();
        if (this.images != null && !this.images.isEmpty()) {
            for (ProductImageEntity img : this.images) {
                if (img.getImageUrl() != null && !img.getImageUrl().isBlank()) {
                    urls.add(img.getImageUrl());
                }
            }
        }
        if (urls.isEmpty()) {
            urls.add((this.imageUrl != null && !this.imageUrl.isBlank()) ? this.imageUrl : "/icon.png");
        }
        return urls;
    }
}
