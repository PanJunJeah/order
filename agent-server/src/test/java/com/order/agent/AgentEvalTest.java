package com.order.agent;

import com.order.agent.eval.AgentEvalSuite;
import com.order.agent.eval.EvalCaseResult;
import com.order.agent.eval.EvalReport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 离线评测 JUnit 入口：{@code mvn test} 即可跑全部核心用例。
 *
 * <p>评测不需要大模型 API Key（离线脚本化），也不需要 MySQL（test 环境用 H2），
 * 也提供等价的 HTTP 入口：{@code GET /api/agent/eval}。
 */
@SpringBootTest
@ActiveProfiles("test")
class AgentEvalTest {

    @Autowired
    private AgentEvalSuite suite;

    @Test
    void evalSuiteAllCasesPass() {
        EvalReport report = suite.runAll();
        for (EvalCaseResult c : report.cases()) {
            if (!c.passed()) {
                System.out.println("[FAIL] " + c.id() + " " + c.name() + " -> " + c.detail());
            }
        }
        System.out.println("== 离线评测: 共 " + report.total() + " 例, 通过 " + report.passed()
                + ", 失败 " + report.failed() + " ==");
        assertEquals(0, report.failed(), "存在未通过的评测用例");
        assertTrue(report.total() >= 10, "核心用例数应不少于 10");
    }
}
