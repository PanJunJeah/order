package com.order.agent.eval;

/**
 * 单条评测用例结果。
 */
public record EvalCaseResult(String id, String name, boolean passed, String detail) {
}
