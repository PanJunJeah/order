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
 * 预订单草稿 —— Agent 唯一被允许写入的东西（MySQL 持久化）。
 *
 * <p>状态机：DRAFT（待人工确认） → CONFIRMED（前端用户确认，已转真实订单）。
 * 草稿本身永不产生订单、永不扣款；真实下单只能走 {@code POST /api/orders/confirm}。
 *
 * <p>幂等：{@code idempotencyKey} 唯一索引 = sha256(sessionId + 桌号 + 地址 + 菜品哈希 + 优惠码)。
 */
@Entity
@Table(name = "order_draft")
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY,
        getterVisibility = JsonAutoDetect.Visibility.NONE,
        isGetterVisibility = JsonAutoDetect.Visibility.NONE)
public class OrderDraft {

    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_CONFIRMED = "CONFIRMED";
    public static final String STATUS_CANCELLED = "CANCELLED";

    @Id
    @Column(length = 16)
    private String draftId;

    @Column(nullable = false, unique = true, length = 64)
    private String idempotencyKey;

    @Column(length = 64)
    private String sessionId;

    private Integer tableNo;

    @Column(length = 64)
    private String address;

    @Column(length = 64)
    private String remark;

    @Column(length = 16)
    private String promoCode;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "order_draft_items", joinColumns = @JoinColumn(name = "draft_id"))
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

    private Instant confirmedAt;

    @Column(length = 32)
    private String orderId;

    protected OrderDraft() {
        // JPA
    }

    public OrderDraft(String draftId, String idempotencyKey, String sessionId, Integer tableNo,
                      String address, String remark, String promoCode, List<OrderItem> items,
                      BigDecimal subtotal, BigDecimal discount, BigDecimal deliveryFee,
                      BigDecimal payable, String status, Instant createdAt, Instant confirmedAt,
                      String orderId) {
        this.draftId = draftId;
        this.idempotencyKey = idempotencyKey;
        this.sessionId = sessionId;
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
        this.confirmedAt = confirmedAt;
        this.orderId = orderId;
    }

    /** 人工确认后落定（事务内修改，由脏检查落库）。 */
    public void markConfirmed(String orderId) {
        this.status = STATUS_CONFIRMED;
        this.confirmedAt = Instant.now();
        this.orderId = orderId;
    }

    public void markCancelled() {
        this.status = STATUS_CANCELLED;
    }

    public boolean isDraft() {
        return STATUS_DRAFT.equals(status);
    }

    public String draftId() {
        return draftId;
    }

    public String idempotencyKey() {
        return idempotencyKey;
    }

    public String sessionId() {
        return sessionId;
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

    public Instant confirmedAt() {
        return confirmedAt;
    }

    public String orderId() {
        return orderId;
    }
}
