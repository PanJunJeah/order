package com.order.agent.tools;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.order.agent.domain.OrderItem;
import com.order.agent.service.MenuService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 工具共用的菜品清单解析器：把模型传入的 itemsJson（[{dishId,quantity}...]）
 * 解析为带价格快照的 {@link OrderItem} 列表。
 */
public final class ItemsJsonParser {

    private ItemsJsonParser() {
    }

    public static List<OrderItem> parse(ObjectMapper objectMapper, MenuService menuService,
                                        String itemsJson) {
        if (itemsJson == null || itemsJson.isBlank()) {
            throw new IllegalArgumentException("菜品清单不能为空");
        }
        try {
            List<Map<String, Object>> raw = objectMapper.readValue(
                    itemsJson, new TypeReference<>() {
                    });
            if (raw.isEmpty()) {
                throw new IllegalArgumentException("菜品清单不能为空");
            }
            List<OrderItem> items = new ArrayList<>();
            for (Map<String, Object> entry : raw) {
                long dishId = ((Number) entry.get("dishId")).longValue();
                int quantity = ((Number) entry.getOrDefault("quantity", 1)).intValue();
                var dish = menuService.findById(dishId);
                if (dish == null) {
                    throw new IllegalArgumentException("菜品不存在：dishId=" + dishId);
                }
                if (quantity <= 0) {
                    throw new IllegalArgumentException("数量必须大于 0：dishId=" + dishId);
                }
                items.add(new OrderItem(dish.id(), dish.name(), dish.price(), quantity));
            }
            return items;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("菜品清单格式错误，应为 [{\"dishId\":1,\"quantity\":2}]", e);
        }
    }
}
