<template>
  <aside class="sidebar">
    <div class="brand">
      <div class="brand-logo">🍜</div>
      <div class="brand-text">
        <div class="brand-name">味知点餐</div>
        <div class="brand-slogan">AI 智慧点餐平台</div>
      </div>
    </div>

    <nav class="nav">
      <button
        v-for="item in menuItems"
        :key="item.component"
        class="nav-item"
        :class="{ active: current === item.component }"
        @click="handleClick(item.component)"
      >
        <span class="nav-icon">{{ item.icon }}</span>
        <span class="nav-name">{{ item.name }}</span>
      </button>
    </nav>

    <div class="sidebar-footer">
      <div class="safety-card">
        <div class="safety-title">🛡️ 交易安全边界</div>
        <div class="safety-text">AI 助手没有交易权限，只能生成草稿；确认下单必须由您本人完成。</div>
      </div>
    </div>
  </aside>
</template>

<script>
export default {
  props: {
    current: { type: String, default: 'Ordering' }
  },
  emits: ['navigate'],
  setup(_, { emit }) {
    const menuItems = [
      { name: '在线点餐', component: 'Ordering', icon: '🍽️' },
      { name: '购物车结算', component: 'Checkout', icon: '🛒' },
      { name: '菜品上架', component: 'AddDish', icon: '➕' },
      { name: '订单总览', component: 'OrderOverview', icon: '📋' }
    ];

    const handleClick = (component) => emit('navigate', component);

    return { menuItems, handleClick };
  }
};
</script>

<style scoped>
.sidebar {
  width: 216px;
  flex-shrink: 0;
  background: linear-gradient(180deg, #232734 0%, #1b1f2a 100%);
  color: #fff;
  display: flex;
  flex-direction: column;
  padding: 20px 14px;
}

.brand {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 6px 8px 20px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
  margin-bottom: 18px;
}

.brand-logo {
  width: 42px;
  height: 42px;
  border-radius: 12px;
  background: var(--brand-gradient);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22px;
  box-shadow: 0 6px 14px rgba(255, 106, 43, 0.4);
}

.brand-name {
  font-size: 16px;
  font-weight: 700;
}

.brand-slogan {
  font-size: 11px;
  color: rgba(255, 255, 255, 0.55);
}

.nav {
  display: flex;
  flex-direction: column;
  gap: 6px;
  flex: 1;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
  padding: 12px 14px;
  border: none;
  border-radius: 10px;
  background: transparent;
  color: rgba(255, 255, 255, 0.72);
  font-size: 14px;
  cursor: pointer;
  text-align: left;
  transition: all 0.15s ease;
  font-family: inherit;
}

.nav-item:hover {
  background: rgba(255, 255, 255, 0.07);
  color: #fff;
}

.nav-item.active {
  background: var(--brand-gradient);
  color: #fff;
  font-weight: 600;
  box-shadow: 0 6px 16px rgba(255, 106, 43, 0.35);
}

.nav-icon {
  font-size: 17px;
}

.sidebar-footer {
  padding-top: 14px;
}

.safety-card {
  background: rgba(255, 255, 255, 0.06);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 12px;
  padding: 12px;
}

.safety-title {
  font-size: 12px;
  font-weight: 700;
  color: #ffb98a;
  margin-bottom: 6px;
}

.safety-text {
  font-size: 11px;
  line-height: 1.7;
  color: rgba(255, 255, 255, 0.6);
}
</style>
