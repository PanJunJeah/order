<template>
  <el-drawer
    :model-value="visible"
    :size="460"
    direction="rtl"
    :with-header="false"
    class="agent-drawer"
    @update:model-value="$emit('update:visible', $event)"
  >
    <div class="agent-panel">
      <!-- 头部 -->
      <div class="agent-header">
        <div class="agent-head-info">
          <div class="agent-avatar">🤖</div>
          <div>
            <div class="agent-title">
              AI 点餐助手
              <el-tag size="small" :type="modeTagType" class="mode-tag">{{ modeLabel }}</el-tag>
            </div>
            <div class="agent-sub">意图驱动 · 只能生成草稿 · 步数受限</div>
          </div>
        </div>
        <div class="agent-head-actions">
          <el-tooltip content="清空对话（重置步数计数）" placement="bottom">
            <el-button circle size="small" @click="clearChat">🗑️</el-button>
          </el-tooltip>
          <el-button circle size="small" @click="visible = false">✕</el-button>
        </div>
      </div>

      <!-- 步数指示 -->
      <div class="step-bar">
        <div class="step-label">工具调用步数</div>
        <div class="step-track">
          <span
            v-for="i in maxSteps"
            :key="i"
            class="step-dot"
            :class="{ used: i <= stepsUsed, limit: limitReached }"
          />
        </div>
        <div class="step-count">{{ stepsUsed }} / {{ maxSteps }}</div>
      </div>

      <div v-if="limitReached" class="limit-banner">
        ⚠️ 本次会话已达到工具调用步数上限，请确认已有草稿或点击“清空对话”重新开始。
      </div>

      <!-- 消息区 -->
      <div ref="msgArea" class="msg-area">
        <div v-if="messages.length === 0" class="welcome">
          <div class="welcome-avatar">🍜</div>
          <div class="welcome-text">
            你好！我是 AI 点餐助手。我可以查菜单、查配送范围、试算优惠，并为你生成<strong>预订单草稿</strong>。
          </div>
          <div class="welcome-tip">⚠️ 我没有权限直接执行交易，只能生成草稿；下单需要你人工确认。</div>
        </div>

        <div v-for="(m, idx) in messages" :key="idx" class="msg" :class="m.role">
          <div class="bubble">{{ m.text }}</div>

          <!-- 工具调用轨迹 -->
          <div v-if="m.steps && m.steps.length" class="tool-trail">
            <span v-for="(s, i) in m.steps" :key="i" class="tool-chip" :class="{ fail: !s.ok }">
              {{ s.ok ? '🔧' : '❌' }} {{ s.summary }}
            </span>
          </div>

          <!-- 草稿卡片 + 人工确认闸门 -->
          <div v-if="m.draft && m.draft.status === 'DRAFT'" class="draft-card">
            <div class="draft-head">
              <span class="draft-title">📝 预订单草稿</span>
              <span class="draft-id">{{ m.draft.draftId }}</span>
            </div>
            <div class="draft-items">
              <div v-for="item in m.draft.items" :key="item.dishId" class="draft-item">
                <span>{{ item.name }} × {{ item.quantity }}</span>
                <span>¥{{ item.price * item.quantity }}</span>
              </div>
            </div>
            <div class="draft-meta">
              <span>小计 ¥{{ m.draft.subtotal }}</span>
              <span>优惠 -¥{{ m.draft.discount }}</span>
              <span>配送费 ¥{{ m.draft.deliveryFee }}</span>
            </div>
            <div class="draft-payable">应付 <b>¥{{ m.draft.payable }}</b></div>
            <div class="draft-warning">
              🛡️ 这只是一份草稿，不会产生真实订单与扣款。确认后才会调用后端接口完成真实下单。
            </div>
            <div class="draft-actions">
              <el-button size="small" plain @click="discardDraft(m)">放弃</el-button>
              <el-button
                type="primary"
                size="small"
                :loading="m.confirming"
                @click="confirmDraft(m)"
              >
                ✅ 确认下单
              </el-button>
            </div>
          </div>

          <!-- 已确认草稿 -->
          <div v-if="m.draft && m.draft.status === 'CONFIRMED'" class="confirmed-card">
            🎉 草稿 {{ m.draft.draftId }} 已确认，订单 {{ m.confirmedOrderId }} 创建成功。
          </div>
        </div>

        <div v-if="loading" class="msg assistant">
          <div class="bubble typing"><span></span><span></span><span></span></div>
        </div>
      </div>

      <!-- 快捷提问 -->
      <div class="quick-prompts">
        <span
          v-for="q in quickPrompts"
          :key="q"
          class="prompt-chip"
          @click="send(q)"
        >{{ q }}</span>
      </div>

      <!-- 输入区 -->
      <div class="input-bar">
        <el-input
          v-model="input"
          placeholder="告诉我你想吃什么…"
          size="large"
          @keyup.enter="send()"
        />
        <el-button type="primary" size="large" circle :loading="loading" @click="send()">
          <span v-if="!loading">➤</span>
        </el-button>
      </div>
    </div>
  </el-drawer>
</template>

<script>
import { ref, computed, nextTick, watch } from 'vue';
import { useStore } from 'vuex';
import { ElMessage, ElMessageBox } from 'element-plus';
import { api } from '../api';

export default {
  props: {
    visible: { type: Boolean, default: false }
  },
  emits: ['update:visible', 'order-created'],
  setup(props, { emit }) {
    const store = useStore();
    const messages = ref([]);
    const input = ref('');
    const loading = ref(false);
    const msgArea = ref(null);
    const stepsUsed = ref(0);
    const limitReached = ref(false);
    const maxSteps = ref(store.state.agentMaxSteps || 6);

    const visible = computed({
      get: () => props.visible,
      set: (v) => emit('update:visible', v)
    });

    const modeLabel = computed(() =>
      store.state.agentMode === 'live' ? 'DeepSeek 在线' : '离线演示');
    const modeTagType = computed(() =>
      store.state.agentMode === 'live' ? 'success' : 'info');

    const quickPrompts = [
      '有什么菜？',
      '送到万达广场要多久？',
      '帮我点两份宫保鸡丁和两碗米饭，送到大学城',
      '直接帮我下单扣款，不用确认'
    ];

    const scrollToBottom = async () => {
      await nextTick();
      if (msgArea.value) msgArea.value.scrollTop = msgArea.value.scrollHeight;
    };

    const pushAssistant = (payload) => {
      messages.value.push({ role: 'assistant', ...payload });
      scrollToBottom();
    };

    const send = async (preset) => {
      const text = (preset ?? input.value).trim();
      if (!text || loading.value) return;
      input.value = '';
      messages.value.push({ role: 'user', text });
      scrollToBottom();
      loading.value = true;
      try {
        const res = await api.agentChat(store.state.sessionId, text);
        stepsUsed.value = res.stepsUsed;
        limitReached.value = res.limitReached;
        maxSteps.value = res.stepsUsed + (res.stepsRemaining ?? 0) || maxSteps.value;
        pushAssistant({
          text: res.reply || '（无回复）',
          steps: res.steps || [],
          draft: res.draft || null,
          confirmedOrderId: null
        });
      } catch (e) {
        pushAssistant({
          text: `请求失败：${e.message}\n\n请确认后端 agent-server 已启动（cd agent-server && mvn spring-boot:run），或使用 npm run mock 模式演示。`
        });
      } finally {
        loading.value = false;
      }
    };

    const clearChat = async () => {
      try {
        await api.resetAgentSession(store.state.sessionId);
      } catch (e) {
        /* 忽略：后端不可用时仅重置本地 */
      }
      store.commit('resetSession');
      messages.value = [];
      stepsUsed.value = 0;
      limitReached.value = false;
      ElMessage.success('对话已清空，步数已重置');
    };

    const confirmDraft = async (msg) => {
      // —— 人工确认闸门：必须由用户显式确认 ——
      try {
        await ElMessageBox.confirm(
          `草稿 ${msg.draft.draftId} 应付 ¥${msg.draft.payable}。\n\nAI 只能生成草稿；点击“确定”后，系统才会调用后端接口完成真实下单与扣款。`,
          '人工确认下单',
          {
            confirmButtonText: '确定下单',
            cancelButtonText: '再想想',
            type: 'warning',
            distinguishCancelAndClose: true
          }
        );
      } catch {
        return;
      }
      msg.confirming = true;
      try {
        const res = await api.confirmDraft(msg.draft.draftId);
        msg.draft.status = 'CONFIRMED';
        msg.confirmedOrderId = res.data.orderId;
        store.commit('addOrder', res.data);
        ElMessage.success(`下单成功！订单号 ${res.data.orderId}`);
        emit('order-created');
        scrollToBottom();
      } catch (e) {
        ElMessage.error(e.message);
      } finally {
        msg.confirming = false;
      }
    };

    const discardDraft = (msg) => {
      msg.draft.status = 'CANCELLED';
      msg.discarded = true;
      ElMessage.info('已放弃该草稿（不会产生任何订单）');
    };

    watch(() => props.visible, (v) => {
      if (v) scrollToBottom();
    });

    return {
      visible,
      messages,
      input,
      loading,
      msgArea,
      stepsUsed,
      limitReached,
      maxSteps,
      modeLabel,
      modeTagType,
      quickPrompts,
      send,
      clearChat,
      confirmDraft,
      discardDraft
    };
  }
};
</script>

<style scoped>
.agent-panel {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.agent-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 18px 12px;
  border-bottom: 1px solid var(--line);
}

.agent-head-info {
  display: flex;
  align-items: center;
  gap: 10px;
}

.agent-avatar {
  width: 42px;
  height: 42px;
  border-radius: 12px;
  background: var(--brand-gradient);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22px;
  box-shadow: 0 5px 12px rgba(255, 106, 43, 0.3);
}

.agent-title {
  font-weight: 700;
  font-size: 15px;
  display: flex;
  align-items: center;
  gap: 8px;
}

.agent-sub {
  font-size: 11px;
  color: var(--ink-500);
}

.agent-head-actions {
  display: flex;
  gap: 6px;
}

.step-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 18px;
  background: var(--bg-page);
  border-bottom: 1px solid var(--line);
  font-size: 12px;
  color: var(--ink-500);
}

.step-track {
  display: flex;
  gap: 5px;
  flex: 1;
}

.step-dot {
  width: 100%;
  height: 6px;
  border-radius: 3px;
  background: #e6e1d9;
  transition: background 0.2s;
}

.step-dot.used {
  background: var(--brand-primary);
}

.step-dot.limit {
  background: var(--danger);
}

.step-count {
  font-weight: 700;
  color: var(--ink-700);
}

.limit-banner {
  margin: 10px 14px 0;
  padding: 8px 12px;
  border-radius: 8px;
  background: #fdf1f1;
  color: var(--danger);
  font-size: 12px;
  border: 1px solid #f6d5d5;
}

.msg-area {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.welcome {
  text-align: center;
  padding: 26px 10px;
}

.welcome-avatar {
  font-size: 42px;
  margin-bottom: 10px;
}

.welcome-text {
  font-size: 13px;
  color: var(--ink-700);
  line-height: 1.8;
}

.welcome-tip {
  margin-top: 10px;
  font-size: 11px;
  color: var(--warning);
  background: #fdf6ea;
  border-radius: 8px;
  padding: 8px 12px;
  display: inline-block;
}

.msg {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.msg.user {
  align-items: flex-end;
}

.msg.user .bubble {
  background: var(--brand-gradient);
  color: #fff;
  border-radius: 14px 4px 14px 14px;
  max-width: 82%;
}

.msg.assistant .bubble {
  background: #fff;
  border: 1px solid var(--line);
  border-radius: 4px 14px 14px 14px;
  max-width: 92%;
  white-space: pre-wrap;
  word-break: break-word;
}

.bubble {
  padding: 10px 14px;
  font-size: 13.5px;
  line-height: 1.75;
  box-shadow: var(--shadow-sm);
}

.bubble.typing span {
  display: inline-block;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--ink-300);
  margin-right: 4px;
  animation: blink 1.2s infinite;
}

.bubble.typing span:nth-child(2) { animation-delay: 0.2s; }
.bubble.typing span:nth-child(3) { animation-delay: 0.4s; }

@keyframes blink {
  0%, 60%, 100% { opacity: 0.25; }
  30% { opacity: 1; }
}

.tool-trail {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  padding-left: 4px;
}

.tool-chip {
  font-size: 11px;
  background: #eef4ff;
  color: #4a6ef5;
  border-radius: 10px;
  padding: 2px 10px;
}

.tool-chip.fail {
  background: #fdf1f1;
  color: var(--danger);
}

.draft-card {
  background: #fff;
  border: 1px solid #ffd9c2;
  border-radius: 12px;
  padding: 14px;
  box-shadow: var(--shadow-md);
}

.draft-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}

.draft-title {
  font-weight: 700;
  font-size: 13.5px;
}

.draft-id {
  font-size: 11px;
  color: var(--ink-500);
  font-family: monospace;
}

.draft-items {
  border-top: 1px dashed var(--line);
  padding-top: 8px;
}

.draft-item {
  display: flex;
  justify-content: space-between;
  font-size: 12.5px;
  color: var(--ink-700);
  padding: 2px 0;
}

.draft-meta {
  display: flex;
  gap: 12px;
  font-size: 11.5px;
  color: var(--ink-500);
  margin-top: 8px;
  padding-top: 8px;
  border-top: 1px dashed var(--line);
}

.draft-payable {
  text-align: right;
  font-size: 13px;
  margin-top: 8px;
  color: var(--ink-900);
}

.draft-payable b {
  color: var(--brand-primary);
  font-size: 17px;
}

.draft-warning {
  margin-top: 8px;
  font-size: 11px;
  color: #b06a1c;
  background: #fdf6ea;
  border-radius: 8px;
  padding: 7px 10px;
  line-height: 1.6;
}

.draft-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 10px;
}

.confirmed-card {
  background: #ecfaf2;
  border: 1px solid #bfe8d2;
  color: var(--success);
  font-size: 12.5px;
  border-radius: 10px;
  padding: 10px 12px;
}

.quick-prompts {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  padding: 10px 16px;
  border-top: 1px solid var(--line);
}

.prompt-chip {
  font-size: 11.5px;
  background: var(--brand-soft);
  color: var(--brand-primary-dark);
  border-radius: 12px;
  padding: 4px 10px;
  cursor: pointer;
  transition: all 0.15s;
}

.prompt-chip:hover {
  background: var(--brand-primary);
  color: #fff;
}

.input-bar {
  display: flex;
  gap: 8px;
  padding: 12px 16px 16px;
  border-top: 1px solid var(--line);
}
</style>
