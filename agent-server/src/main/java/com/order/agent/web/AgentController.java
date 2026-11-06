package com.order.agent.web;

import com.order.agent.agent.AgentDtos;
import com.order.agent.agent.AgentOrchestrator;
import com.order.agent.eval.AgentEvalSuite;
import com.order.agent.eval.EvalReport;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Agent 聊天与离线评测接口。
 */
@RestController
@RequestMapping("/api/agent")
public class AgentController {

    private final AgentOrchestrator orchestrator;
    private final AgentEvalSuite evalSuite;

    public AgentController(AgentOrchestrator orchestrator, AgentEvalSuite evalSuite) {
        this.orchestrator = orchestrator;
        this.evalSuite = evalSuite;
    }

    /** 意图驱动的对话入口（前端聊天面板调用）。 */
    @PostMapping("/chat")
    public ApiResponse<AgentDtos.ChatResponse> chat(@RequestBody AgentDtos.ChatRequest request) {
        return ApiResponse.ok(orchestrator.chat(request.sessionId(), request.message()));
    }

    /** 清空会话（重置记忆与步数计数）。 */
    @DeleteMapping("/chat/{sessionId}")
    public ApiResponse<Void> reset(@PathVariable String sessionId) {
        orchestrator.resetSession(sessionId);
        return ApiResponse.ok("会话已清空", null);
    }

    /** 运行状态：当前模式 / 步数上限等。 */
    @GetMapping("/status")
    public ApiResponse<Map<String, Object>> status() {
        return ApiResponse.ok(Map.of(
                "mode", orchestrator.currentMode(),
                "maxSteps", evalSuite.maxSteps(),
                "toolCount", 4,
                "tools", "queryMenu, queryDelivery, trialDiscount, createOrderDraft"));
    }

    /** 离线评测：无需大模型 API Key，进程内跑全部核心用例。 */
    @GetMapping("/eval")
    public ApiResponse<EvalReport> eval() {
        EvalReport report = evalSuite.runAll();
        return report.failed() == 0 ? ApiResponse.ok(report) : ApiResponse.fail("有评测用例未通过", report);
    }
}
