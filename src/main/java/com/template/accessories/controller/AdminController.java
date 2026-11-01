package com.template.accessories.controller;

import com.template.accessories.entity.ProductEntity;
import com.template.accessories.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final ProductService productService;

    @GetMapping
    public String adminDashboard(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String category,
            Model model) {

        Page<ProductEntity> productPage;
        if (q != null && !q.trim().isEmpty()) {
            productPage = productService.searchProducts(q, page, size, category);
        } else {
            productPage = productService.getProductList(page, size, category);
        }

        model.addAttribute("products", productPage.getContent());
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", productPage.getTotalPages());
        model.addAttribute("totalElements", productPage.getTotalElements());
        model.addAttribute("searchQuery", q);
        model.addAttribute("selectedCategory", category);

        // Stats
        model.addAttribute("totalCount", productService.countTotal());
        model.addAttribute("inStockCount", productService.countInStock());
        model.addAttribute("outOfStockCount", productService.countOutOfStock());
        model.addAttribute("categories", productService.getAllCategories());

        return "admin";
    }
}
