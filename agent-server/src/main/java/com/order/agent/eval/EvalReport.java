package com.order.agent.eval;

import java.util.List;

/**
 * 离线评测总报告。
 */
public record EvalReport(int total, int passed, int failed, List<EvalCaseResult> cases) {
}
