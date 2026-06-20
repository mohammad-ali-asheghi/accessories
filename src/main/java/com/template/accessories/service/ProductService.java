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

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;

    @Transactional(rollbackFor = Exception.class)
    public void createOrUpdateProduct(ProductEntity entity) {
        productRepository.save(entity);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteProduct(Long id) {
        productRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public ProductEntity getProduct(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ServiceException("Product not found!"));
    }

    @Transactional(readOnly = true)
    public Page<ProductEntity> getProductList(int page) {
        PageRequest pageRequest = PageRequest.of(page, 25, Sort.by("id").descending());
        return productRepository.findAll(pageRequest);
    }

    @Transactional(readOnly = true)
    public Page<ProductEntity> searchProducts(String q, int page) {
        PageRequest pageRequest = PageRequest.of(page, 25, Sort.by("id").descending());
        return productRepository.findByNameContainingIgnoreCase(q, pageRequest);
    }

}
