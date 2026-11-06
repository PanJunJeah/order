package com.order.agent.agent;

import java.math.BigDecimal;
import java.util.List;

/**
 * 与前端交互的 Agent 聊天契约。
 */
public final class AgentDtos {

    private AgentDtos() {
    }

    public record ChatRequest(String sessionId, String message) {
    }

    /** 单步工具调用轨迹。 */
    public record ToolStep(String tool, String summary, boolean ok) {
    }

    /** 草稿卡片（前端据此渲染“人工确认”按钮）。 */
    public record DraftView(
            String draftId,
            String idempotencyKey,
            boolean reused,
            Integer tableNo,
            String address,
            String remark,
            String promoCode,
            List<com.order.agent.domain.OrderItem> items,
            BigDecimal subtotal,
            BigDecimal discount,
            BigDecimal deliveryFee,
            BigDecimal payable,
            String status,
            String message
    ) {
    }

    public record ChatResponse(
            String sessionId,
            String reply,
            int stepsUsed,
            int stepsRemaining,
            boolean limitReached,
            String mode,
            List<ToolStep> steps,
            DraftView draft
    ) {
    }

    /** 编排层内部结果（统一 live / offline 两条路径）。 */
    public record TurnResult(
            String reply,
            List<ToolStep> steps,
            com.order.agent.domain.OrderDraft draft,
            int stepsUsed,
            boolean limitReached
    ) {
    }
}
