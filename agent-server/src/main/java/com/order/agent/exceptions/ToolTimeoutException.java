package com.order.agent.exceptions;

/**
 * 工具执行超时 —— 由工具内部的超时守卫抛出，框架会将其转为工具错误消息回传模型。
 */
public class ToolTimeoutException extends RuntimeException {

    public ToolTimeoutException(String toolName, long timeoutMs) {
        super("工具 " + toolName + " 执行超时（超过 " + timeoutMs + "ms），已中止本次调用");
    }
}
