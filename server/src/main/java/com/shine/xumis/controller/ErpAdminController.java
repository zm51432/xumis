package com.shine.xumis.controller;

import com.shine.xumis.integration.erp.ErpSyncService;
import com.shine.xumis.service.ProductService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * ERP 管理接口（预留）：手动触发商品全量同步。
 * 受 app.admin-key 保护，生产环境请叠加正式鉴权。
 */
@RestController
@RequestMapping("/api/erp")
public class ErpAdminController {

    private final ProductService productService;
    private final ErpSyncService erpSyncService;

    @Value("${app.admin-key:admin}")
    private String adminKey;

    public ErpAdminController(ProductService productService, ErpSyncService erpSyncService) {
        this.productService = productService;
        this.erpSyncService = erpSyncService;
    }

    @PostMapping("/sync-products")
    public ResponseEntity<?> syncProducts(@RequestParam(value = "key", required = false) String key) {
        if (key == null || !key.equals(adminKey)) {
            return ResponseEntity.status(401).body(Map.of("error", "unauthorized"));
        }
        erpSyncService.syncAllProducts(productService.findAll());
        return ResponseEntity.ok(Map.of("ok", true, "synced", productService.findAll().size()));
    }
}
