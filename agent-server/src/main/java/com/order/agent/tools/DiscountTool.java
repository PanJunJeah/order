package com.order.agent.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.order.agent.domain.DiscountQuote;
import com.order.agent.domain.OrderItem;
import com.order.agent.service.DiscountService;
import com.order.agent.service.MenuService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.List;

/**
 * 只读工具 3/3：试算优惠。纯计算，不锁定价格、不写任何状态。
 */
public class DiscountTool {

    private final MenuService menuService;
    private final DiscountService discountService;
    private final ObjectMapper objectMapper;

    public DiscountTool(MenuService menuService, DiscountService discountService,
                        ObjectMapper objectMapper) {
        this.menuService = menuService;
        this.discountService = discountService;
        this.objectMapper = objectMapper;
    }

    @Tool(description = """
            试算优惠：根据菜品清单（JSON 数组，元素形如 {"dishId":1,"quantity":2}）与可选优惠码，
            计算小计、可用的满减/优惠码、减免金额与应付金额。
            这是只读工具：只做计算，不锁定价格、不产生订单、不扣款。""")
    public String trialDiscount(
            @ToolParam(description = "菜品清单 JSON，形如 [{\"dishId\":1,\"quantity\":2}]") String itemsJson,
            @ToolParam(description = "优惠码（NEW10/WELCOME15/FULL20），可为空") String promoCode) {

        List<OrderItem> items = ItemsJsonParser.parse(objectMapper, menuService, itemsJson);
        DiscountQuote quote = discountService.trial(items, promoCode);
        return Json.of(objectMapper)
                .put("items", items)
                .put("quote", quote)
                .toJson();
    }
}
