package com.order.agent.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.order.agent.domain.DeliveryQuote;
import com.order.agent.domain.DiscountQuote;
import com.order.agent.domain.OrderItem;
import com.order.agent.service.DeliveryService;
import com.order.agent.service.DiscountService;
import com.order.agent.service.DraftService;
import com.order.agent.service.MenuService;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.math.BigDecimal;
import java.util.List;

/**
 * 草稿写入工具（Agent 唯一的“写”工具）。
 *
 * <p><b>安全边界</b>：本工具只写“预订单草稿”，状态为 DRAFT；
 * 绝不创建真实订单、绝不扣款。真实下单只能由用户在前端人工确认后
 * 调用 {@code POST /api/orders/confirm} 完成。
 *
 * <p><b>幂等控制</b>：幂等键 = sha256(sessionId + 桌号 + 地址 + 菜品哈希 + 优惠码)，
 * 模型重试 / 重复生成相同内容时返回同一草稿（reused=true）。
 */
public class DraftTool {

    private final MenuService menuService;
    private final DiscountService discountService;
    private final DraftService draftService;
    private final DeliveryService deliveryService;
    private final ObjectMapper objectMapper;

    public DraftTool(MenuService menuService, DiscountService discountService,
                     DraftService draftService, DeliveryService deliveryService,
                     ObjectMapper objectMapper) {
        this.menuService = menuService;
        this.discountService = discountService;
        this.draftService = draftService;
        this.deliveryService = deliveryService;
        this.objectMapper = objectMapper;
    }

    @Tool(description = """
            生成预订单草稿（DRAFT）。这是你唯一的写操作，且只写草稿：
            - 绝不创建真实订单、绝不扣款、绝不支付；
            - 草稿需要用户在前端页面人工确认后，系统才会真正下单；
            - 同一会话、相同菜品内容重复调用会返回同一个草稿（幂等），请放心重试。""")
    public String createOrderDraft(
            @ToolParam(description = "桌号（堂食）或留空（外卖）") Integer tableNo,
            @ToolParam(description = "菜品清单 JSON，形如 [{\"dishId\":1,\"quantity\":2}]") String itemsJson,
            @ToolParam(description = "收货地址，堂食可填“堂食”") String address,
            @ToolParam(description = "备注，如少辣、不要香菜，可为空") String remark,
            ToolContext toolContext) {

        String sessionId = String.valueOf(toolContext.getContext().getOrDefault("sessionId", "unknown"));
        String promoCode = String.valueOf(toolContext.getContext().getOrDefault("promoCode", ""));
        if ("null".equals(promoCode) || promoCode.isBlank()) {
            promoCode = null;
        }

        List<OrderItem> items = ItemsJsonParser.parse(objectMapper, menuService, itemsJson);
        DiscountQuote discountQuote = discountService.trial(items, promoCode);

        // 配送费：堂食免配送费；外卖按地址报价，超出配送范围则拒绝生成草稿
        BigDecimal deliveryFee = resolveDeliveryFee(address);
        DraftService.DraftResult result = draftService.create(sessionId, tableNo, address,
                remark, promoCode, items, discountQuote.subtotal(), discountQuote.discount(),
                deliveryFee);

        return Json.of(objectMapper)
                .put("draftId", result.draft().draftId())
                .put("idempotencyKey", result.draft().idempotencyKey())
                .put("reused", result.reused())
                .put("status", result.draft().status())
                .put("tableNo", result.draft().tableNo())
                .put("address", result.draft().address())
                .put("items", result.draft().items())
                .put("subtotal", result.draft().subtotal())
                .put("discount", result.draft().discount())
                .put("deliveryFee", result.draft().deliveryFee())
                .put("payable", result.draft().payable())
                .put("message", "草稿已生成（" + (result.reused() ? "幂等命中，复用已有草稿" : "首次生成")
                        + "）。注意：这只是一份草稿，不会产生真实订单与扣款；"
                        + "需要用户在前端页面人工确认后，系统才会调用后端接口完成真实下单。")
                .toJson();
    }

    private BigDecimal resolveDeliveryFee(String address) {
        String addr = address == null ? "" : address.trim();
        if (addr.isEmpty() || addr.contains("堂食") || addr.contains("店内")) {
            return BigDecimal.ZERO;
        }
        DeliveryQuote quote = deliveryService.quote(addr);
        if (!quote.deliverable()) {
            throw new IllegalArgumentException("该地址超出配送范围（" + addr + "），无法生成草稿");
        }
        return quote.deliveryFee();
    }
}
