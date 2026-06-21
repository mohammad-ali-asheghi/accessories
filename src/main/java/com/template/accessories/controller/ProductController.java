package com.template.accessories.controller;

import com.template.accessories.entity.ProductEntity;
import com.template.accessories.enums.RoleEnum;
import com.template.accessories.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Objects;
import java.util.UUID;

@Controller
@RequiredArgsConstructor
public class ProductController {

    @Value("${supabase.url}")
    private String url;

    @Value("${supabase.key}")
    private String key;

    private final ProductService productService;
    private final RestTemplate restTemplate;

    @GetMapping("/products/create")
    @PreAuthorize("hasRole(T(com.template.accessories.enums.RoleEnum).ADMIN)")
    public String createProductPage(Model model) {
        model.addAttribute("product", new ProductEntity());
        model.addAttribute("isEdit", false);
        return "product-form";
    }

    @PostMapping("/products/create")
    @PreAuthorize("hasRole(T(com.template.accessories.enums.RoleEnum).ADMIN)")
    public String createProduct(@ModelAttribute ProductEntity product,
                                @RequestParam("imageFile") MultipartFile imageFile,
                                RedirectAttributes redirectAttributes) {
        product.setImageUrl(createFile(imageFile, redirectAttributes));
        productService.createOrUpdateProduct(product);
        redirectAttributes.addFlashAttribute("message", "create product success.");
        return "redirect:/";
    }

    @GetMapping("/api/products")
    @ResponseBody
    public Page<ProductEntity> getProducts(@RequestParam(defaultValue = "0") int page) {
        return productService.getProductList(page);
    }

    @GetMapping("/api/products/search")
    @ResponseBody
    public Page<ProductEntity> searchProducts(
            @RequestParam String q,
            @RequestParam(defaultValue = "0") int page) {
        return productService.searchProducts(q, page);
    }

    @GetMapping("/")
    public String index(Model model, Authentication authentication) {
        boolean isAdmin = false;
        if (authentication != null && authentication.isAuthenticated()) {
            isAdmin = authentication.getAuthorities().stream()
                    .anyMatch(a ->
                            Objects.equals(a.getAuthority(), RoleEnum.ADMIN.getAuthority())
                    );
        }
        model.addAttribute("isAdmin", isAdmin);
        return "index";
    }

    @GetMapping("/products/{id}")
    public String productDetails(@PathVariable Long id, Model model, Authentication authentication) {
        ProductEntity product = productService.getProduct(id);
        model.addAttribute("product", product);

        boolean isAdmin = false;
        if (authentication != null && authentication.isAuthenticated()) {
            isAdmin = authentication.getAuthorities().stream()
                    .anyMatch(a ->
                            Objects.equals(a.getAuthority(), RoleEnum.ADMIN.getAuthority())
                    );
        }
        model.addAttribute("isAdmin", isAdmin);

        return "product-details";
    }

    @GetMapping("/products/{id}/edit")
    @PreAuthorize("hasRole(T(com.template.accessories.enums.RoleEnum).ADMIN)")
    public String editProductPage(@PathVariable Long id, Model model) {
        ProductEntity product = productService.getProduct(id);
        model.addAttribute("product", product);
        model.addAttribute("isEdit", true);
        return "product-form";
    }

    @PostMapping("/products/{id}/edit")
    @PreAuthorize("hasRole(T(com.template.accessories.enums.RoleEnum).ADMIN)")
    public String editProduct(@PathVariable Long id,
                              @ModelAttribute ProductEntity updatedProduct,
                              @RequestParam(value = "imageFile", required = false) MultipartFile imageFile,
                              RedirectAttributes redirectAttributes) {

        ProductEntity product = productService.getProduct(id);

        product.setImageUrl(createFile(imageFile, redirectAttributes));
        product.setName(updatedProduct.getName());
        product.setDescription(updatedProduct.getDescription());
        product.setPrice(updatedProduct.getPrice());

        productService.createOrUpdateProduct(product);
        redirectAttributes.addFlashAttribute("message", "change success.");
        return "redirect:/products/" + id;
    }

    private String createFile(MultipartFile imageFile, RedirectAttributes redirectAttributes) {
        if (imageFile == null || imageFile.isEmpty()) {
            return null;
        }

        try {
            String originalName = Objects.requireNonNull(
                    imageFile.getOriginalFilename()
            ).replaceAll("\\s+", "_");
            String fileName = UUID.randomUUID() + "_" + originalName;

            String bucketName = "images";

            String uploadUrl = url + "/storage/v1/object/" + bucketName + "/" + fileName;

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(key);
            headers.setContentType(MediaType.parseMediaType(Objects.requireNonNull(imageFile.getContentType())));

            HttpEntity<byte[]> requestEntity = new HttpEntity<>(imageFile.getBytes(), headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    uploadUrl,
                    HttpMethod.POST,
                    requestEntity,
                    String.class
            );

            if (response.getStatusCode().is2xxSuccessful()) {
                return url + "/storage/v1/object/public/" + bucketName + "/" + fileName;
            } else {
                throw new RuntimeException("sup base has error!" + response.getBody());
            }

        } catch (Exception e) {
            e.fillInStackTrace();
            redirectAttributes.addFlashAttribute("message", "wrong when upload pic!" + e.getMessage());
            return null;
        }
    }

    @PostMapping("/products/{id}/delete")
    @PreAuthorize("hasRole(T(com.template.accessories.enums.RoleEnum).ADMIN)")
    @ResponseBody
    public ResponseEntity<?> deleteProduct(@PathVariable Long id) {
        ProductEntity product = productService.getProduct(id);
        deleteFileFromStorage(product.getImageUrl());
        productService.deleteProduct(id);
        return ResponseEntity.ok().build();
    }

    private void deleteFileFromStorage(String fileUrlOrName) {
        try {
            String fileName = extractFileName(fileUrlOrName);
            String bucketName = "images";

            String deleteUrl = url + "/storage/v1/object/" + bucketName + "/" + fileName;

            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + key);
            headers.set("apikey", key);

            HttpEntity<String> entity = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.exchange(deleteUrl, HttpMethod.DELETE, entity, String.class);

            System.out.println("Delete Success." + response.getStatusCode());
        } catch (Exception e) {
            System.err.println("Wrong When Delete File." + e.getMessage());
        }
    }

    private String extractFileName(String url) {
        return url.substring(url.lastIndexOf("/") + 1);
    }
}
