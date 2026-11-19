package com.template.accessories.service;

import com.template.accessories.entity.ProductEntity;
import com.template.accessories.entity.ProductImageEntity;
import com.template.accessories.exception.ServiceException;
import com.template.accessories.repository.ProductImageRepository;
import com.template.accessories.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductService {

    private static final int MAX_PAGE_SIZE = 50;

    private final ProductRepository productRepository;
    private final ProductImageRepository productImageRepository;
    private final FileStorageService fileStorageService;

    @Transactional(rollbackFor = Exception.class)
    public ProductEntity createOrUpdateProduct(ProductEntity entity) {
        if (entity.getCreatedAt() == null) {
            entity.setCreatedAt(LocalDateTime.now());
        }
        entity.syncCoverAndCount();
        return productRepository.save(entity);
    }

    @Transactional(rollbackFor = Exception.class)
    public ProductEntity addImagesToProduct(ProductEntity product, List<String> imageUrls) {
        if (imageUrls == null || imageUrls.isEmpty()) {
            product.syncCoverAndCount();
            return productRepository.save(product);
        }

        // If product had a legacy imageUrl not yet in product_image table, preserve it first
        if (product.getImages().isEmpty()
                && product.getImageUrl() != null
                && !product.getImageUrl().isBlank()
                && !"/icon.png".equals(product.getImageUrl())) {
            ProductImageEntity legacy = new ProductImageEntity(product, product.getImageUrl(), 0, true);
            product.getImages().add(legacy);
        }

        int nextOrder = product.getImages().size();
        boolean hasPrimary = product.getImages().stream().anyMatch(ProductImageEntity::isPrimary);

        for (String url : imageUrls) {
            if (url == null || url.isBlank()) continue;
            String cleanUrl = url.trim();

            boolean alreadyExists = product.getImages().stream()
                    .anyMatch(img -> cleanUrl.equalsIgnoreCase(img.getImageUrl()));
            if (alreadyExists) continue;

            boolean makePrimary = !hasPrimary;
            ProductImageEntity img = new ProductImageEntity(product, cleanUrl, nextOrder++, makePrimary);
            product.getImages().add(img);
            if (makePrimary) {
                hasPrimary = true;
            }
        }

        product.syncCoverAndCount();
        return productRepository.save(product);
    }

    @Transactional(rollbackFor = Exception.class)
    public ProductEntity setPrimaryImage(Long productId, Long imageId) {
        ProductEntity product = getProductWithImages(productId);
        boolean found = false;
        for (ProductImageEntity img : product.getImages()) {
            boolean isTarget = img.getId().equals(imageId);
            img.setPrimary(isTarget);
            if (isTarget) {
                found = true;
            }
        }
        if (!found) {
            throw new ServiceException("تصویر مورد نظر برای این محصول یافت نشد.");
        }
        product.syncCoverAndCount();
        return productRepository.save(product);
    }

    @Transactional(rollbackFor = Exception.class)
    public ProductEntity deleteProductImage(Long productId, Long imageId) {
        ProductEntity product = getProductWithImages(productId);
        ProductImageEntity target = productImageRepository.findByIdAndProductId(imageId, productId)
                .orElseThrow(() -> new ServiceException("تصویر مورد نظر یافت نشد."));

        String urlToDelete = target.getImageUrl();
        product.getImages().removeIf(img -> img.getId().equals(imageId));
        productImageRepository.delete(target);

        if (product.getImages().isEmpty()) {
            product.setImageUrl("/icon.png");
        }
        product.syncCoverAndCount();
        ProductEntity saved = productRepository.save(product);

        fileStorageService.deleteFile(urlToDelete);
        return saved;
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteProduct(Long id) {
        ProductEntity product = getProductWithImages(id);
        List<String> urlsToDelete = new ArrayList<>();
        if (product.getImages() != null) {
            for (ProductImageEntity img : product.getImages()) {
                urlsToDelete.add(img.getImageUrl());
            }
        }
        if (product.getImageUrl() != null && !urlsToDelete.contains(product.getImageUrl())) {
            urlsToDelete.add(product.getImageUrl());
        }

        productRepository.delete(product);

        for (String url : urlsToDelete) {
            fileStorageService.deleteFile(url);
        }
    }

    @Transactional(readOnly = true)
    public ProductEntity getProduct(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ServiceException("محصول مورد نظر یافت نشد!"));
    }

    @Transactional(readOnly = true)
    public ProductEntity getProductWithImages(Long id) {
        return productRepository.findWithImagesById(id)
                .orElseThrow(() -> new ServiceException("محصول مورد نظر یافت نشد!"));
    }

    @Transactional(readOnly = true)
    public Page<ProductEntity> getProductList(int page, int size, String category) {
        PageRequest pageRequest = buildPageRequest(page, size);
        if (category != null && !category.trim().isEmpty() && !category.equalsIgnoreCase("all")) {
            return productRepository.findByCategoryIgnoreCase(category.trim(), pageRequest);
        }
        return productRepository.findAll(pageRequest);
    }

    @Transactional(readOnly = true)
    public Page<ProductEntity> searchProducts(String q, int page, int size, String category) {
        PageRequest pageRequest = buildPageRequest(page, size);
        boolean hasCategory = category != null && !category.trim().isEmpty() && !category.equalsIgnoreCase("all");
        boolean hasQuery = q != null && !q.trim().isEmpty();

        if (hasCategory && hasQuery) {
            return productRepository.findByCategoryIgnoreCaseAndNameContainingIgnoreCase(category.trim(), q.trim(), pageRequest);
        } else if (hasCategory) {
            return productRepository.findByCategoryIgnoreCase(category.trim(), pageRequest);
        } else if (hasQuery) {
            return productRepository.findByNameContainingIgnoreCase(q.trim(), pageRequest);
        } else {
            return productRepository.findAll(pageRequest);
        }
    }

    @Transactional(readOnly = true)
    public List<String> getAllCategories() {
        return productRepository.findDistinctCategories();
    }

    @Transactional(readOnly = true)
    public long countTotal() {
        return productRepository.count();
    }

    @Transactional(readOnly = true)
    public long countInStock() {
        return productRepository.countByInStockTrue();
    }

    @Transactional(readOnly = true)
    public long countOutOfStock() {
        return productRepository.countByInStockFalse();
    }

    @Transactional(rollbackFor = Exception.class)
    public ProductEntity toggleStock(Long id) {
        ProductEntity product = getProduct(id);
        product.setInStock(!product.isInStock());
        return productRepository.save(product);
    }

    private PageRequest buildPageRequest(int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), MAX_PAGE_SIZE);
        return PageRequest.of(safePage, safeSize, Sort.by("id").descending());
    }
}
