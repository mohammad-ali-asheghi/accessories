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

import java.util.List;

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
        ProductEntity product = productService.getProduct(id);
        model.addAttribute("product", product);
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
                                @RequestParam(value = "imageFile", required = false) MultipartFile imageFile,
                                @RequestParam(value = "directImageUrl", required = false) String directImageUrl,
                                RedirectAttributes redirectAttributes) {
        try {
            String uploadedUrl = fileStorageService.storeFile(imageFile);
            if (uploadedUrl != null && !uploadedUrl.isBlank()) {
                product.setImageUrl(uploadedUrl);
            } else if (directImageUrl != null && !directImageUrl.isBlank()) {
                product.setImageUrl(directImageUrl.trim());
            }

            if (product.getImageUrl() == null || product.getImageUrl().isBlank()) {
                product.setImageUrl("/icon.png"); // Default fallback image
            }

            ProductEntity saved = productService.createOrUpdateProduct(product);
            log.info("Successfully created product: id={}, name={}, inStock={}", saved.getId(), saved.getName(), saved.isInStock());
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
        ProductEntity product = productService.getProduct(id);
        model.addAttribute("product", product);
        model.addAttribute("isEdit", true);
        model.addAttribute("categories", productService.getAllCategories());
        return "product-form";
    }

    @PostMapping("/products/{id:[0-9]+}/edit")
    @PreAuthorize("hasRole('ADMIN')")
    public String editProduct(@PathVariable Long id,
                              @ModelAttribute ProductEntity updatedProduct,
                              @RequestParam(value = "imageFile", required = false) MultipartFile imageFile,
                              @RequestParam(value = "directImageUrl", required = false) String directImageUrl,
                              RedirectAttributes redirectAttributes) {
        try {
            ProductEntity product = productService.getProduct(id);

            // Bug fix: only update imageUrl if a new file or direct URL was supplied
            String newUploadedUrl = fileStorageService.storeFile(imageFile);
            if (newUploadedUrl != null && !newUploadedUrl.isBlank()) {
                // Delete old file if present
                fileStorageService.deleteFile(product.getImageUrl());
                product.setImageUrl(newUploadedUrl);
            } else if (directImageUrl != null && !directImageUrl.isBlank()) {
                product.setImageUrl(directImageUrl.trim());
            }
            // else: keep existing product.getImageUrl()!

            product.setName(updatedProduct.getName());
            product.setDescription(updatedProduct.getDescription());
            product.setPrice(updatedProduct.getPrice());
            product.setCategory(updatedProduct.getCategory());
            product.setInStock(updatedProduct.isInStock());

            ProductEntity saved = productService.createOrUpdateProduct(product);
            log.info("Successfully updated product: id={}, name={}, inStock={}", saved.getId(), saved.getName(), saved.isInStock());
            redirectAttributes.addFlashAttribute("successMessage", "تغییرات محصول با موفقیت ذخیره شد.");
            return "redirect:/admin";
        } catch (Exception e) {
            log.error("Error updating product {}", id, e);
            redirectAttributes.addFlashAttribute("errorMessage", "خطا در ویرایش محصول: " + e.getMessage());
            return "redirect:/products/" + id + "/edit";
        }
    }

    @PostMapping("/products/{id:[0-9]+}/delete")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseBody
    public ResponseEntity<?> deleteProduct(@PathVariable Long id) {
        try {
            ProductEntity product = productService.getProduct(id);
            if (product.getImageUrl() != null) {
                fileStorageService.deleteFile(product.getImageUrl());
            }
            productService.deleteProduct(id);
            return ResponseEntity.ok().body("{\"success\":true}");
        } catch (Exception e) {
            log.error("Error deleting product {}", id, e);
            return ResponseEntity.badRequest().body("{\"success\":false,\"message\":\"" + e.getMessage() + "\"}");
        }
    }

    @PostMapping("/products/{id:[0-9]+}/toggle-stock")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseBody
    public ResponseEntity<?> toggleStock(@PathVariable Long id) {
        try {
            ProductEntity updated = productService.toggleStock(id);
            return ResponseEntity.ok().body("{\"success\":true,\"inStock\":" + updated.isInStock() + "}");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("{\"success\":false,\"message\":\"" + e.getMessage() + "\"}");
        }
    }
}
