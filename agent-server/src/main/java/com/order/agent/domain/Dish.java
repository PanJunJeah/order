package com.order.agent.domain;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/**
 * 菜品（MySQL 持久化实体）。
 *
 * <p>保留 record 风格的访问器（id()/name()/…）以兼容既有工具/控制器/评测代码；
 * 通过 {@link JsonAutoDetect} 让 Jackson 直接按字段序列化，输出结构与之前完全一致，
 * 并新增 {@code image} 字段承载真实菜品图片 URL。
 */
@Entity
@Table(name = "dish")
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY,
        getterVisibility = JsonAutoDetect.Visibility.NONE,
        isGetterVisibility = JsonAutoDetect.Visibility.NONE)
public class Dish {

    @Id
    private long id;

    @Column(nullable = false, length = 64)
    private String name;

    @Column(length = 32)
    private String category;

    @Column(length = 255)
    private String description;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(nullable = false)
    private int stock;

    @Column(length = 16)
    private String emoji;

    @Column(length = 512)
    private String image;

    @Column(nullable = false)
    private boolean spicy;

    protected Dish() {
        // JPA
    }

    public Dish(long id, String name, String category, String description,
                BigDecimal price, int stock, String emoji, String image, boolean spicy) {
        this.id = id;
        this.name = name;
        this.category = category;
        this.description = description;
        this.price = price;
        this.stock = stock;
        this.emoji = emoji;
        this.image = image;
        this.spicy = spicy;
    }

    public long id() {
        return id;
    }

    public String name() {
        return name;
    }

    public String category() {
        return category;
    }

    public String description() {
        return description;
    }

    public BigDecimal price() {
        return price;
    }

    public int stock() {
        return stock;
    }

    public String emoji() {
        return emoji;
    }

    public String image() {
        return image;
    }

    public boolean spicy() {
        return spicy;
    }
}
