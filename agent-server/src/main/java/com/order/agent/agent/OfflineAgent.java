package com.order.agent.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.order.agent.agent.AgentDtos.ToolStep;
import com.order.agent.agent.AgentDtos.TurnResult;
import com.order.agent.config.AgentProperties;
import com.order.agent.domain.Dish;
import com.order.agent.domain.OrderDraft;
import com.order.agent.service.DraftService;
import com.order.agent.service.MenuService;
import com.order.agent.tools.DeliveryTool;
import com.order.agent.tools.DiscountTool;
import com.order.agent.tools.DraftTool;
import com.order.agent.tools.MenuTool;
import org.springframework.ai.chat.model.ToolContext;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 离线降级 Agent：未配置大模型 API Key 时的确定性演示模式。
 *
 * <p>意图路由是确定性规则，但工具调用与 live 模式完全同源
 * （直接调用同一批工具类），因此离线模式同样遵守：
 * 只能生成草稿、绝不真实下单、步数受限。
 */
public class OfflineAgent {

    public static final String REFUSAL_SENTENCE = "我没有权限直接执行交易，只能生成草稿";

    private static final List<String> TRANSACTION_WORDS = List.of(
            "支付", "付款", "扣款", "直接下单", "立即下单", "马上结账", "不用确认", "无需确认", "自动下单");

    private static final List<String> KNOWN_ADDRESSES = List.of(
            "万达广场", "火车站", "大学城", "高新区", "机场");

    private static final Map<Character, Integer> CN_NUM = Map.ofEntries(
            Map.entry('一', 1), Map.entry('二', 2), Map.entry('两', 2), Map.entry('三', 3),
            Map.entry('四', 4), Map.entry('五', 5), Map.entry('六', 6), Map.entry('七', 7),
            Map.entry('八', 8), Map.entry('九', 9), Map.entry('十', 10));

    private final MenuService menuService;
    private final DraftService draftService;
    private final MenuTool menuTool;
    private final DeliveryTool deliveryTool;
    private final DiscountTool discountTool;
    private final DraftTool draftTool;
    private final ObjectMapper objectMapper;
    private final AgentProperties properties;

    public OfflineAgent(MenuService menuService, com.order.agent.service.DeliveryService deliveryService,
                        com.order.agent.service.DiscountService discountService, DraftService draftService,
                        ObjectMapper objectMapper, AgentProperties properties) {
        this.menuService = menuService;
        this.draftService = draftService;
        this.objectMapper = objectMapper;
        this.properties = properties;
        // 与 live 模式使用完全相同的工具实现，保证行为一致
        this.menuTool = new MenuTool(menuService, objectMapper);
        this.deliveryTool = new DeliveryTool(deliveryService, objectMapper, properties);
        this.discountTool = new DiscountTool(menuService, discountService, objectMapper);
        this.draftTool = new DraftTool(menuService, discountService, draftService,
                deliveryService, objectMapper);
    }

    public TurnResult handle(String sessionId, String message) {
        String msg = message == null ? "" : message.trim();
        this.originalMessage = msg;
        List<ToolStep> steps = new ArrayList<>();
        boolean limitReached = false;

        List<Dish> wanted = extractDishes(msg);
        boolean transactionIntent = TRANSACTION_WORDS.stream().anyMatch(msg::contains);
        boolean menuIntent = containsAny(msg, "菜单", "有什么菜", "有哪些菜", "推荐", "看看菜", "招牌菜");
        boolean deliveryIntent = containsAny(msg, "配送", "外卖", "送到", "送餐", "距离", "运费", "快递");
        boolean discountIntent = containsAny(msg, "优惠", "满减", "折扣", "便宜", "优惠码", "券");
        boolean orderIntent = !wanted.isEmpty() && containsAny(msg, "点", "买", "来一份", "要", "下单", "吃", "来", "加");

        // —— 越权意图优先拦截：任何“直接交易”要求都先拒绝 ——
        if (transactionIntent) {
            String reply = REFUSAL_SENTENCE + "。我无法创建真实订单、无法扣款、无法支付。"
                    + "我可以帮您生成一份预订单草稿，只有您在页面上点击『确认下单』人工确认后，"
                    + "系统才会调用后端接口完成真实下单。";
            if (!wanted.isEmpty() && canStep(steps, properties.getMaxSteps())) {
                return draftFlow(sessionId, msg, wanted, steps, reply);
            }
            return new TurnResult(reply, steps, null, steps.size(), false);
        }

        // —— 点餐意图优先于配送/菜单/优惠意图（“送到xx”只是点餐的一部分） ——
        if (orderIntent && !wanted.isEmpty()) {
            return draftFlow(sessionId, msg, wanted, steps, "好的，先帮您核算金额并生成草稿：");
        }

        if (menuIntent) {
            if (!canStep(steps, properties.getMaxSteps())) {
                limitReached = true;
            } else {
                String result = menuTool.queryMenu(null, null, 10);
                steps.add(new ToolStep("queryMenu", "查询菜单", true));
                return new TurnResult(menuSummary(result) + "\n\n告诉我你想吃什么（可以说数量，如“两份宫保鸡丁”），我可以帮你生成草稿。",
                        steps, null, steps.size(), false);
            }
        }

        if (deliveryIntent) {
            String address = extractAddress(msg);
            if (address == null) {
                return new TurnResult("请问要送到哪里？例如：万达广场 / 火车站 / 大学城 / 高新区。",
                        steps, null, steps.size(), false);
            }
            if (!canStep(steps, properties.getMaxSteps())) {
                limitReached = true;
            } else {
                String result = deliveryTool.queryDelivery(address, new ToolContext(Map.of("sessionId", sessionId)));
                steps.add(new ToolStep("queryDelivery", "查询配送范围", true));
                return new TurnResult(deliverySummary(result), steps, null, steps.size(), false);
            }
        }

        if (discountIntent) {
            if (wanted.isEmpty()) {
                return new TurnResult("试算优惠需要先告诉我菜品清单哦。可用的优惠码：NEW10（满60减10）、"
                        + "WELCOME15（满80减15）、FULL20（满120减20）；不输优惠码也有自动满减（满50减5 / 满100减20）。",
                        steps, null, steps.size(), false);
            }
            String promo = extractPromo(msg);
            String itemsJson = toItemsJson(wanted);
            if (!canStep(steps, properties.getMaxSteps())) {
                limitReached = true;
            } else {
                String result = discountTool.trialDiscount(itemsJson, promo);
                steps.add(new ToolStep("trialDiscount", "试算优惠", true));
                return new TurnResult(discountSummary(result), steps, null, steps.size(), false);
            }
        }

        return new TurnResult(
                "我是「味知」AI 点餐助手。我可以：\n"
                        + "1️⃣ 查菜单（试试“有什么菜”）；\n"
                        + "2️⃣ 查配送范围（试试“送到万达广场要多久”）；\n"
                        + "3️⃣ 试算优惠（试试“帮我算算两份宫保鸡丁的优惠”）；\n"
                        + "4️⃣ 生成预订单草稿（试试“点一份鱼香肉丝和两碗米饭，送到大学城”）。\n\n"
                        + "⚠️ 注意：我没有权限直接执行交易，只能生成草稿；草稿需要您人工确认后才会真正下单。",
                steps, null, steps.size(), limitReached);
    }

    /** 草稿流程：查菜单 → 试算优惠 → 生成草稿（与 live 模式完全相同的工具）。 */
    private TurnResult draftFlow(String sessionId, String msg, List<Dish> wanted,
                                 List<ToolStep> steps, String preamble) {
        int max = properties.getMaxSteps();
        if (!canStep(steps, max)) {
            return new TurnResult(preamble + "已达到工具调用步数上限，请先人工确认已有草稿或开启新会话。",
                    steps, null, steps.size(), true);
        }
        steps.add(new ToolStep("queryMenu", "查询菜单", true));

        String promo = extractPromo(msg);
        String itemsJson = toItemsJson(wanted);

        if (!canStep(steps, max)) {
            return new TurnResult(preamble + "已达到工具调用步数上限。", steps, null, steps.size(), true);
        }
        String discountJson = discountTool.trialDiscount(itemsJson, promo);
        steps.add(new ToolStep("trialDiscount", "试算优惠", true));

        if (!canStep(steps, max)) {
            return new TurnResult(preamble + "已达到工具调用步数上限。", steps, null, steps.size(), true);
        }
        Integer tableNo = extractTableNo(msg);
        String address = extractAddress(msg);
        if (address == null) {
            address = tableNo != null ? "堂食" : "万达广场";
        }
        String draftJson = draftTool.createOrderDraft(tableNo, itemsJson, address,
                extractRemark(msg), new ToolContext(Map.of("sessionId", sessionId, "promoCode", promo == null ? "" : promo)));
        steps.add(new ToolStep("createOrderDraft", "生成预订单草稿", true));

        OrderDraft draft = draftService.findLatestBySession(sessionId);
        return new TurnResult(preamble + "\n\n" + draftSummaryText(draftJson, draft),
                steps, draft, steps.size(), false);
    }

    // ---------- 意图/要素提取 ----------

    List<Dish> extractDishes(String msg) {
        List<Dish> result = new ArrayList<>();
        for (Dish dish : menuService.findAll(null, null, null)) {
            Pattern pattern = Pattern.compile(
                    "([1-9一二两三四五六七八九十])?\\s*[份碗盘杯个]?\\s*" + Pattern.quote(dish.name()));
            Matcher matcher = pattern.matcher(msg);
            int qty = 0;
            while (matcher.find()) {
                qty += parseQuantity(matcher.group(1));
            }
            if (qty > 0) {
                result.add(dish);
                // 数量信息挂在 msg 里，toItemsJson 重新计算
            }
        }
        return result;
    }

    int parseQuantity(String token) {
        if (token == null) {
            return 1;
        }
        char c = token.charAt(0);
        if (Character.isDigit(c)) {
            return c - '0';
        }
        return CN_NUM.getOrDefault(c, 1);
    }

    private String toItemsJson(List<Dish> wanted) {
        List<Map<String, Object>> items = new ArrayList<>();
        for (Dish dish : wanted) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("dishId", dish.id());
            item.put("quantity", countQuantity(dish));
            items.add(item);
        }
        try {
            return objectMapper.writeValueAsString(items);
        } catch (Exception e) {
            throw new IllegalStateException("无法序列化菜品清单", e);
        }
    }

    /** 当前消息原文（数量提取时使用）。 */
    private String originalMessage = "";

    private int countQuantity(Dish dish) {
        Pattern pattern = Pattern.compile(
                "([1-9一二两三四五六七八九十])?\\s*[份碗盘杯个]?\\s*" + Pattern.quote(dish.name()));
        Matcher matcher = pattern.matcher(originalMessage);
        int qty = 0;
        while (matcher.find()) {
            qty += parseQuantity(matcher.group(1));
        }
        return qty;
    }

    private Integer extractTableNo(String msg) {
        Matcher m = Pattern.compile("桌(?:号)?\\s*(\\d{1,3})").matcher(msg);
        return m.find() ? Integer.parseInt(m.group(1)) : null;
    }

    private String extractAddress(String msg) {
        for (String address : KNOWN_ADDRESSES) {
            if (msg.contains(address)) {
                return address;
            }
        }
        return msg.contains("堂食") ? "堂食" : null;
    }

    private String extractPromo(String msg) {
        for (String code : List.of("NEW10", "WELCOME15", "FULL20")) {
            if (msg.toUpperCase().contains(code)) {
                return code;
            }
        }
        return null;
    }

    private String extractRemark(String msg) {
        for (String kw : List.of("少辣", "不要辣", "不要香菜", "多加饭")) {
            if (msg.contains(kw)) {
                return kw;
            }
        }
        return null;
    }

    private boolean canStep(List<ToolStep> steps, int max) {
        return steps.size() < max;
    }

    private static boolean containsAny(String msg, String... words) {
        for (String w : words) {
            if (msg.contains(w)) {
                return true;
            }
        }
        return false;
    }

    // ---------- 结果格式化 ----------

    private String menuSummary(String json) {
        try {
            Map<?, ?> map = objectMapper.readValue(json, Map.class);
            List<?> dishes = (List<?>) map.get("dishes");
            Object total = map.get("total");
            StringBuilder sb = new StringBuilder("为您找到 ").append(total == null ? map.get("count") : total)
                    .append(" 道菜：\n");
            for (Object o : dishes) {
                Map<?, ?> d = (Map<?, ?>) o;
                sb.append("• ").append(d.get("emoji")).append(" ").append(d.get("name"))
                        .append(" ¥").append(d.get("price"))
                        .append("（").append(d.get("category")).append("，库存 ").append(d.get("stock")).append("）\n");
            }
            return sb.toString().trim();
        } catch (Exception e) {
            return "菜单查询完成。";
        }
    }

    private String deliverySummary(String json) {
        try {
            Map<?, ?> map = objectMapper.readValue(json, Map.class);
            Map<?, ?> quote = (Map<?, ?>) map.get("quote");
            if (!(Boolean) quote.get("deliverable")) {
                return "很抱歉，「" + quote.get("address") + "」超出配送范围（最远 10 公里）。";
            }
            return "「" + quote.get("address") + "」可以配送 ✅\n"
                    + "• 区域：" + quote.get("zone") + "，距离约 " + quote.get("distanceKm") + " km\n"
                    + "• 配送费：¥" + quote.get("deliveryFee") + "，预计 " + quote.get("etaMinutes") + " 分钟送达";
        } catch (Exception e) {
            return "配送查询完成。";
        }
    }

    private String discountSummary(String json) {
        try {
            Map<?, ?> map = objectMapper.readValue(json, Map.class);
            Map<?, ?> quote = (Map<?, ?>) map.get("quote");
            return "优惠试算结果：\n"
                    + "• 小计：¥" + quote.get("subtotal") + "\n"
                    + "• 规则：" + quote.get("rule") + "\n"
                    + "• 减免：-¥" + quote.get("discount") + "\n"
                    + "• 应付：¥" + quote.get("payable")
                    + (!quote.get("note").toString().isBlank() ? "\n• 提示：" + quote.get("note") : "");
        } catch (Exception e) {
            return "优惠试算完成。";
        }
    }

    private String draftSummaryText(String draftJson, OrderDraft draft) {
        StringBuilder sb = new StringBuilder();
        try {
            Map<?, ?> map = objectMapper.readValue(draftJson, Map.class);
            sb.append("📝 预订单草稿 ").append(map.get("draftId")).append("\n");
            List<?> items = (List<?>) map.get("items");
            for (Object o : items) {
                Map<?, ?> i = (Map<?, ?>) o;
                sb.append("• ").append(i.get("name")).append(" x").append(i.get("quantity"))
                        .append("  ¥").append(i.get("price")).append("\n");
            }
            sb.append("小计 ¥").append(map.get("subtotal"))
                    .append("，优惠 -¥").append(map.get("discount"))
                    .append("，配送费 ¥").append(map.get("deliveryFee"))
                    .append("，应付 ¥").append(map.get("payable")).append("\n");
            if (map.get("address") != null && !"堂食".equals(map.get("address"))) {
                sb.append("收货地址：").append(map.get("address")).append("\n");
            }
        } catch (Exception e) {
            sb.append("草稿 ").append(draft != null ? draft.draftId() : "").append(" 已生成。\n");
        }
        sb.append("\n⚠️ 这只是一份草稿，不会产生真实订单与扣款。请点击下方卡片上的『确认下单』，")
                .append("经您人工确认后，系统才会调用后端接口完成真实下单。");
        return sb.toString();
    }
}
