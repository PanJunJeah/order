package com.order.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Locale;

/**
 * Agent 编排层配置。
 */
@ConfigurationProperties(prefix = "agent")
public class AgentProperties {

    public enum Mode {
        /** 自动：配置了大模型 API Key 走 live，否则降级为 offline。 */
        AUTO,
        /** 强制走大模型（DeepSeek）。 */
        LIVE,
        /** 强制离线演示模式：确定性脚本化助手，不产生任何外部网络调用。 */
        OFFLINE
    }

    /** 运行模式，默认 auto。 */
    private Mode mode = Mode.AUTO;

    /** 单次对话最大工具调用步数，默认 6，防止模型死循环。 */
    private int maxSteps = 6;

    /** 单次工具执行超时（毫秒），默认 5000。 */
    private long toolTimeoutMs = 5000;

    /** 会话记忆消息条数上限。 */
    private int conversationMaxMessages = 40;

    /** 会话过期时间（分钟）。 */
    private int conversationTtlMinutes = 30;

    public Mode getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = Mode.valueOf(mode.trim().toUpperCase(Locale.ROOT));
    }

    public int getMaxSteps() {
        return maxSteps;
    }

    public void setMaxSteps(int maxSteps) {
        this.maxSteps = maxSteps;
    }

    public long getToolTimeoutMs() {
        return toolTimeoutMs;
    }

    public void setToolTimeoutMs(long toolTimeoutMs) {
        this.toolTimeoutMs = toolTimeoutMs;
    }

    public int getConversationMaxMessages() {
        return conversationMaxMessages;
    }

    public void setConversationMaxMessages(int conversationMaxMessages) {
        this.conversationMaxMessages = conversationMaxMessages;
    }

    public int getConversationTtlMinutes() {
        return conversationTtlMinutes;
    }

    public void setConversationTtlMinutes(int conversationTtlMinutes) {
        this.conversationTtlMinutes = conversationTtlMinutes;
    }
}
