package com.order.agent.eval;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.ToolCallback;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 离线评测用的脚本化工具循环（仅存在于评测代码中，生产代码不用它）。
 *
 * <p>它复刻的是 Spring AI ChatModel（OpenAiChatModel）内部的固定流程——
 * 调模型 → 发现 tool_calls → 交给 {@link ToolCallingManager} 执行 →
 * 回填结果 → 再调模型——全部通过框架原生 ToolCalling 原语完成，
 * 不解析任何 JSON，也没有业务化的 while 逻辑。
 * 生产环境中该循环由 OpenAiChatModel 内部原生驱动；
 * 这里用脚本化模型替代大模型，使得评测完全离线、可重复。
 */
public class ScriptedAgentLoop {

    private final ToolCallingManager manager;

    public record Result(String finalText, List<String> toolHistory, RuntimeException failure) {
        public boolean ok() {
            return failure == null;
        }
    }

    public ScriptedAgentLoop(ToolCallingManager manager) {
        this.manager = manager;
    }

    public Result run(ChatModel model, List<ToolCallback> callbacks,
                      Map<String, Object> toolContext, String userText) {
        ToolCallingChatOptions options = ToolCallingChatOptions.builder()
                .toolCallbacks(callbacks)
                .toolContext(toolContext)
                .build();

        List<Message> messages = new ArrayList<>();
        messages.add(new UserMessage(userText));
        Prompt prompt = new Prompt(messages, options);
        ChatResponse previous = null;
        List<String> history = new ArrayList<>();

        try {
            while (true) {
                ChatResponse response = model.call(prompt);
                if (response == null || response.getResult() == null) {
                    return new Result(null, history, new IllegalStateException("模型未返回结果"));
                }
                AssistantMessage output = response.getResult().getOutput();
                if (!output.hasToolCalls()) {
                    return new Result(output.getText(), history, null);
                }

                for (AssistantMessage.ToolCall toolCall : output.getToolCalls()) {
                    history.add(toolCall.name());
                }
                // 与 OpenAiChatModel 内部循环一致：模型返回工具调用后，
                // 交给 ToolCallingManager 执行（步数守卫在此计数/限流），
                // 再把工具结果消息回填给下一轮模型调用。
                ToolExecutionResult toolResult = manager.executeToolCalls(prompt, response);
                messages.add(output);
                messages.addAll(toolResult.conversationHistory());
                prompt = new Prompt(new ArrayList<>(messages), options);
                previous = response;
            }
        } catch (RuntimeException e) {
            return new Result(null, history, e);
        }
    }
}
