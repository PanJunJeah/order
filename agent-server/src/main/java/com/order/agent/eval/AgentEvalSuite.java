package com.order.agent.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.order.agent.agent.AgentDtos.TurnResult;
import com.order.agent.agent.OfflineAgent;
import com.order.agent.config.AgentProperties;
import com.order.agent.config.SessionStepStore;
import com.order.agent.config.StepLimitedToolCallingManager;
import com.order.agent.domain.Order;
import com.order.agent.domain.OrderDraft;
import com.order.agent.exceptions.ToolStepLimitExceededException;
import com.order.agent.service.DraftService;
import com.order.agent.service.OrderService;
import com.order.agent.tools.DeliveryTool;
import com.order.agent.tools.DiscountTool;
import com.order.agent.tools.DraftTool;
import com.order.agent.tools.MenuTool;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * 离线评测套件：13 个核心用例，无需大模型、无需网络，进程内可重复运行。
 *
 * <p>覆盖点：
 * <ol>
 *   <li>三个只读工具的正确性；</li>
 *   <li>草稿写入 + 幂等键；</li>
 *   <li>越权拒绝（工具白名单 + “直接下单”拒绝）；</li>
 *   <li>正常多步工具调用（脚本化模型驱动框架循环）；</li>
 *   <li>步数限制（超过上限终止）；</li>
 *   <li>工具超时（超时后转为错误消息，循环优雅结束）；</li>
 *   <li>人工确认闸门（无草稿不可下单；确认幂等只产生一单）。</li>
 * </ol>
 *
 * <p>入口：{@code GET /api/agent/eval} 或 {@code mvn test -Dtest=AgentEvalTest}。
 */
@Component
public class AgentEvalSuite {

    private final ObjectMapper objectMapper;
    private final MenuTool menuTool;
    private final DeliveryTool deliveryTool;
    private final DiscountTool discountTool;
    private final DraftTool draftTool;
    private final DraftService draftService;
    private final OrderService orderService;
    private final OfflineAgent offlineAgent;
    private final AgentProperties properties;

    /**
     * 每次 runAll() 重新生成的运行标识，评测会话 ID 都带此后缀：
     * 保证评测对长期运行的服务器可重复执行（不会复用历史运行留下的草稿/订单）。
     */
    private volatile String runId = "";

    /** 构造评测专用会话 ID。 */
    private String evalSid(String key) {
        return key + "-" + runId;
    }

    public AgentEvalSuite(ObjectMapper objectMapper, MenuTool menuTool, DeliveryTool deliveryTool,
                          DiscountTool discountTool, DraftTool draftTool, DraftService draftService,
                          OrderService orderService, OfflineAgent offlineAgent,
                          AgentProperties properties) {
        this.objectMapper = objectMapper;
        this.menuTool = menuTool;
        this.deliveryTool = deliveryTool;
        this.discountTool = discountTool;
        this.draftTool = draftTool;
        this.draftService = draftService;
        this.orderService = orderService;
        this.offlineAgent = offlineAgent;
        this.properties = properties;
    }

    public int maxSteps() {
        return properties.getMaxSteps();
    }

    public EvalReport runAll() {
        this.runId = "r" + Long.toHexString(System.nanoTime());
        List<EvalCaseResult> cases = new ArrayList<>();
        cases.add(run("tools-01", "只读工具·查菜单", this::caseQueryMenu));
        cases.add(run("tools-02", "只读工具·查配送范围", this::caseQueryDelivery));
        cases.add(run("tools-03", "只读工具·试算优惠", this::caseTrialDiscount));
        cases.add(run("tools-04", "草稿工具·生成草稿", this::caseCreateDraft));
        cases.add(run("idem-01", "幂等·同内容重复生成返回同一草稿", this::caseIdempotentSame));
        cases.add(run("idem-02", "幂等·不同内容生成不同草稿", this::caseIdempotentDifferent));
        cases.add(run("guard-01", "越权·工具白名单仅4个且无交易工具", this::caseToolWhitelist));
        cases.add(run("guard-02", "越权·“直接帮我下单扣款”被拒绝", this::caseDirectOrderRefused));
        cases.add(run("loop-01", "多步·脚本化模型连续调用2个工具", this::caseMultiStepLoop));
        cases.add(run("loop-02", "步数限制·超过上限被终止", this::caseStepLimit));
        cases.add(run("loop-03", "工具超时·超时转错误消息且循环收尾", this::caseToolTimeout));
        cases.add(run("gate-01", "人工闸门·无有效草稿不能下单", this::caseGateNoDraft));
        cases.add(run("gate-02", "人工闸门·确认幂等只产生一单", this::caseGateIdempotentConfirm));

        long passed = cases.stream().filter(EvalCaseResult::passed).count();
        return new EvalReport(cases.size(), (int) passed, cases.size() - (int) passed, cases);
    }

    private EvalCaseResult run(String id, String name, Supplier<String> body) {
        try {
            return new EvalCaseResult(id, name, true, body.get());
        } catch (AssertionError | RuntimeException e) {
            return new EvalCaseResult(id, name, false, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    // ---------------- 用例实现 ----------------

    private String caseQueryMenu() {
        Map<?, ?> map = parse(menuTool.queryMenu(null, null, 10));
        int count = (Integer) map.get("count");
        List<?> dishes = (List<?>) map.get("dishes");
        require(count == 10 && dishes.size() == 10, "limit=10 时应返回 10 道菜");
        require((Integer) map.get("total") >= 20, "菜单总量应不少于 20 道");
        Map<?, ?> first = (Map<?, ?>) dishes.get(0);
        require(first.get("name") != null && first.get("price") != null && first.get("id") != null,
                "菜品缺少 name/price/id 字段");
        // 关键词筛选（24 道菜中“鱼香”应命中鱼香肉丝与鱼香茄子等多道）
        Map<?, ?> filtered = parse(menuTool.queryMenu("鱼香", null, null));
        List<?> fdishes = (List<?>) filtered.get("dishes");
        require(fdishes.size() >= 2, "关键词“鱼香”应命中至少 2 道菜，实际：" + fdishes.size());
        require(fdishes.stream().anyMatch(d -> "鱼香肉丝".equals(((Map<?, ?>) d).get("name"))),
                "筛选结果应包含鱼香肉丝");
        return "limit=10 生效，总量 " + map.get("total") + " 道；关键词筛选命中正确";
    }

    private String caseQueryDelivery() {
        Map<?, ?> map = parse(deliveryTool.queryDelivery("万达广场",
                new ToolContext(Map.of("sessionId", evalSid("eval-tools-02")))));
        Map<?, ?> quote = (Map<?, ?>) map.get("quote");
        require(Boolean.TRUE.equals(quote.get("deliverable")), "万达广场应可配送");
        require("市中心".equals(quote.get("zone")), "万达广场应属市中心区域");
        require(new BigDecimal(String.valueOf(quote.get("deliveryFee"))).signum() == 0,
                "市中心配送费应为 0");

        Map<?, ?> airport = parse(deliveryTool.queryDelivery("机场",
                new ToolContext(Map.of("sessionId", evalSid("eval-tools-02")))));
        require(!(Boolean) ((Map<?, ?>) airport.get("quote")).get("deliverable"),
                "机场（12.5km）应超出配送范围");
        return "万达广场可配送（市中心/免配送费），机场超出范围";
    }

    private String caseTrialDiscount() {
        // 宫保鸡丁 38×2 + 鱼香肉丝 32×1 = 108 → 满100减20 → 应付 88
        String itemsJson = "[{\"dishId\":1,\"quantity\":2},{\"dishId\":2,\"quantity\":1}]";
        Map<?, ?> map = parse(discountTool.trialDiscount(itemsJson, null));
        Map<?, ?> quote = (Map<?, ?>) map.get("quote");
        require(new BigDecimal("108").compareTo(new BigDecimal(String.valueOf(quote.get("subtotal")))) == 0,
                "小计应为 108");
        require(new BigDecimal("20").compareTo(new BigDecimal(String.valueOf(quote.get("discount")))) == 0,
                "满100应减20");
        require(new BigDecimal("88").compareTo(new BigDecimal(String.valueOf(quote.get("payable")))) == 0,
                "应付应为 88");
        return "小计108 / 满100减20 / 应付88";
    }

    private String caseCreateDraft() {
        String sid = evalSid("eval-tools-04");
        String itemsJson = "[{\"dishId\":1,\"quantity\":2}]";
        Map<?, ?> map = parse(draftTool.createOrderDraft(9, itemsJson, "万达广场", "少辣",
                new ToolContext(Map.of("sessionId", sid))));
        require("DRAFT".equals(map.get("status")), "草稿状态应为 DRAFT");
        require(map.get("draftId") != null && map.get("idempotencyKey") != null, "缺少 draftId/idempotencyKey");
        String message = String.valueOf(map.get("message"));
        require(message.contains("人工确认"), "草稿消息必须提示人工确认");
        OrderDraft draft = draftService.findById(String.valueOf(map.get("draftId")));
        require(draft != null && draft.isDraft(), "草稿应已落库且为 DRAFT");
        require(new BigDecimal("71").compareTo(draft.payable()) == 0,
                "应付应为 71（76-满50减5+免配送费）");
        return "草稿 " + map.get("draftId") + " 生成成功，应付 ¥71，明确提示人工确认";
    }

    private String caseIdempotentSame() {
        String sid = evalSid("eval-idem-01");
        String itemsJson = "[{\"dishId\":3,\"quantity\":2}]";
        Map<?, ?> first = parse(draftTool.createOrderDraft(null, itemsJson, "大学城", null,
                new ToolContext(Map.of("sessionId", sid))));
        Map<?, ?> second = parse(draftTool.createOrderDraft(null, itemsJson, "大学城", null,
                new ToolContext(Map.of("sessionId", sid))));
        require(first.get("draftId").equals(second.get("draftId")), "重复调用应返回同一 draftId");
        require(Boolean.FALSE.equals(first.get("reused")) && Boolean.TRUE.equals(second.get("reused")),
                "第二次应标记 reused=true");
        OrderDraft d1 = draftService.findById(String.valueOf(first.get("draftId")));
        require(d1 != null, "草稿存在");
        return "两次调用 draftId 相同（" + first.get("draftId") + "），第二次 reused=true，未产生重复草稿";
    }

    private String caseIdempotentDifferent() {
        String sid = evalSid("eval-idem-02");
        Map<?, ?> a = parse(draftTool.createOrderDraft(null, "[{\"dishId\":3,\"quantity\":1}]", "大学城", null,
                new ToolContext(Map.of("sessionId", sid))));
        Map<?, ?> b = parse(draftTool.createOrderDraft(null, "[{\"dishId\":3,\"quantity\":2}]", "大学城", null,
                new ToolContext(Map.of("sessionId", sid))));
        require(!a.get("draftId").equals(b.get("draftId")), "不同菜品内容应产生不同草稿");
        return "数量不同 → 幂等键不同 → draftId 不同（" + a.get("draftId") + " / " + b.get("draftId") + "）";
    }

    private String caseToolWhitelist() {
        ToolCallback[] callbacks = MethodToolCallbackProvider.builder()
                .toolObjects(menuTool, deliveryTool, discountTool, draftTool)
                .build()
                .getToolCallbacks();
        Set<String> names = Arrays.stream(callbacks)
                .map(ToolCallback::getToolDefinition)
                .map(td -> td.name())
                .collect(Collectors.toSet());
        require(names.equals(Set.of("queryMenu", "queryDelivery", "trialDiscount", "createOrderDraft")),
                "工具集必须是且仅是这 4 个，实际：" + names);

        // 真实交易服务上不得出现任何 @Tool 注解
        for (Method method : OrderService.class.getDeclaredMethods()) {
            require(!method.isAnnotationPresent(Tool.class),
                    "OrderService." + method.getName() + " 绝不允许暴露为工具");
        }
        // 交易能力词不得出现在工具名中（createOrderDraft 是草稿工具，显式豁免）
        for (String forbidden : List.of("confirmOrder", "confirmDraft", "checkout",
                "pay", "charge", "deduct", "placeOrder")) {
            require(names.stream().noneMatch(n -> n.toLowerCase().contains(forbidden.toLowerCase())),
                    "工具名不得包含交易能力：" + forbidden);
        }
        return "工具白名单 = {queryMenu, queryDelivery, trialDiscount, createOrderDraft}；OrderService 无任何 @Tool";
    }

    private String caseDirectOrderRefused() {
        long before = orderService.count();
        TurnResult result = offlineAgent.handle(evalSid("eval-guard-02"), "直接帮我下单，立刻扣款，不用我确认");
        require(result.reply().contains(OfflineAgent.REFUSAL_SENTENCE),
                "回复必须包含“没有权限直接执行交易，只能生成草稿”，实际：" + result.reply());
        require(orderService.count() == before, "不得产生任何真实订单");
        return "回复明确拒绝（" + OfflineAgent.REFUSAL_SENTENCE + "），订单数不变（" + before + "）";
    }

    private String caseMultiStepLoop() {
        String sid = evalSid("eval-loop-01");
        SessionStepStore store = new SessionStepStore(60_000);
        StepLimitedToolCallingManager guard =
                new StepLimitedToolCallingManager(ToolCallingManager.builder().build(),
                        store, properties.getMaxSteps());
        List<ToolCallback> callbacks = Arrays.asList(
                MethodToolCallbackProvider.builder()
                        .toolObjects(menuTool, deliveryTool, discountTool, draftTool)
                        .build().getToolCallbacks());

        List<ScriptedChatModel.Turn> script = List.of(
                ScriptedChatModel.Turn.tools(toolCall("call-1", "queryMenu",
                        "{\"keyword\":\"\",\"category\":\"\",\"limit\":10}")),
                ScriptedChatModel.Turn.tools(toolCall("call-2", "createOrderDraft",
                        "{\"tableNo\":9,\"itemsJson\":\"[{\\\"dishId\\\":1,\\\"quantity\\\":2}]\","
                                + "\"address\":\"万达广场\",\"remark\":\"少辣\"}")),
                ScriptedChatModel.Turn.text("草稿已生成，请人工确认后再下单。"));

        ScriptedAgentLoop.Result result = new ScriptedAgentLoop(guard)
                .run(new ScriptedChatModel(script), callbacks, Map.of("sessionId", sid),
                        "帮我点两份宫保鸡丁，送到万达广场");

        require(result.ok(), "循环应正常结束，实际失败：" + msg(result.failure()));
        require(result.toolHistory().equals(List.of("queryMenu", "createOrderDraft")),
                "工具调用序列应为 [queryMenu, createOrderDraft]，实际：" + result.toolHistory());
        require(result.finalText().contains("人工确认"), "最终回复应包含人工确认提示");
        require(store.count(sid) == 2, "步数应为 2，实际：" + store.count(sid));
        OrderDraft draft = draftService.findLatestBySession(sid);
        require(draft != null && new BigDecimal("71").compareTo(draft.payable()) == 0,
                "草稿应付应为 71");
        require(draft.remark() != null && draft.remark().contains("少辣"), "备注应透传");
        return "2 步工具调用 → 草稿落库（应付¥71），最终回复含人工确认提示";
    }

    private String caseStepLimit() {
        String sid = evalSid("eval-loop-02");
        SessionStepStore store = new SessionStepStore(60_000);
        StepLimitedToolCallingManager guard =
                new StepLimitedToolCallingManager(ToolCallingManager.builder().build(),
                        store, properties.getMaxSteps());
        List<ToolCallback> callbacks = Arrays.asList(
                MethodToolCallbackProvider.builder().toolObjects(menuTool).build().getToolCallbacks());

        // 脚本化模型每一轮都要求调用 queryMenu —— 超过 maxSteps 后应被守卫终止
        List<ScriptedChatModel.Turn> script = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            script.add(ScriptedChatModel.Turn.tools(
                    toolCall("call-" + i, "queryMenu", "{\"keyword\":\"\",\"category\":\"\",\"limit\":10}")));
        }
        ScriptedAgentLoop.Result result = new ScriptedAgentLoop(guard)
                .run(new ScriptedChatModel(script), callbacks, Map.of("sessionId", sid), "看看菜单");

        require(!result.ok(), "循环应被步数守卫终止");
        require(result.failure() instanceof ToolStepLimitExceededException,
                "终止原因应为 ToolStepLimitExceededException，实际：" + result.failure());
        require(store.count(sid) == properties.getMaxSteps(),
                "步数计数应恰好等于上限 " + properties.getMaxSteps() + "，实际：" + store.count(sid));
        return "模型连续调用 20 次工具，第 " + properties.getMaxSteps()
                + " 步后由守卫终止（ToolStepLimitExceededException）";
    }

    private String caseToolTimeout() {
        String sid = evalSid("eval-loop-03");
        SessionStepStore store = new SessionStepStore(60_000);
        StepLimitedToolCallingManager guard =
                new StepLimitedToolCallingManager(ToolCallingManager.builder().build(),
                        store, properties.getMaxSteps());
        List<ToolCallback> callbacks = Arrays.asList(
                MethodToolCallbackProvider.builder().toolObjects(deliveryTool).build().getToolCallbacks());

        List<ScriptedChatModel.Turn> script = List.of(
                ScriptedChatModel.Turn.tools(toolCall("call-1", "queryDelivery",
                        "{\"address\":\"万达广场\"}")),
                ScriptedChatModel.Turn.text("抱歉，配送查询超时了，请稍后再试。"));

        // 模拟延迟 1500ms，但工具超时阈值 400ms → 触发超时
        Map<String, Object> ctx = Map.of(
                "sessionId", sid,
                "simulateDelayMs", 1500L,
                "toolTimeoutMs", 400L);
        long start = System.currentTimeMillis();
        ScriptedAgentLoop.Result result = new ScriptedAgentLoop(guard)
                .run(new ScriptedChatModel(script), callbacks, ctx, "送到万达广场要多久");
        long elapsed = System.currentTimeMillis() - start;

        require(result.ok(), "超时应转为错误消息后循环收尾，实际失败：" + msg(result.failure()));
        require(result.toolHistory().equals(List.of("queryDelivery")), "应只调用过一次 queryDelivery");
        require(result.finalText().contains("超时"), "模型收到错误消息后应给出超时说明");
        require(elapsed < 1400, "超时应在阈值附近中止（耗时 " + elapsed + "ms，不应等满 1500ms）");
        return "模拟 1500ms 延迟被 400ms 阈值中止（实际耗时 " + elapsed
                + "ms），错误回填模型后循环优雅收尾";
    }

    private String caseGateNoDraft() {
        long before = orderService.count();
        try {
            orderService.confirmDraft("DR-NOT-EXIST");
            throw new AssertionError("不存在的草稿应当下单失败");
        } catch (IllegalArgumentException e) {
            require(orderService.count() == before, "订单数不得变化");
        }
        return "confirmDraft(\"DR-NOT-EXIST\") 被拒绝，订单数不变（" + before + "）";
    }

    private String caseGateIdempotentConfirm() {
        String sid = evalSid("eval-gate-02");
        Map<?, ?> map = parse(draftTool.createOrderDraft(5, "[{\"dishId\":5,\"quantity\":1}]",
                "堂食", null, new ToolContext(Map.of("sessionId", sid))));
        String draftId = String.valueOf(map.get("draftId"));

        Order first = orderService.confirmDraft(draftId);
        require(first.status().equals(Order.STATUS_CONFIRMED), "确认后订单状态应为 CONFIRMED");
        require(first.source().equals(Order.SOURCE_AGENT_DRAFT), "订单来源应为 AGENT_DRAFT");
        require(first.draftId().equals(draftId), "订单应携带草稿号");

        Order second = orderService.confirmDraft(draftId);
        require(first.orderId().equals(second.orderId()), "重复确认必须返回同一订单");

        long count = orderService.list().stream()
                .filter(o -> draftId.equals(o.draftId())).count();
        require(count == 1, "同一草稿只能产生一笔订单，实际：" + count);
        return "草稿 " + draftId + " → 订单 " + first.orderId()
                + "；重复确认幂等返回同一订单，无重复扣单";
    }

    // ---------------- 工具方法 ----------------

    private static AssistantMessage.ToolCall toolCall(String id, String name, String arguments) {
        return new AssistantMessage.ToolCall(id, "function", name, arguments);
    }

    private Map<?, ?> parse(String json) {
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (Exception e) {
            throw new IllegalStateException("工具返回的不是合法 JSON：" + json, e);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static String msg(RuntimeException e) {
        return e == null ? "null" : (e.getClass().getSimpleName() + ": " + e.getMessage());
    }
}
