package com.shine.xumis.controller;

import com.shine.xumis.model.Category;
import com.shine.xumis.model.Product;
import com.shine.xumis.service.ProductService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * 店铺前台页面（Thymeleaf 视图）
 */
@Controller
public class StorefrontController {

    private final ProductService productService;

    public StorefrontController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("featured", productService.findFeatured());
        model.addAttribute("customizable", productService.findCustomizable());
        return "home";
    }

    @GetMapping("/products")
    public String products(@RequestParam(value = "cat", required = false) Category cat, Model model) {
        List<Product> list;
        if (cat != null) {
            list = productService.findByCategory(cat);
            model.addAttribute("activeCat", cat);
        } else {
            list = productService.findAll();
        }
        model.addAttribute("products", list);
        return "products";
    }

    @GetMapping("/product/{id}")
    public String detail(@PathVariable Long id, Model model) {
        Product p = productService.findById(id);
        if (p == null) {
            return "redirect:/products";
        }
        model.addAttribute("product", p);
        return "product-detail";
    }
}
