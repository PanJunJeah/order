package com.order.agent.eval;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;
import java.util.Map;

/**
 * 脚本化 ChatModel：离线评测用，不发起任何网络请求。
 *
 * <p>按“轮次”回放预设内容：要么返回一组 tool_calls，要么返回最终文本。
 * 它替代真实大模型，用于离线驱动 Spring AI 的工具循环。
 */
public class ScriptedChatModel implements ChatModel {

    /** 一轮脚本：toolCalls 非空则触发工具调用，否则 text 为最终回复。 */
    public record Turn(List<AssistantMessage.ToolCall> toolCalls, String text) {
        public static Turn tools(AssistantMessage.ToolCall... calls) {
            return new Turn(List.of(calls), null);
        }

        public static Turn text(String text) {
            return new Turn(null, text);
        }
    }

    private final List<Turn> script;
    private int callCount = 0;

    public ScriptedChatModel(List<Turn> script) {
        this.script = script;
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        Turn turn = script.get(Math.min(callCount, script.size() - 1));
        callCount++;
        AssistantMessage message;
        if (turn.toolCalls() != null && !turn.toolCalls().isEmpty()) {
            message = new AssistantMessage("", Map.of(), turn.toolCalls());
        } else {
            message = new AssistantMessage(turn.text() == null ? "" : turn.text());
        }
        return new ChatResponse(List.of(new Generation(message)));
    }

    public int callCount() {
        return callCount;
    }
}
