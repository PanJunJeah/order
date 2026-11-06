package com.order.agent.service;

import com.order.agent.domain.DeliveryQuote;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 配送服务：按地址估算配送范围、距离、配送费与送达时间。
 * 只读服务，不产生任何订单副作用。
 */
@Service
public class DeliveryService {

    /** 配送区域：名称 -> (最近距离km, 最远距离km, 配送费, 预计分钟) */
    private static final Map<String, double[]> ZONES = new LinkedHashMap<>() {{
        put("市中心", new double[]{0, 3, 0, 20});
        put("近郊", new double[]{3, 6, 5, 35});
        put("远郊", new double[]{6, 10, 8, 50});
    }};

    /** 已知地址 -> 距离(km)，保证演示与评测结果稳定。 */
    private static final Map<String, Double> KNOWN_ADDRESSES = new LinkedHashMap<>() {{
        put("万达广场", 1.2);
        put("火车站", 2.8);
        put("大学城", 5.5);
        put("高新区", 7.9);
        put("机场", 12.5);
    }};

    public static final double MAX_DISTANCE_KM = 10.0;

    public synchronized DeliveryQuote quote(String address) {
        return quote(address, 0);
    }

    /**
     * 带模拟延迟的报价（用于“工具超时”评测：delayMs 超过工具超时阈值时触发超时）。
     */
    public synchronized DeliveryQuote quote(String address, long delayMs) {
        if (delayMs > 0) {
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("报价被中断", e);
            }
        }
        String addr = address == null ? "" : address.trim();
        if (addr.isEmpty()) {
            throw new IllegalArgumentException("请提供收货地址");
        }
        double km = KNOWN_ADDRESSES.getOrDefault(addr, pseudoDistance(addr));
        if (km > MAX_DISTANCE_KM) {
            return DeliveryQuote.unreachable(addr);
        }
        for (Map.Entry<String, double[]> zone : ZONES.entrySet()) {
            double[] spec = zone.getValue();
            if (km >= spec[0] && km <= spec[1]) {
                return new DeliveryQuote(addr, zone.getKey(), km,
                        BigDecimal.valueOf(spec[2]).setScale(2), (int) spec[3], true);
            }
        }
        return DeliveryQuote.unreachable(addr);
    }

    /** 未知地址：哈希映射到 0.5~11.5 km 之间，保证同地址结果稳定。 */
    private static double pseudoDistance(String address) {
        int h = Math.abs(address.hashCode());
        return Math.round(((h % 1100) / 100.0) * 10.0) / 10.0 + 0.5;
    }
}
