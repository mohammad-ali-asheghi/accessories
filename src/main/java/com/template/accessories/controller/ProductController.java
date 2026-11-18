package com.template.accessories.controller;

import com.template.accessories.entity.ProductEntity;
import com.template.accessories.service.FileStorageService;
import com.template.accessories.service.ProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Controller
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;
    private final FileStorageService fileStorageService;

    @GetMapping("/")
    public String index(Model model) {
        Page<ProductEntity> initialProducts = productService.getProductList(0, 12, null);
        model.addAttribute("products", initialProducts.getContent());
        model.addAttribute("hasMore", initialProducts.hasNext());
        model.addAttribute("categories", productService.getAllCategories());
        return "index";
    }

    @GetMapping("/api/products")
    @ResponseBody
    public Page<ProductEntity> getProducts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size,
            @RequestParam(required = false) String category) {
        return productService.getProductList(page, size, category);
    }

    @GetMapping("/api/products/search")
    @ResponseBody
    public Page<ProductEntity> searchProducts(
            @RequestParam(required = false, defaultValue = "") String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size,
            @RequestParam(required = false) String category) {
        return productService.searchProducts(q, page, size, category);
    }

    @GetMapping("/api/products/categories")
    @ResponseBody
    public List<String> getCategories() {
        return productService.getAllCategories();
    }

    @GetMapping("/products/{id:[0-9]+}")
    public String productDetails(@PathVariable Long id, Model model) {
        ProductEntity product = productService.getProductWithImages(id);
        model.addAttribute("product", product);
        model.addAttribute("galleryImages", product.getGalleryImageUrls());
        return "product-details";
    }

    @GetMapping("/products/create")
    @PreAuthorize("hasRole('ADMIN')")
    public String createProductPage(Model model) {
        ProductEntity product = new ProductEntity();
        product.setInStock(true);
        model.addAttribute("product", product);
        model.addAttribute("isEdit", false);
        model.addAttribute("categories", productService.getAllCategories());
        return "product-form";
    }

    @PostMapping("/products/create")
    @PreAuthorize("hasRole('ADMIN')")
    public String createProduct(@ModelAttribute ProductEntity product,
                                @RequestParam(value = "imageFiles", required = false) List<MultipartFile> imageFiles,
                                @RequestParam(value = "directImageUrls", required = false) String directImageUrls,
                                RedirectAttributes redirectAttributes) {
        try {
            validateProductInput(product);

            List<String> allImageUrls = new ArrayList<>();
            allImageUrls.addAll(fileStorageService.storeFiles(imageFiles));
            allImageUrls.addAll(parseDirectUrls(directImageUrls));

            ProductEntity saved = productService.createOrUpdateProduct(product);
            saved = productService.addImagesToProduct(saved, allImageUrls);

            log.info("Successfully created product: id={}, name={}, images={}, inStock={}",
                    saved.getId(), saved.getName(), saved.getImageCount(), saved.isInStock());
            redirectAttributes.addFlashAttribute("successMessage", "محصول «" + saved.getName() + "» با موفقیت افزوده شد.");
            return "redirect:/admin";
        } catch (Exception e) {
            log.error("Error creating product", e);
            redirectAttributes.addFlashAttribute("errorMessage", "خطا در ایجاد محصول: " + e.getMessage());
            return "redirect:/products/create";
        }
    }

    @GetMapping("/products/{id:[0-9]+}/edit")
    @PreAuthorize("hasRole('ADMIN')")
    public String editProductPage(@PathVariable Long id, Model model) {
        ProductEntity product = productService.getProductWithImages(id);
        model.addAttribute("product", product);
        model.addAttribute("isEdit", true);
        model.addAttribute("categories", productService.getAllCategories());
        return "product-form";
    }

    @PostMapping("/products/{id:[0-9]+}/edit")
    @PreAuthorize("hasRole('ADMIN')")
    public String editProduct(@PathVariable Long id,
                              @ModelAttribute ProductEntity updatedProduct,
                              @RequestParam(value = "imageFiles", required = false) List<MultipartFile> imageFiles,
                              @RequestParam(value = "directImageUrls", required = false) String directImageUrls,
                              RedirectAttributes redirectAttributes) {
        try {
            validateProductInput(updatedProduct);
            ProductEntity product = productService.getProductWithImages(id);

            product.setName(updatedProduct.getName().trim());
            product.setDescription(updatedProduct.getDescription());
            product.setPrice(updatedProduct.getPrice());
            product.setCategory(updatedProduct.getCategory() != null ? updatedProduct.getCategory().trim() : null);
            product.setInStock(updatedProduct.isInStock());

            List<String> newImageUrls = new ArrayList<>();
            newImageUrls.addAll(fileStorageService.storeFiles(imageFiles));
            newImageUrls.addAll(parseDirectUrls(directImageUrls));

            ProductEntity saved = productService.addImagesToProduct(product, newImageUrls);
            log.info("Successfully updated product: id={}, name={}, images={}, inStock={}",
                    saved.getId(), saved.getName(), saved.getImageCount(), saved.isInStock());
            redirectAttributes.addFlashAttribute("successMessage", "تغییرات محصول با موفقیت ذخیره شد.");
            return "redirect:/admin";
        } catch (Exception e) {
            log.error("Error updating product {}", id, e);
            redirectAttributes.addFlashAttribute("errorMessage", "خطا در ویرایش محصول: " + e.getMessage());
            return "redirect:/products/" + id + "/edit";
        }
    }

    @PostMapping("/products/{id:[0-9]+}/images/{imageId:[0-9]+}/primary")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> setPrimaryImage(@PathVariable Long id, @PathVariable Long imageId) {
        try {
            ProductEntity updated = productService.setPrimaryImage(id, imageId);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "imageUrl", updated.getImageUrl()
            ));
        } catch (Exception e) {
            log.error("Error setting primary image {} for product {}", imageId, id, e);
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @PostMapping("/products/{id:[0-9]+}/images/{imageId:[0-9]+}/delete")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> deleteProductImage(@PathVariable Long id, @PathVariable Long imageId) {
        try {
            ProductEntity updated = productService.deleteProductImage(id, imageId);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "imageCount", updated.getImageCount(),
                    "imageUrl", updated.getImageUrl()
            ));
        } catch (Exception e) {
            log.error("Error deleting image {} for product {}", imageId, id, e);
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @PostMapping("/products/{id:[0-9]+}/delete")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> deleteProduct(@PathVariable Long id) {
        try {
            productService.deleteProduct(id);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            log.error("Error deleting product {}", id, e);
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @PostMapping("/products/{id:[0-9]+}/toggle-stock")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> toggleStock(@PathVariable Long id) {
        try {
            ProductEntity updated = productService.toggleStock(id);
            return ResponseEntity.ok(Map.of("success", true, "inStock", updated.isInStock()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    private void validateProductInput(ProductEntity product) {
        if (product.getName() == null || product.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("نام محصول نمی‌تواند خالی باشد.");
        }
        if (product.getPrice() != null && product.getPrice() < 0) {
            throw new IllegalArgumentException("قیمت محصول نمی‌تواند منفی باشد.");
        }
    }

    private List<String> parseDirectUrls(String rawUrls) {
        List<String> urls = new ArrayList<>();
        if (rawUrls == null || rawUrls.isBlank()) {
            return urls;
        }
        String[] lines = rawUrls.split("\\r?\\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty() && (trimmed.startsWith("http://") || trimmed.startsWith("https://") || trimmed.startsWith("/"))) {
                urls.add(trimmed);
            }
        }
        return urls;
    }
}
