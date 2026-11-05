package com.shine.xumis.service;

import com.shine.xumis.model.Order;
import com.shine.xumis.model.OrderItem;
import com.shine.xumis.repository.OrderRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/** 订单状态邮件（P0-2）：下单成功 / 付款 / 发货 / 完成 / 取消，异步发送，后台可开关。 */
@Service
public class OrderMailService {

    private final MailService mail;
    private final SettingsService settings;
    private final OrderRepository orderRepository;

    @Value("${app.site-base-url:https://shine.xumois.cn}")
    private String siteBaseUrl;

    public OrderMailService(MailService mail, SettingsService settings, OrderRepository orderRepository) {
        this.mail = mail;
        this.settings = settings;
        this.orderRepository = orderRepository;
    }

    private boolean enabled() { return settings.getBool("mail.orderNotify"); }

    /**
     * 后台/接口传进来的 Order 往往是已脱离事务的实体，直接访问 items 会触发
     * LazyInitializationException。这里在只读事务里按订单号重新加载并初始化明细，
     * HTML 全部拼好后再交给异步线程池，线程池里不再碰 JPA 实体。
     */
    private Order reload(Order o) {
        if (o == null || o.getOrderNo() == null) return null;
        Order fresh = orderRepository.findByOrderNo(o.getOrderNo()).orElse(null);
        if (fresh != null && fresh.getItems() != null) fresh.getItems().size();
        return fresh;
    }

    @Transactional(readOnly = true)
    public void placed(Order o) {
        if (!enabled()) return;
        Order fresh = reload(o);
        if (fresh != null) send(fresh, "订单已提交", "我们已收到你的订单，等待付款确认。");
    }

    @Transactional(readOnly = true)
    public void statusChanged(Order o) {
        if (!enabled() || o == null) return;
        Order fresh = reload(o);
        if (fresh == null) return;
        String st = fresh.getStatus() == null ? "" : fresh.getStatus().toUpperCase();
        String title;
        String lead;
        switch (st) {
            case "PAID" -> { title = "付款已确认"; lead = "我们已确认收到你的付款，正在为你备货。"; }
            case "SHIPPED" -> { title = "订单已发货"; lead = "你的包裹已经发出，物流信息可在订单查询页查看。"; }
            case "DONE" -> { title = "订单已完成"; lead = "感谢你的购买，期待再次为你制作小物件。"; }
            case "CANCELLED" -> { title = "订单已取消"; lead = "订单已取消，如已扣款会原路退回；如有疑问请回复本邮件。"; }
            case "PARTIAL_RETURN" -> { title = "部分退货已处理"; lead = "你申请的部分退货已处理，款项将原路退回。"; }
            case "REFUNDED" -> { title = "退货退款已完成"; lead = "退货退款已处理完毕。"; }
            default -> { return; }
        }
        send(fresh, title, lead);
    }

    private void send(Order o, String title, String lead) {
        if (o == null || o.getEmail() == null || o.getEmail().isBlank()) return;
        String store = settings.getOr(SettingsService.SITE_NAME, "shine星心");
        StringBuilder items = new StringBuilder();
        if (o.getItems() != null) {
            for (OrderItem it : o.getItems()) {
                String name = it.getProductName() == null ? "商品" : it.getProductName();
                String sku = it.getSkuLabel() == null || it.getSkuLabel().isBlank() ? "" : "（" + it.getSkuLabel() + "）";
                BigDecimal line = (it.getPrice() == null ? BigDecimal.ZERO : it.getPrice())
                        .multiply(BigDecimal.valueOf(it.getQuantity() == null ? 1 : it.getQuantity()));
                items.append("<li>").append(esc(name)).append(sku).append(" × ")
                     .append(it.getQuantity()).append(" — ").append(line.toPlainString()).append(" USD</li>");
            }
        }
        String chargeRow = "<p>商品小计：" + (o.getSubtotalAmount() == null ? "0" : o.getSubtotalAmount().toPlainString())
                + " USD · 运费：" + (o.getShippingFee() == null ? "0" : o.getShippingFee().toPlainString())
                + " USD · 税费：" + (o.getTaxAmount() == null ? "0" : o.getTaxAmount().toPlainString()) + " USD</p>";
        String couponRow = (o.getDiscountAmount() != null && o.getDiscountAmount().signum() > 0)
                ? "<p>优惠：-" + o.getDiscountAmount().toPlainString() + " USD"
                  + (o.getCouponCode() == null ? "" : "（" + esc(o.getCouponCode()) + "）") + "</p>" : "";
        String track = (o.getTrackingNo() == null || o.getTrackingNo().isBlank()) ? ""
                : "<p>物流单号：<strong>" + esc(o.getTrackingNo()) + "</strong>"
                  + (o.getCarrier() == null ? "" : " · " + esc(o.getCarrier())) + "</p>";
        String link = siteBaseUrl + "/order/track?no=" + o.getOrderNo();
        String html = "<div style=\"font-family:Arial,'Microsoft YaHei',sans-serif;max-width:600px;margin:auto;color:#33281f;line-height:1.7\">"
                + "<div style=\"background:#FFF3C4;border-radius:12px;padding:18px 22px\">"
                + "<h2 style=\"margin:0;color:#B71C1C\">" + esc(store) + " · " + esc(title) + "</h2></div>"
                + "<p>亲爱的 " + esc(o.getCustomerName()) + "，你好：</p>"
                + "<p>" + esc(lead) + "</p>"
                + "<div style=\"background:#fff;border:1px solid #eee;border-radius:12px;padding:16px 22px\">"
                + "<p style=\"margin:0\"><strong>订单号：</strong>" + esc(o.getOrderNo()) + "</p>"
                + "<p><strong>状态：</strong>" + esc(o.getStatus()) + "</p><ul>" + items + "</ul>"
                + couponRow + chargeRow
                + "<p><strong>应付金额：</strong>"
                + (o.getTotalAmount() == null ? "0" : o.getTotalAmount().toPlainString()) + " USD</p>"
                + track + "</div>"
                + "<p><a href=\"" + link + "\" style=\"color:#B71C1C\">查看订单进度 →</a></p>"
                + "<p style=\"color:#999;font-size:12px\">这是一封自动发送的交易通知邮件，请勿直接回复。"
                + "如需帮助请联系 " + esc(settings.getOr(SettingsService.SITE_EMAIL, "xumo92241@gmail.com")) + "。</p></div>";
        mail.sendHtml(o.getEmail(), store + " · " + title + "（" + o.getOrderNo() + "）", html, "订单通知");
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
