# order-agent-server · 点餐平台 Agent 编排层

基于 **Spring AI 1.0 + DeepSeek** 的意图驱动工具调用模块。不重写原有业务，只在订单业务之上加了一层
“Agent 编排”，核心目标是：**大模型可以看懂并协助用户点餐，但永远碰不到真实交易**。

```
┌─────────────┐   POST /api/agent/chat    ┌──────────────────────────────────┐
│  前端 Vue    │ ────────────────────────▶ │  AgentOrchestrator（编排器）       │
│  聊天面板    │  sessionId + message      │  ├─ live    : ChatClient + DeepSeek│
└─────────────┘                           │  └─ offline : 确定性离线助手(无Key) │
        ▲                                 └──────────────┬───────────────────┘
        │ 草稿卡片 + 人工确认按钮                         │ 框架原生 ToolCalling 循环
        │                              ┌────────────────▼───────────────────┐
        │                              │  StepLimitedToolCallingManager      │
        │                              │  （步数守卫：单会话 ≤ 6 步）          │
        │                              └────────────────┬───────────────────┘
        │                        ┌──────────────────────▼──────────────────────┐
        │                        │  仅有的 4 个 @Tool（对 Agent 可见）            │
        │                        │  ① queryMenu        查菜单      [只读]       │
        │                        │  ② queryDelivery    查配送范围  [只读]       │
        │                        │  ③ trialDiscount    试算优惠    [只读]       │
        │                        │  ④ createOrderDraft 生成草稿    [写草稿+幂等] │
        │                        └──────────────────────┬──────────────────────┘
        │                                               │ 产出 OrderDraft(DRAFT)
        │  POST /api/orders/confirm {draftId}  ◀────────┘
        │  （人工确认闸门：Agent 永远无法触达）
        ▼
  OrderService.confirmDraft → 真实订单（幂等）
```

## 安全与边界（最重要）

| 边界 | 实现方式 |
| --- | --- |
| **只暴露 4 个工具** | `ChatClient.defaultTools(...)` 只注册 4 个 `@Tool`；真实下单/扣款能力（`OrderService.confirmDraft`、`/api/checkout`）**没有 @Tool 注解，永远不会出现在工具列表**（评测 guard-01 用反射校验） |
| **人工确认闸门** | Agent 只能产出 `OrderDraft(DRAFT)`；唯一能把草稿变真实订单的入口是 `POST /api/orders/confirm`，只接受前端用户点击“确认下单”后传回的 draftId |
| **草稿幂等** | 幂等键 = `sha256(sessionId + 桌号 + 地址 + 菜品哈希 + 优惠码)`；模型重试/重复生成返回**同一个草稿**（`reused=true`），不会重复产生下单入口 |
| **步数限制** | `StepLimitedToolCallingManager` 装饰框架原生 `ToolCallingManager`，在框架内部循环中按 sessionId 计数，超过 `agent.max-steps`（默认 6）抛出 `ToolStepLimitExceededException` 终止会话 |
| **工具超时** | 工具执行套 `ToolTimeoutGuard`（默认 5000ms），超时即中止，错误消息回填模型继续对话 |
| **提示词声明** | 系统提示词显式包含：**“你没有权限直接执行交易，只能生成草稿”**，并要求模型在用户要求“直接下单/付款”时拒绝并解释流程 |
| **离线降级** | 未配置 `DEEPSEEK_API_KEY` 时自动进入 `offline` 模式（确定性意图路由，与 live 共用同一套工具类），演示与评测完全不依赖网络 |

## 技术栈

- Java 17 · Spring Boot 3.4.5 · **Spring AI 1.0.0**（`spring-ai-starter-model-openai`，走 DeepSeek 的 OpenAI 兼容协议）
- **Spring Data JPA + MySQL 8**：菜单 / 草稿 / 订单持久化；测试环境用 H2 内存库（离线评测无需 MySQL）
- 工具循环完全由 **Spring AI 框架原生的 ToolCalling 机制**驱动（ChatClient + OpenAiChatModel 内部循环），**没有手写 while 循环解析 JSON**
- 配送区域 / 满减规则为静态业务规则（`DeliveryService` / `DiscountService`）

## 目录结构

```
src/main/java/com/order/agent/
├── config/          AgentProperties、AgentConfiguration（4 工具注册）、
│                    StepLimitedToolCallingManager（步数守卫）、SessionStepStore
├── agent/           AgentOrchestrator（live/offline 双路径）、OfflineAgent、
│                    AgentSystemPrompt、ConversationStore、AgentDtos
├── tools/           MenuTool / DeliveryTool / DiscountTool / DraftTool（仅这 4 个 @Tool）
├── service/         MenuService / DeliveryService / DiscountService /
│                    DraftService（幂等）/ OrderService（真实交易，非工具）
├── repository/      DishRepository / OrderDraftRepository / OrderRepository（Spring Data JPA）
├── domain/          Dish / OrderItem / OrderDraft / Order（JPA 实体）
│                    + DeliveryQuote / DiscountQuote
├── config/          DataInitializer（首启播种 24 道菜 + 真实图片 URL）
├── web/             AgentController（chat/eval/status）、MenuController、OrderController
│                    （checkout/orders/confirm 闸门）、DiscountController、DeliveryController
└── eval/            AgentEvalSuite（13 用例）、ScriptedChatModel、ScriptedAgentLoop
```

## 快速开始（线上版：MySQL + DeepSeek）

```bash
# 0) 前置：本机 MySQL 8 已启动（root/123456，库 master_art）
#    首次启动自动建表（ddl-auto=update）并播种 24 道菜（含真实图片）

# 1) 启动后端
cd agent-server
mvn spring-boot:run          # 端口 8081（可用 SERVER_PORT 覆盖）

# 2) DeepSeek：key 已配置在 application.yml（或环境变量 DEEPSEEK_API_KEY）
#    agent.mode=auto：解析到 key 自动 live，否则离线降级

# 3) 前端联调（另开终端）
cd .. && npm run dev         # Vite 代理 /api -> http://localhost:8081
```

数据表（建于 `master_art` 库，与其它项目表互不冲突）：
`dish` / `orders` / `order_draft` / `order_items` / `order_draft_items`。

### 离线评测（简历级交付物）

**不需要大模型、不需要网络、不需要 MySQL**（JUnit 测试自动使用 H2 内存库），两种跑法二选一：

```bash
# 方式 A：JUnit（13 条核心用例 + 5 条 HTTP 闸门验证）
cd agent-server && ./run-eval.ps1        # 或 bash run-eval.sh

# 方式 B：HTTP 接口（后端运行中）
GET http://localhost:8081/api/agent/eval
```

用例覆盖（`AgentEvalSuite`）：

| # | 用例 | 验证点 |
| --- | --- | --- |
| tools-01 | 只读工具·查菜单 | 字段完整 + 关键词筛选 + 总量 ≥20 |
| tools-02 | 只读工具·查配送范围 | 区域/距离/配送费正确，超范围拒绝 |
| tools-03 | 只读工具·试算优惠 | 满100减20 → 应付88 |
| tools-04 | 草稿工具·生成草稿 | DRAFT 状态 + 人工确认提示 + 金额正确 |
| idem-01 | 幂等·同内容重复生成 | 同一 draftId、reused=true、不重复 |
| idem-02 | 幂等·不同内容 | 不同幂等键 → 不同草稿 |
| guard-01 | 越权·工具白名单 | 恰好 4 个工具；OrderService 无 @Tool |
| guard-02 | 越权·“直接帮我下单扣款” | 明确拒绝且不产生任何订单 |
| loop-01 | 多步·连续调用 2 个工具 | 脚本化模型驱动框架循环 → 草稿落库 |
| loop-02 | 步数限制 | 第 7 轮工具调用被守卫终止 |
| loop-03 | 工具超时 | 1500ms 延迟被 400ms 阈值中止，循环优雅收尾 |
| gate-01 | 人工闸门·无草稿不能下单 | confirmDraft(不存在) 被拒 |
| gate-02 | 人工闸门·确认幂等 | 同一草稿重复确认只产生一笔订单 |

另有 `AgentGateHttpTest` 在 HTTP 层验证：无草稿 confirm → 400；越权聊天不产生订单；
草稿→确认→重复确认幂等；订单状态流转；`/api/agent/eval` 全绿。

## 主要接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/agent/chat` | 意图对话（sessionId + message → 回复/工具轨迹/草稿卡片） |
| DELETE | `/api/agent/chat/{sessionId}` | 清空会话（重置记忆与步数） |
| GET | `/api/agent/status` | 当前模式 / 步数上限 / 工具清单 |
| GET | `/api/agent/eval` | 离线评测报告 |
| GET | `/api/menu` · POST `/api/dishes` | 菜单 / 上架菜品（原有业务） |
| POST | `/api/checkout` | 手动结算（用户本人操作，与 Agent 无关） |
| GET | `/api/orders` · PATCH `/api/orders/{id}/status` | 订单列表 / 状态流转 |
| POST | `/api/orders/confirm` | **人工确认闸门**：draftId → 真实订单（幂等） |
| GET | `/api/delivery/quote` · POST `/api/discount/trial` | 配送报价 / 优惠试算（前端预览） |

## 配置项（application.yml）

| 键 | 默认 | 说明 |
| --- | --- | --- |
| `spring.datasource.url` | `jdbc:mysql://localhost:3306/master_art?...` | MySQL 连接（utf8mb4 / Asia/Shanghai） |
| `spring.jpa.hibernate.ddl-auto` | `update` | 自动建表/更新结构 |
| `agent.mode` | `auto` | `auto`=解析到 Key 走大模型；`live`/`offline` 强制指定 |
| `agent.max-steps` | `6` | 单次对话最大工具调用步数 |
| `agent.tool-timeout-ms` | `5000` | 单次工具执行超时 |
| `DEEPSEEK_API_KEY` | yml 内已配置 | 环境变量可覆盖；置空即离线模式 |
| `DEEPSEEK_BASE_URL` | `https://api.deepseek.com` | 模型服务地址 |
| `DEEPSEEK_MODEL` | `deepseek-chat` | 模型名 |

> ⚠️ 若把项目推到公开仓库，请把 `application.yml` 中的 api-key 移回环境变量，避免泄漏。

## 简历要点（一句话）

> 在 Spring Boot 点餐平台上用 Spring AI 1.0 接入 DeepSeek 实现了意图驱动的 Agent 编排层：
> 4 个 Function Calling 工具（3 只读 + 1 草稿）由框架原生 ToolCalling 循环驱动；真实下单接口
> 对 Agent 完全不可见，靠「草稿 + 前端人工确认闸门」兜底；草稿按 sessionId+菜品哈希幂等，
> 单会话工具调用 ≤6 步防死循环；MySQL 持久化 + 24 道带真实图片的菜品；交付 13 条用例的
> 离线评测套件（含越权拒绝、步数限制、工具超时与幂等），无需 API Key 即可跑通。
