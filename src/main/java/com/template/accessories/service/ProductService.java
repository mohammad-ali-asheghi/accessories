package com.template.accessories.service;

import com.template.accessories.entity.ProductEntity;
import com.template.accessories.exception.ServiceException;
import com.template.accessories.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;

    @Transactional(rollbackFor = Exception.class)
    public void createOrUpdateProduct(ProductEntity entity) {
        if (entity.getInStock() == null) {
            entity.setInStock(true);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteProduct(Long id) {
        productRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public ProductEntity getProduct(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ServiceException("محصول مورد نظر یافت نشد!"));
    }

    @Transactional(readOnly = true)
    public Page<ProductEntity> getProductList(int page, int size, String category) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by("id").descending());
        if (category != null && !category.trim().isEmpty() && !category.equalsIgnoreCase("all")) {
            return productRepository.findByCategoryIgnoreCase(category.trim(), pageRequest);
        }
        return productRepository.findAll(pageRequest);
    }

    @Transactional(readOnly = true)
    public Page<ProductEntity> searchProducts(String q, int page, int size, String category) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by("id").descending());
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
}
