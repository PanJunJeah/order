<template>
  <div class="app-shell">
    <Sidebar :current="currentPage" @navigate="onNavigate" />

    <div class="app-main">
      <header class="app-header">
        <div class="header-left">
          <div class="header-title">
            <span class="header-logo">🍜</span>
            <div>
              <div class="title-main">{{ pageTitle }}</div>
              <div class="title-sub">味知 · 智慧点餐平台</div>
            </div>
          </div>
        </div>

        <div class="header-actions">
          <el-tag class="table-tag" effect="plain" round size="large">
            🪑 桌号 {{ tableNo }}
          </el-tag>
          <el-button class="agent-btn" type="primary" round size="large" @click="chatVisible = true">
            <span class="agent-btn-icon">🤖</span> AI 点餐助手
            <span v-if="agentMode === 'offline'" class="agent-mode-dot">离线演示</span>
          </el-button>
        </div>
      </header>

      <main class="page-host">
        <transition name="fade" mode="out-in">
          <component :is="currentPage" :key="currentPage" />
        </transition>
      </main>
    </div>

    <AgentChat
      v-model:visible="chatVisible"
      @order-created="onOrderCreated"
    />
  </div>
</template>

<script>
import { ref, computed, onMounted } from 'vue';
import { useStore } from 'vuex';
import Sidebar from './components/Sidebar.vue';
import Ordering from './components/Ordering.vue';
import Checkout from './components/Checkout.vue';
import AddDish from './components/AddDish.vue';
import OrderOverview from './components/OrderOverview.vue';
import AgentChat from './components/AgentChat.vue';
import { api } from './api';

const PAGE_TITLES = {
  Ordering: '在线点餐',
  Checkout: '购物车结算',
  AddDish: '菜品上架',
  OrderOverview: '订单总览'
};

export default {
  components: { Sidebar, Ordering, Checkout, AddDish, OrderOverview, AgentChat },
  setup() {
    const store = useStore();
    const currentPage = ref('Ordering');
    const chatVisible = ref(false);

    const tableNo = computed(() => store.state.tableNo);
    const agentMode = computed(() => store.state.agentMode);
    const pageTitle = computed(() => PAGE_TITLES[currentPage.value] || '点餐平台');

    const onNavigate = (page) => {
      currentPage.value = page;
      store.commit('setCurrentPage', page);
    };

    const onOrderCreated = () => {
      onNavigate('OrderOverview');
    };

    onMounted(async () => {
      try {
        const status = await api.agentStatus();
        store.commit('setAgentMeta', { mode: status.mode, maxSteps: status.maxSteps });
      } catch (e) {
        // 后端未启动时保持 unknown，聊天面板会给出提示
        store.commit('setAgentMeta', { mode: 'offline', maxSteps: 6 });
      }
    });

    return { currentPage, chatVisible, tableNo, agentMode, pageTitle, onNavigate, onOrderCreated };
  }
};
</script>

<style scoped>
.app-shell {
  display: flex;
  height: 100vh;
  overflow: hidden;
}

.app-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.app-header {
  height: 72px;
  padding: 0 28px;
  background: rgba(255, 255, 255, 0.85);
  backdrop-filter: blur(8px);
  border-bottom: 1px solid var(--line);
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-shrink: 0;
  z-index: 10;
}

.header-title {
  display: flex;
  align-items: center;
  gap: 12px;
}

.header-logo {
  font-size: 30px;
  filter: drop-shadow(0 3px 6px rgba(255, 106, 43, 0.35));
}

.title-main {
  font-size: 17px;
  font-weight: 700;
  color: var(--ink-900);
  line-height: 1.3;
}

.title-sub {
  font-size: 12px;
  color: var(--ink-500);
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 14px;
}

.table-tag {
  font-weight: 600;
}

.agent-btn {
  box-shadow: 0 6px 16px rgba(255, 106, 43, 0.3);
  border: none;
}

.agent-btn-icon {
  margin-right: 4px;
}

.agent-mode-dot {
  margin-left: 8px;
  font-size: 11px;
  background: rgba(255, 255, 255, 0.25);
  padding: 1px 8px;
  border-radius: 10px;
}

.page-host {
  flex: 1;
  overflow-y: auto;
  padding: 24px 28px 40px;
}
</style>
