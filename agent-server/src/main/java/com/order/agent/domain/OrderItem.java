package com.order.agent.domain;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.math.BigDecimal;

/**
 * 订单条目（菜品 + 数量 + 下单时价格快照），可嵌入订单/草稿。
 */
@Embeddable
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY,
        getterVisibility = JsonAutoDetect.Visibility.NONE,
        isGetterVisibility = JsonAutoDetect.Visibility.NONE)
public class OrderItem {

    @Column(name = "dish_id", nullable = false)
    private long dishId;

    @Column(name = "item_name", nullable = false, length = 64)
    private String name;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(nullable = false)
    private int quantity;

    protected OrderItem() {
        // JPA
    }

    public OrderItem(long dishId, String name, BigDecimal price, int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("菜品数量必须大于 0");
        }
        this.dishId = dishId;
        this.name = name;
        this.price = price;
        this.quantity = quantity;
    }

    public long dishId() {
        return dishId;
    }

    public String name() {
        return name;
    }

    public BigDecimal price() {
        return price;
    }

    public int quantity() {
        return quantity;
    }

    public BigDecimal subtotal() {
        return price.multiply(BigDecimal.valueOf(quantity));
    }
}
