package com.order.agent.service;

import com.order.agent.domain.DiscountQuote;
import com.order.agent.domain.OrderItem;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 优惠试算服务：只做计算，不锁定价格、不写任何状态。
 *
 * <p>规则（演示用，可按需扩展）：
 * <ul>
 *   <li>自动满减：满 100 减 20；满 50 减 5；否则无优惠；</li>
 *   <li>优惠码（二选一，优惠码有效时替代自动满减）：
 *       NEW10 满 60 减 10；WELCOME15 满 80 减 15；FULL20 满 120 减 20。</li>
 * </ul>
 */
@Service
public class DiscountService {

    /** 自动满减阶梯：阈值 -> 减免（从高到低匹配第一档）。 */
    private static final Map<BigDecimal, BigDecimal> AUTO_RULES = new LinkedHashMap<>() {{
        put(new BigDecimal("100"), new BigDecimal("20"));
        put(new BigDecimal("50"), new BigDecimal("5"));
    }};

    /** 优惠码：code -> (最低消费, 减免)。 */
    private static final Map<String, BigDecimal[]> PROMO_CODES = new LinkedHashMap<>() {{
        put("NEW10", new BigDecimal[]{new BigDecimal("60"), new BigDecimal("10")});
        put("WELCOME15", new BigDecimal[]{new BigDecimal("80"), new BigDecimal("15")});
        put("FULL20", new BigDecimal[]{new BigDecimal("120"), new BigDecimal("20")});
    }};

    public synchronized DiscountQuote trial(List<OrderItem> items, String promoCode) {
        BigDecimal subtotal = items.stream()
                .map(OrderItem::subtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        String code = promoCode == null ? "" : promoCode.trim().toUpperCase();
        if (!code.isEmpty() && PROMO_CODES.containsKey(code)) {
            BigDecimal[] spec = PROMO_CODES.get(code);
            if (subtotal.compareTo(spec[0]) >= 0) {
                BigDecimal discount = spec[1];
                return new DiscountQuote(subtotal,
                        "优惠码 " + code + "（满 " + spec[0] + " 减 " + discount + "）",
                        discount, subtotal.subtract(discount), code, true, "优惠码生效");
            }
            return new DiscountQuote(subtotal, "自动满减", autoDiscount(subtotal),
                    subtotal.subtract(autoDiscount(subtotal)), code, false,
                    "优惠码 " + code + " 未满足门槛（需满 " + spec[0] + "），已按自动满减计算");
        }
        BigDecimal discount = autoDiscount(subtotal);
        String rule = discount.signum() > 0 ? "自动满减" : "无满减活动";
        String note = code.isEmpty() ? "" : "优惠码 " + code + " 无效，已按自动满减计算";
        return new DiscountQuote(subtotal, rule, discount, subtotal.subtract(discount),
                code, false, note);
    }

    private static BigDecimal autoDiscount(BigDecimal subtotal) {
        for (Map.Entry<BigDecimal, BigDecimal> rule : AUTO_RULES.entrySet()) {
            if (subtotal.compareTo(rule.getKey()) >= 0) {
                return rule.getValue();
            }
        }
        return BigDecimal.ZERO;
    }

    public static boolean isValidPromoCode(String promoCode) {
        return promoCode != null && PROMO_CODES.containsKey(promoCode.trim().toUpperCase());
    }
}
