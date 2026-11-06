package com.order.agent.exceptions;

/**
 * 单次对话工具调用步数超限 —— 由步数守卫在框架内部循环中抛出，防止模型死循环。
 */
public class ToolStepLimitExceededException extends RuntimeException {

    public ToolStepLimitExceededException(int maxSteps) {
        super("本次对话的工具调用步数已达上限（" + maxSteps + " 步），已停止继续调用工具。请基于已有信息回复用户。");
    }
}
