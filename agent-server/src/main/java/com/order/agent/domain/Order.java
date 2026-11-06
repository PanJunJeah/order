package com.order.agent.domain;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 真实订单（只有人工确认后才会产生，MySQL 持久化）。
 *
 * <p>来源分为两类：
 * <ul>
 *   <li>{@code AGENT_DRAFT} —— AI 草稿经用户人工确认后转单；</li>
 *   <li>{@code MANUAL} —— 用户在购物车手动结算。</li>
 * </ul>
 */
@Entity
@Table(name = "orders")
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY,
        getterVisibility = JsonAutoDetect.Visibility.NONE,
        isGetterVisibility = JsonAutoDetect.Visibility.NONE)
public class Order {

    public static final String SOURCE_AGENT_DRAFT = "AGENT_DRAFT";
    public static final String SOURCE_MANUAL = "MANUAL";

    /** 状态机：CONFIRMED(已确认) → PAID(已支付) → PREPARING(制作中) → SERVED(已出餐) */
    public static final String STATUS_CONFIRMED = "CONFIRMED";
    public static final String STATUS_PAID = "PAID";
    public static final String STATUS_PREPARING = "PREPARING";
    public static final String STATUS_SERVED = "SERVED";

    @Id
    @Column(length = 32)
    private String orderId;

    @Column(nullable = false, length = 16)
    private String source;

    private Integer tableNo;

    @Column(length = 64)
    private String address;

    @Column(length = 64)
    private String remark;

    @Column(length = 16)
    private String promoCode;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "order_items", joinColumns = @JoinColumn(name = "order_id"))
    private List<OrderItem> items = new ArrayList<>();

    @Column(precision = 10, scale = 2)
    private BigDecimal subtotal;

    @Column(precision = 10, scale = 2)
    private BigDecimal discount;

    @Column(precision = 10, scale = 2)
    private BigDecimal deliveryFee;

    @Column(precision = 10, scale = 2)
    private BigDecimal payable;

    @Column(length = 16)
    private String status;

    private Instant createdAt;

    @Column(length = 32)
    private String draftId;

    protected Order() {
        // JPA
    }

    public Order(String orderId, String source, Integer tableNo, String address, String remark,
                 String promoCode, List<OrderItem> items, BigDecimal subtotal, BigDecimal discount,
                 BigDecimal deliveryFee, BigDecimal payable, String status, Instant createdAt,
                 String draftId) {
        this.orderId = orderId;
        this.source = source;
        this.tableNo = tableNo;
        this.address = address;
        this.remark = remark;
        this.promoCode = promoCode;
        this.items = new ArrayList<>(items);
        this.subtotal = subtotal;
        this.discount = discount;
        this.deliveryFee = deliveryFee;
        this.payable = payable;
        this.status = status;
        this.createdAt = createdAt;
        this.draftId = draftId;
    }

    /** 状态流转（事务内修改，由脏检查落库）。 */
    public void setStatus(String status) {
        this.status = status;
    }

    public String orderId() {
        return orderId;
    }

    public String source() {
        return source;
    }

    public Integer tableNo() {
        return tableNo;
    }

    public String address() {
        return address;
    }

    public String remark() {
        return remark;
    }

    public String promoCode() {
        return promoCode;
    }

    public List<OrderItem> items() {
        return items;
    }

    public BigDecimal subtotal() {
        return subtotal;
    }

    public BigDecimal discount() {
        return discount;
    }

    public BigDecimal deliveryFee() {
        return deliveryFee;
    }

    public BigDecimal payable() {
        return payable;
    }

    public String status() {
        return status;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public String draftId() {
        return draftId;
    }
}
