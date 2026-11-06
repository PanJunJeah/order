<template>
  <div class="overview-page">
    <!-- 统计卡片 -->
    <div class="stats-row">
      <div class="stat-card panel-card">
        <div class="stat-icon">📦</div>
        <div class="stat-num">{{ orders.length }}</div>
        <div class="stat-label">全部订单</div>
      </div>
      <div class="stat-card panel-card">
        <div class="stat-icon">👨‍🍳</div>
        <div class="stat-num">{{ activeCount }}</div>
        <div class="stat-label">进行中（未出餐）</div>
      </div>
      <div class="stat-card panel-card">
        <div class="stat-icon">💰</div>
        <div class="stat-num">¥{{ totalRevenue }}</div>
        <div class="stat-label">累计营业额</div>
      </div>
      <div class="stat-card panel-card">
        <div class="stat-icon">🤖</div>
        <div class="stat-num">{{ aiOrderCount }}</div>
        <div class="stat-label">AI 草稿转单</div>
      </div>
    </div>

    <!-- 订单列表 -->
    <div class="panel-card orders-card">
      <div class="panel-head">
        <h2 class="page-title">📋 订单总览</h2>
        <el-button size="small" round @click="refresh">🔄 刷新</el-button>
      </div>

      <el-table v-if="orders.length" :data="orders" style="width: 100%">
        <el-table-column prop="orderId" label="订单号" width="110">
          <template #default="{ row }">
            <span class="order-id">{{ row.orderId }}</span>
          </template>
        </el-table-column>

        <el-table-column label="来源" width="110">
          <template #default="{ row }">
            <el-tag v-if="row.source === 'AGENT_DRAFT'" type="warning" effect="light" size="small">
              🤖 AI 草稿
            </el-tag>
            <el-tag v-else type="info" effect="plain" size="small">🛒 手动</el-tag>
          </template>
        </el-table-column>

        <el-table-column label="桌号/地址" width="120">
          <template #default="{ row }">
            {{ row.tableNo ? `桌号 ${row.tableNo}` : row.address || '—' }}
          </template>
        </el-table-column>

        <el-table-column label="菜品" min-width="200">
          <template #default="{ row }">
            <div class="items-cell">
              <span v-for="item in row.items" :key="item.dishId" class="item-tag">
                {{ item.name }} ×{{ item.quantity }}
              </span>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="金额" width="120">
          <template #default="{ row }">
            <div class="amount-cell">
              <span class="amount">¥{{ row.payable }}</span>
              <span v-if="row.discount > 0" class="saved">已省 ¥{{ row.discount }}</span>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="下单时间" width="100">
          <template #default="{ row }">
            <span class="time-cell">{{ formatTime(row.createdAt) }}</span>
          </template>
        </el-table-column>

        <el-table-column label="状态" width="150">
          <template #default="{ row }">
            <el-select
              :model-value="row.status"
              size="small"
              @change="(v) => changeStatus(row, v)"
            >
              <el-option v-for="s in STATUSES" :key="s.value" :label="s.label" :value="s.value" />
            </el-select>
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-else description="暂无订单，去点几道菜吧" />
    </div>
  </div>
</template>

<script>
import { ref, computed, onMounted } from 'vue';
import { useStore } from 'vuex';
import { ElMessage } from 'element-plus';
import { api } from '../api';

const STATUSES = [
  { value: 'CONFIRMED', label: '🕐 已确认' },
  { value: 'PAID', label: '💳 已支付' },
  { value: 'PREPARING', label: '👨‍🍳 制作中' },
  { value: 'SERVED', label: '✅ 已出餐' }
];

export default {
  setup() {
    const store = useStore();
    const loading = ref(false);

    const orders = computed(() => store.state.orders);
    const activeCount = computed(() => orders.value.filter((o) => o.status !== 'SERVED').length);
    const totalRevenue = computed(() =>
      orders.value.reduce((s, o) => s + Number(o.payable || 0), 0).toFixed(2));
    const aiOrderCount = computed(
      () => orders.value.filter((o) => o.source === 'AGENT_DRAFT').length);

    const formatTime = (t) => {
      if (!t) return '—';
      const d = new Date(t);
      return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`;
    };

    const loadOrders = async () => {
      loading.value = true;
      try {
        const list = await api.getOrders();
        store.commit('setOrders', list);
      } catch (e) {
        ElMessage.warning(`订单加载失败：${e.message}`);
      } finally {
        loading.value = false;
      }
    };

    const changeStatus = async (row, status) => {
      try {
        await api.updateOrderStatus(row.orderId, status);
        store.commit('updateOrderStatus', { orderId: row.orderId, status });
        if (status === 'SERVED') ElMessage.success(`订单 ${row.orderId} 已出餐`);
      } catch (e) {
        ElMessage.error(e.message);
      }
    };

    onMounted(loadOrders);

    return {
      orders, activeCount, totalRevenue, aiOrderCount,
      STATUSES, formatTime, changeStatus, refresh: loadOrders, loading
    };
  }
};
</script>

<style scoped>
.overview-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.stats-row {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
}

.stat-card {
  padding: 18px 20px;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.stat-icon {
  font-size: 22px;
}

.stat-num {
  font-size: 26px;
  font-weight: 800;
  color: var(--ink-900);
}

.stat-label {
  font-size: 12px;
  color: var(--ink-500);
}

.orders-card {
  padding: 18px 20px;
}

.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 14px;
}

.order-id {
  font-family: 'SF Mono', Consolas, monospace;
  font-weight: 700;
  color: var(--ink-700);
}

.items-cell {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.item-tag {
  font-size: 11.5px;
  background: var(--bg-page);
  border-radius: 8px;
  padding: 2px 8px;
  color: var(--ink-700);
}

.amount-cell {
  display: flex;
  flex-direction: column;
}

.amount {
  font-weight: 700;
  color: var(--brand-primary);
}

.saved {
  font-size: 11px;
  color: var(--success);
}

.time-cell {
  color: var(--ink-500);
  font-size: 12.5px;
}
</style>
