package com.shine.xumis.integration.erp.dto;

import com.shine.xumis.model.Order;
import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

/** 订单 -> ERP 报文（含折扣、物流、SKU 快照，2026-11-05 补全）。 */
public class ErpOrderDto {
    public String orderNo;
    public String customerName;
    public String email;
    public String phone;
    public String country;
    public String address;
    public String note;
    public String currency;
    public String status;
    public String paymentMethod;
    public BigDecimal subtotalAmount;
    public BigDecimal discountAmount;
    public BigDecimal shippingFee;
    public BigDecimal taxAmount;
    public BigDecimal totalAmount;
    public String couponCode;
    public String trackingNo;
    public String carrier;
    public String trackingUrl;
    public String placedAt;
    public String shippedAt;
    public List<ErpOrderItemDto> items;

    public static ErpOrderDto from(Order o) {
        ErpOrderDto d = new ErpOrderDto();
        d.orderNo = o.getOrderNo();
        d.customerName = o.getCustomerName();
        d.email = o.getEmail();
        d.phone = o.getPhone();
        d.country = o.getCountry();
        d.address = o.getAddress();
        d.note = o.getNote();
        d.currency = o.getCurrency();
        d.status = o.getStatus();
        d.paymentMethod = o.getPaymentMethod();
        d.subtotalAmount = o.getSubtotalAmount();
        d.discountAmount = o.getDiscountAmount();
        d.shippingFee = o.getShippingFee();
        d.taxAmount = o.getTaxAmount();
        d.totalAmount = o.getTotalAmount();
        d.couponCode = o.getCouponCode();
        d.trackingNo = o.getTrackingNo();
        d.carrier = o.getCarrier();
        d.trackingUrl = o.getTrackingUrl();
        d.placedAt = o.getCreatedAt() == null ? null : o.getCreatedAt().toString();
        d.shippedAt = o.getShippedAt() == null ? null : o.getShippedAt().toString();
        try {
            d.items = o.getItems() == null ? List.of() : o.getItems().stream().map(i -> {
            ErpOrderItemDto it = new ErpOrderItemDto();
            it.productId = i.getProductId();
            it.productName = i.getProductName();
            it.productNameEn = i.getProductNameEn();
            it.imageUrl = i.getImageUrl();
            it.price = i.getPrice();
            it.quantity = i.getQuantity();
            it.skuId = i.getSkuId();
            it.skuLabel = i.getSkuLabel();
                return it;
            }).collect(Collectors.toList());
        } catch (Exception e) {
            // 脱离事务的实体无法读明细时，只推主表字段，绝不阻塞业务
            d.items = List.of();
        }
        return d;
    }

    public static class ErpOrderItemDto {
        public Long productId;
        public String productName;
        public String productNameEn;
        public String imageUrl;
        public BigDecimal price;
        public Integer quantity;
        public Long skuId;
        public String skuLabel;
    }
}
