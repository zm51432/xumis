package com.shine.xumis.service;

import com.shine.xumis.model.Category;
import com.shine.xumis.model.Product;
import com.shine.xumis.repository.ProductRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ProductService {

    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    public List<Product> findAll() {
        return productRepository.findAll();
    }

    public List<Product> findByCategory(Category category) {
        return productRepository.findByCategory(category);
    }

    public List<Product> findFeatured() {
        return productRepository.findByFeaturedTrue();
    }

    public List<Product> findCustomizable() {
        return productRepository.findByCustomizableTrue();
    }

    public Product findById(Long id) {
        return productRepository.findById(id).orElse(null);
    }

    public Product save(Product product) {
        return productRepository.save(product);
    }
}
