# 🍜 味知 · 智慧点餐平台（Vue3 + Spring Boot Agent 编排层）

一个在线点餐平台：**Vue 3 + Element Plus 前端** + **Spring Boot（Spring AI + DeepSeek）Agent 编排层**。
大模型以“意图驱动工具调用”的方式协助点餐，但受严格安全边界约束：**Agent 只能生成草稿，永远无法直接交易**。

```
┌───────────────────────────┐        ┌──────────────────────────────────────┐
│ 前端（本目录，Vue3 + Vite） │        │ 后端（agent-server/，Spring Boot 3.4）│
│                           │        │                                      │
│  🍽 在线点餐 / 🛒 购物车结算 │ ◀────▶ │  菜单/菜品/结算/订单（原有业务 REST）     │
│  ➕ 菜品上架 / 📋 订单总览   │        │                                      │
│                           │  chat  │  🤖 Agent 编排层（Spring AI 1.0）      │
│  🤖 AI 点餐助手（抽屉面板）  │ ─────▶ │   · DeepSeek（deepseek-chat）         │
│   · 工具步数指示 (x/6)      │        │   · 4 个 Function Calling 工具         │
│   · 草稿卡片 + 确认下单按钮  │ ◀───── │   · 单会话工具调用 ≤ 6 步               │
│                           │        │   · 草稿幂等（sessionId+菜品哈希）       │
│   ✅ 人工确认闸门           │ ─────▶ │  POST /api/orders/confirm → 真实订单   │
└───────────────────────────┘        └──────────────────────────────────────┘
```

## 目录结构

- **`/`（前端）**：Vue 3 + Vite + Element Plus + Vuex，已完成整体美化重构并接入 Agent 对话与草稿确认流
- **`/agent-server`**：Spring Boot Agent 编排层（Spring AI 1.0 + DeepSeek + MySQL），含 4 工具、步数守卫、
  幂等草稿、人工确认闸门、24 道真实图片菜品、13 条用例的离线评测。详见 [agent-server/README.md](agent-server/README.md)

## 快速开始（线上版：MySQL + DeepSeek）

```bash
# 0) 前置：本机 MySQL 8 已启动（root/123456，库 master_art）
#    首次启动后端会自动建表并播种 24 道菜（含 Wikimedia 真实菜品图片）

# 1) 启动后端（端口 8081；key 已在 application.yml，自动进入 live 模式）
cd agent-server
mvn spring-boot:run

# 2) 启动前端（新终端，回到项目根目录）
npm install      # 首次
npm run dev      # http://localhost:5173 ，/api 已代理到 8081

# 纯前端离线演示（不启动后端也能玩，含脚本化 AI 助手）
npm run mock     # VITE_API_MOCK=true
```

## 前端功能

| 页面 | 说明 |
| --- | --- |
| 在线点餐 | 搜索 + 分类筛选 + 菜品卡片（**真实图片**、库存/辣度、加载失败自动回退 emoji），悬浮购物车条 |
| 购物车结算 | 数量调整、堂食/外卖切换、地址与配送费实时报价、优惠码试算、确认支付 |
| 菜品上架 | emoji 图标选择 + 名称/分类/价格/库存/描述/图片URL/辣度 |
| 订单总览 | 统计卡片（订单数/进行中/营业额/AI 转单）+ 状态流转（已确认→已支付→制作中→已出餐） |
| AI 点餐助手 | 聊天抽屉：工具步数指示（x/6）、工具调用轨迹 chips、**草稿卡片 + 人工确认闸门**、快捷提问 |

## Agent 安全边界（重点）

1. **工具白名单**：Agent 只有 4 个工具 —— 查菜单、查配送范围、试算优惠（只读×3）+ 生成草稿（写草稿）；
   真实的“创建订单/扣款”接口**没有 @Tool 注解**，模型永远看不到。
2. **人工确认闸门**：Agent 产出 `DRAFT` 草稿 → 前端草稿卡片弹“人工确认”警告框 → 用户点击“确定下单”
   → 前端才调用 `POST /api/orders/confirm` 完成真实下单。
3. **幂等控制**：草稿幂等键 = `sha256(sessionId + 桌号 + 地址 + 菜品哈希 + 优惠码)`，模型重试复用同一草稿。
4. **步数限制**：单次对话工具调用 ≤ 6 步（`StepLimitedToolCallingManager` 装饰框架原生 ToolCallingManager），
   超限即终止，防止死循环与 token 消耗。
5. **提示词声明**：系统提示词显式写明 *“你没有权限直接执行交易，只能生成草稿”*。

## 离线评测

```bash
cd agent-server
./run-eval.ps1        # 或 bash run-eval.sh；无需 API Key、无需网络、无需 MySQL（测试用 H2）
# 等价 HTTP 入口（后端运行时）：GET http://localhost:8081/api/agent/eval
```

13 条核心用例覆盖：只读工具正确性、草稿幂等、**越权“直接帮我下单扣款”拒绝**、正常多步工具调用、
**步数限制**、**工具超时**、**人工闸门（无草稿不可下单 / 确认幂等只产生一单）**；另有 5 条 HTTP 层闸门验证。

## 常见问题

- **前端提示“后端未启动”？** 确认 `agent-server` 已 `mvn spring-boot:run`（端口 8081）；或改用 `npm run mock` 纯前端演示。
- **8080 被占用？** 后端默认端口已改为 8081；如需改回可用环境变量 `SERVER_PORT` 并同步修改 `vite.config.js` 代理目标。
- **聊天是离线模式？** `agent.mode=auto`：解析到 api-key（环境变量或 application.yml）即走 DeepSeek live；无 Key 时自动降级为确定性离线助手（与 live 共用同一套工具实现）。
- **数据库连不上？** 确认 MySQL 8 已启动、root/123456 有效、库 `master_art` 存在（首次启动自动建表并播种 24 道菜）。
- **`npm run build` 报 esbuild “Access is denied”？** 通常是 dev 服务正在运行时两个 esbuild 争用临时目录；先停止 dev 服务再构建，或将 `TEMP` 指向项目内目录。
- **⚠️ 密钥安全**：`application.yml` 内已写入 DeepSeek key，若推送到公开仓库请先改回环境变量 `DEEPSEEK_API_KEY`。
