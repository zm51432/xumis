package com.shine.xumis.service;

import com.shine.xumis.model.Category;
import com.shine.xumis.model.Product;
import com.shine.xumis.repository.ProductRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    /** 分页查询（手机端 API 用） */
    public Page<Product> findPage(Pageable pageable) {
        return productRepository.findAll(pageable);
    }

    public Page<Product> findPageByCategory(Category category, Pageable pageable) {
        return productRepository.findByCategory(category, pageable);
    }

    public Product findById(Long id) {
        return productRepository.findById(id).orElse(null);
    }

    public Product save(Product product) {
        return productRepository.save(product);
    }
}
