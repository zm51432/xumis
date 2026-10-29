package com.shine.xumis.dto;

import com.shine.xumis.model.Product;

import java.util.List;

/**
 * 商品搜索结果（含分页信息），供前台列表页与手机端 API 共用。
 */
public class ProductSearchResult {
    private final List<Product> items;
    private final int page;
    private final int totalPages;
    private final long totalItems;

    public ProductSearchResult(List<Product> items, int page, int totalPages, long totalItems) {
        this.items = items;
        this.page = page;
        this.totalPages = totalPages;
        this.totalItems = totalItems;
    }

    public List<Product> getItems() { return items; }
    public int getPage() { return page; }
    public int getTotalPages() { return totalPages; }
    public long getTotalItems() { return totalItems; }
}
