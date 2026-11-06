package com.order.agent.agent;

import com.order.agent.agent.AgentDtos.ChatResponse;
import com.order.agent.agent.AgentDtos.DraftView;
import com.order.agent.agent.AgentDtos.ToolStep;
import com.order.agent.agent.AgentDtos.TurnResult;
import com.order.agent.config.AgentProperties;
import com.order.agent.config.StepLimitedToolCallingManager;
import com.order.agent.domain.OrderDraft;
import com.order.agent.exceptions.ToolStepLimitExceededException;
import com.order.agent.service.DraftService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Agent 编排器：入口统一，内部按模式分派。
 *
 * <ul>
 *   <li><b>live</b>：Spring AI ChatClient + OpenAiChatModel（DeepSeek），
 *       工具循环完全由框架原生 ToolCalling 机制驱动，不手写 while 循环；
 *       步数由 {@link StepLimitedToolCallingManager} 在框架循环内部强制。</li>
 *   <li><b>offline</b>：确定性离线助手（无 Key 时自动降级），工具实现与 live 同源。</li>
 * </ul>
 */
public class AgentOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(AgentOrchestrator.class);

    private final ChatClient chatClient;
    private final OfflineAgent offlineAgent;
    private final ConversationStore conversationStore;
    private final StepLimitedToolCallingManager stepGuard;
    private final DraftService draftService;
    private final AgentProperties properties;
    /** 配置解析后的 api-key（支持环境变量或直接写在 application.yml）。 */
    private final String resolvedApiKey;

    public AgentOrchestrator(ChatClient chatClient, OfflineAgent offlineAgent,
                             ConversationStore conversationStore,
                             StepLimitedToolCallingManager stepGuard,
                             DraftService draftService, AgentProperties properties,
                             String resolvedApiKey) {
        this.chatClient = chatClient;
        this.offlineAgent = offlineAgent;
        this.conversationStore = conversationStore;
        this.stepGuard = stepGuard;
        this.draftService = draftService;
        this.properties = properties;
        this.resolvedApiKey = resolvedApiKey;
    }

    public String currentMode() {
        return resolveMode() == AgentProperties.Mode.LIVE ? "live" : "offline";
    }

    public ChatResponse chat(String sessionId, String message) {
        String sid = sessionId == null || sessionId.isBlank() ? "s-" + System.nanoTime() : sessionId;
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("消息不能为空");
        }

        if (resolveMode() == AgentProperties.Mode.OFFLINE) {
            TurnResult result = offlineAgent.handle(sid, message);
            conversationStore.append(sid, new UserMessage(message));
            conversationStore.append(sid, new AssistantMessage(result.reply()));
            return toResponse(sid, result, "offline");
        }

        // ---------- live：框架原生工具循环 ----------
        conversationStore.append(sid, new UserMessage(message));
        List<org.springframework.ai.chat.messages.Message> history = conversationStore.list(sid);
        try {
            String reply = chatClient.prompt()
                    .messages(history)
                    .toolContext(Map.of("sessionId", sid))
                    .call()
                    .content();
            if (reply == null || reply.isBlank()) {
                reply = "抱歉，我暂时没有生成有效回复，请换个说法试试。";
            }
            conversationStore.append(sid, new AssistantMessage(reply));
            int used = stepGuard.stepsUsed(sid);
            List<ToolStep> steps = stepGuard.tracesOf(sid).stream()
                    .map(t -> new ToolStep(t.tool(), summarize(t.tool()), t.ok()))
                    .toList();
            OrderDraft draft = draftService.findLatestBySession(sid);
            return new ChatResponse(sid, reply, used,
                    Math.max(0, properties.getMaxSteps() - used), false, "live",
                    steps, toDraftView(draft));
        } catch (ToolStepLimitExceededException e) {
            log.warn("会话 {} 达到工具调用步数上限", sid);
            conversationStore.append(sid, new AssistantMessage(e.getMessage()));
            int used = stepGuard.stepsUsed(sid);
            List<ToolStep> steps = stepGuard.tracesOf(sid).stream()
                    .map(t -> new ToolStep(t.tool(), summarize(t.tool()), t.ok()))
                    .toList();
            OrderDraft draft = draftService.findLatestBySession(sid);
            return new ChatResponse(sid,
                    "⚠️ " + e.getMessage() + "\n\n请基于已有信息继续操作：您可以在页面确认已有草稿，或点击“清空对话”重新开始。",
                    used, 0, true, "live", steps, toDraftView(draft));
        } catch (Exception e) {
            log.error("Agent 调用失败", e);
            return new ChatResponse(sid,
                    "抱歉，大模型服务暂时不可用（" + rootMessage(e) + "）。当前已自动进入离线模式，"
                            + "您仍可以让我查菜单、试算优惠并生成草稿。",
                    0, properties.getMaxSteps(), false, "live-error",
                    List.of(), null);
        }
    }

    /** 清空会话（记忆 + 步数计数 + 工具轨迹）。 */
    public void resetSession(String sessionId) {
        conversationStore.clear(sessionId);
        stepGuard.resetSession(sessionId);
    }

    private AgentProperties.Mode resolveMode() {
        return switch (properties.getMode()) {
            case LIVE -> AgentProperties.Mode.LIVE;
            case OFFLINE -> AgentProperties.Mode.OFFLINE;
            case AUTO -> {
                String key = resolvedApiKey == null ? "" : resolvedApiKey.trim();
                boolean keyConfigured = !key.isEmpty() && !"demo-offline-key".equals(key);
                yield keyConfigured ? AgentProperties.Mode.LIVE : AgentProperties.Mode.OFFLINE;
            }
        };
    }

    private ChatResponse toResponse(String sid, TurnResult result, String mode) {
        return new ChatResponse(sid, result.reply(), result.stepsUsed(),
                Math.max(0, properties.getMaxSteps() - result.stepsUsed()),
                result.limitReached(), mode, result.steps(), toDraftView(result.draft()));
    }

    private DraftView toDraftView(OrderDraft draft) {
        if (draft == null) {
            return null;
        }
        return new DraftView(draft.draftId(), draft.idempotencyKey(),
                false, draft.tableNo(), draft.address(), draft.remark(), draft.promoCode(),
                draft.items(), draft.subtotal(), draft.discount(), draft.deliveryFee(),
                draft.payable(), draft.status(),
                "草稿已生成。注意：这只是一份草稿，不会产生真实订单与扣款；"
                        + "需要您人工确认后，系统才会调用后端接口完成真实下单。");
    }

    private static String summarize(String tool) {
        return switch (tool) {
            case "queryMenu" -> "查询菜单";
            case "queryDelivery" -> "查询配送范围";
            case "trialDiscount" -> "试算优惠";
            case "createOrderDraft" -> "生成预订单草稿";
            default -> tool;
        };
    }

    private static String rootMessage(Throwable e) {
        Throwable t = e;
        while (t.getCause() != null) {
            t = t.getCause();
        }
        String m = t.getMessage();
        return m == null ? t.getClass().getSimpleName() : m;
    }
}
