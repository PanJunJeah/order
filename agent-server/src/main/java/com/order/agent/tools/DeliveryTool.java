package com.order.agent.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.order.agent.config.AgentProperties;
import com.order.agent.domain.DeliveryQuote;
import com.order.agent.exceptions.ToolTimeoutGuard;
import com.order.agent.service.DeliveryService;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * 只读工具 2/3：查配送范围。带超时守卫（模拟超时用于评测）。
 */
public class DeliveryTool {

    private final DeliveryService deliveryService;
    private final ObjectMapper objectMapper;
    private final AgentProperties properties;

    public DeliveryTool(DeliveryService deliveryService, ObjectMapper objectMapper,
                        AgentProperties properties) {
        this.deliveryService = deliveryService;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Tool(description = """
            查询配送范围与配送费。根据收货地址返回是否可配送、所在区域、距离、配送费与预计送达时间。
            这是只读工具：不产生订单、不产生任何扣费。""")
    public String queryDelivery(
            @ToolParam(description = "收货地址，例如“万达广场”") String address,
            ToolContext toolContext) {

        // 评测钩子：ToolContext 中可注入模拟延迟与自定义超时阈值（生产环境不会出现）
        long simulateDelayMs = longValue(toolContext, "simulateDelayMs", 0);
        long timeoutMs = longValue(toolContext, "toolTimeoutMs", properties.getToolTimeoutMs());

        DeliveryQuote quote = ToolTimeoutGuard.run("queryDelivery", timeoutMs,
                () -> deliveryService.quote(address, simulateDelayMs));
        return Json.of(objectMapper).put("quote", quote).toJson();
    }

    private static long longValue(ToolContext ctx, String key, long defaultValue) {
        Object v = ctx.getContext().get(key);
        if (v instanceof Number n) {
            return n.longValue();
        }
        return defaultValue;
    }
}
