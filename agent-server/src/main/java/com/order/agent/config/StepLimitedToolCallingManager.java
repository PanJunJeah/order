package com.order.agent.config;

import com.order.agent.exceptions.ToolStepLimitExceededException;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 步数守卫：装饰框架原生的 {@link ToolCallingManager}。
 *
 * <p>Spring AI 的 ChatModel（OpenAiChatModel）内部自带工具循环
 * （调模型 → 发现 tool_calls → 由 ToolCallingManager 执行工具 →
 * 回填结果 → 再调模型），本类不重写任何循环逻辑，只在框架每轮调用
 * {@code executeToolCalls} 时按 sessionId 计数；一旦达到 {@code maxSteps}，
 * 抛出 {@link ToolStepLimitExceededException} 终止会话，防止模型死循环。
 */
public class StepLimitedToolCallingManager implements ToolCallingManager {

    private final ToolCallingManager delegate;
    private final SessionStepStore stepStore;
    private final int maxSteps;

    /** sessionId -> 本会话工具调用轨迹（名称/是否成功），供前端展示。 */
    private final Map<String, List<ToolTrace>> traces = new ConcurrentHashMap<>();

    public record ToolTrace(String tool, boolean ok) {
    }

    public StepLimitedToolCallingManager(ToolCallingManager delegate, SessionStepStore stepStore,
                                         int maxSteps) {
        this.delegate = delegate;
        this.stepStore = stepStore;
        this.maxSteps = maxSteps;
    }

    @Override
    public List<ToolDefinition> resolveToolDefinitions(ToolCallingChatOptions chatOptions) {
        return delegate.resolveToolDefinitions(chatOptions);
    }

    /**
     * 框架循环在“模型返回了工具调用”时才会调用这里（previousChatResponse 必不为空）。
     * 每一轮工具调用在此计数；超过上限即抛异常终止会话。
     */
    @Override
    public ToolExecutionResult executeToolCalls(Prompt prompt, ChatResponse previousChatResponse) {
        if (previousChatResponse == null) {
            // 防御分支：没有工具调用可执行（真实框架不会走到这里）
            return ToolExecutionResult.builder().build();
        }
        String sessionId = resolveSessionId(prompt);
        int current = stepStore.count(sessionId);
        if (current >= maxSteps) {
            // 先查后增：超限的这轮不计数、不执行，计数精确停在 maxSteps
            throw new ToolStepLimitExceededException(maxSteps);
        }
        stepStore.incrementAndGet(sessionId);
        ToolExecutionResult result = delegate.executeToolCalls(prompt, previousChatResponse);
        recordTraces(prompt, result);
        return result;
    }

    private void recordTraces(Prompt prompt, ToolExecutionResult result) {
        String sessionId = resolveSessionId(prompt);
        for (Message message : result.conversationHistory()) {
            if (message instanceof ToolResponseMessage toolResponseMessage) {
                for (ToolResponseMessage.ToolResponse response : toolResponseMessage.getResponses()) {
                    boolean ok = response.responseData() == null
                            || !response.responseData().startsWith("Error");
                    traces.computeIfAbsent(sessionId, k -> new CopyOnWriteArrayList<>())
                            .add(new ToolTrace(response.name(), ok));
                }
            }
        }
    }

    private String resolveSessionId(Prompt prompt) {
        if (prompt.getOptions() instanceof ToolCallingChatOptions options) {
            Object sid = options.getToolContext().get("sessionId");
            if (sid != null) {
                return String.valueOf(sid);
            }
        }
        return "unknown";
    }

    public List<ToolTrace> tracesOf(String sessionId) {
        return new ArrayList<>(traces.getOrDefault(sessionId, List.of()));
    }

    public int stepsUsed(String sessionId) {
        return stepStore.count(sessionId);
    }

    /** 会话重置：清空步数计数与工具轨迹（前端“清空对话”时调用）。 */
    public void resetSession(String sessionId) {
        stepStore.reset(sessionId);
        traces.remove(sessionId);
    }
}
