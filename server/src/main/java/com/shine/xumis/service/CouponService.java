package com.shine.xumis.service;

import com.shine.xumis.model.Coupon;
import com.shine.xumis.repository.CouponRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

/** 优惠券 / 满减（P1-4）：校验 + 计算折扣 + 核销。 */
@Service
public class CouponService {

    public record Result(boolean ok, String message, BigDecimal discount, boolean freeShip, Coupon coupon) {}

    private final CouponRepository repo;

    public CouponService(CouponRepository repo) { this.repo = repo; }

    public List<Coupon> findAll() { return repo.findAllByOrderByIdDesc(); }
    public Coupon findById(Long id) { return id == null ? null : repo.findById(id).orElse(null); }

    public Result evaluate(String code, BigDecimal subtotal) {
        BigDecimal base = subtotal == null ? BigDecimal.ZERO : subtotal;
        if (code == null || code.isBlank()) return new Result(false, "请输入优惠码", BigDecimal.ZERO, false, null);
        Coupon c = repo.findByCodeIgnoreCase(code.trim()).orElse(null);
        if (c == null) return new Result(false, "优惠码不存在", BigDecimal.ZERO, false, null);
        if (!c.isEnabled()) return new Result(false, "该优惠码已停用", BigDecimal.ZERO, false, null);
        LocalDateTime now = LocalDateTime.now();
        if (c.getStartsAt() != null && now.isBefore(c.getStartsAt())) return new Result(false, "优惠码尚未生效", BigDecimal.ZERO, false, null);
        if (c.getEndsAt() != null && now.isAfter(c.getEndsAt())) return new Result(false, "优惠码已过期", BigDecimal.ZERO, false, null);
        if (c.getUsageLimit() != null && c.getUsageLimit() > 0
                && (c.getUsedCount() == null ? 0 : c.getUsedCount()) >= c.getUsageLimit()) {
            return new Result(false, "优惠码已被用完", BigDecimal.ZERO, false, null);
        }
        BigDecimal min = c.getMinSubtotal() == null ? BigDecimal.ZERO : c.getMinSubtotal();
        if (base.compareTo(min) < 0) {
            return new Result(false, "未满 " + min.toPlainString() + " USD，暂不可用", BigDecimal.ZERO, false, null);
        }
        String type = c.getType() == null ? "FIXED" : c.getType().toUpperCase(Locale.ROOT);
        BigDecimal value = c.getValue() == null ? BigDecimal.ZERO : c.getValue();
        BigDecimal discount = BigDecimal.ZERO;
        boolean freeShip = false;
        if ("PERCENT".equals(type)) {
            discount = base.multiply(value).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            BigDecimal cap = c.getMaxDiscount() == null ? BigDecimal.ZERO : c.getMaxDiscount();
            if (cap.signum() > 0 && discount.compareTo(cap) > 0) discount = cap;
        } else if ("FREE_SHIP".equals(type)) {
            freeShip = true;
        } else {
            discount = value;
        }
        if (discount.compareTo(base) > 0) discount = base;
        if (discount.signum() < 0) discount = BigDecimal.ZERO;
        String msg = freeShip ? "已免运费" : "已优惠 " + discount.toPlainString() + " USD";
        return new Result(true, msg, discount, freeShip, c);
    }

    /** 核销：订单真正落库后才 +1，避免校验阶段就被占用 */
    @Transactional
    public void consume(Coupon c) {
        if (c == null || c.getId() == null) return;
        repo.findById(c.getId()).ifPresent(fresh -> {
            fresh.setUsedCount((fresh.getUsedCount() == null ? 0 : fresh.getUsedCount()) + 1);
            repo.save(fresh);
        });
    }

    @Transactional
    public void save(Coupon c) { repo.save(c); }

    @Transactional
    public void delete(Long id) { if (id != null) repo.deleteById(id); }
}
