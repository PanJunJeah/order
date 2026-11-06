package com.order.agent.domain;

import java.math.BigDecimal;

/**
 * 配送报价：是否可配送、距离、配送费、预计送达时间。
 */
public record DeliveryQuote(
        String address,
        String zone,
        double distanceKm,
        BigDecimal deliveryFee,
        int etaMinutes,
        boolean deliverable
) {
    public static DeliveryQuote unreachable(String address) {
        return new DeliveryQuote(address, "超出配送范围", -1, BigDecimal.ZERO, -1, false);
    }
}
