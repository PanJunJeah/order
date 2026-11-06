package com.order.agent.domain;

import java.math.BigDecimal;

/**
 * 优惠试算结果（只读，不锁定任何金额）。
 */
public record DiscountQuote(
        BigDecimal subtotal,
        String rule,
        BigDecimal discount,
        BigDecimal payable,
        String promoCode,
        boolean promoApplied,
        String note
) {
}
