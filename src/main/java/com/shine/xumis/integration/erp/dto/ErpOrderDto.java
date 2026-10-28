package com.shine.xumis.integration.erp.dto;

import com.shine.xumis.model.Order;
import com.shine.xumis.model.OrderItem;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 订单 -> ERP 报文（预留对接用，字段可随目标 ERP 调整）。
 */
public class ErpOrderDto {
    public String orderNo;
    public String customerName;
    public String email;
    public String phone;
    public String country;
    public String address;
    public String currency;
    public String status;
    public java.math.BigDecimal totalAmount;
    public List<ErpOrderItemDto> items;

    public static ErpOrderDto from(Order o) {
        ErpOrderDto d = new ErpOrderDto();
        d.orderNo = o.getOrderNo();
        d.customerName = o.getCustomerName();
        d.email = o.getEmail();
        d.phone = o.getPhone();
        d.country = o.getCountry();
        d.address = o.getAddress();
        d.currency = o.getCurrency();
        d.status = o.getStatus();
        d.totalAmount = o.getTotalAmount();
        d.items = o.getItems().stream().map(i -> {
            ErpOrderItemDto it = new ErpOrderItemDto();
            it.productId = i.getProductId();
            it.productName = i.getProductName();
            it.price = i.getPrice();
            it.quantity = i.getQuantity();
            return it;
        }).collect(Collectors.toList());
        return d;
    }

    public static class ErpOrderItemDto {
        public Long productId;
        public String productName;
        public java.math.BigDecimal price;
        public Integer quantity;
    }
}
