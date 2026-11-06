<template>
  <div class="ordering-page">
    <!-- 顶部工具栏 -->
    <div class="toolbar panel-card">
      <el-input
        v-model="keyword"
        placeholder="🔍 搜索菜品，如“鱼香”"
        clearable
        class="search-input"
        @input="filterMenu"
      />
      <div class="category-chips">
        <span
          class="chip"
          :class="{ active: category === '' }"
          @click="selectCategory('')"
        >全部</span>
        <span
          v-for="cat in categories"
          :key="cat"
          class="chip"
          :class="{ active: category === cat }"
          @click="selectCategory(cat)"
        >{{ cat }}</span>
      </div>
    </div>

    <!-- 菜品网格 -->
    <div v-loading="loading" class="dish-grid">
      <div v-for="dish in filtered" :key="dish.id" class="dish-card panel-card">
        <div class="dish-media">
          <img
            v-if="dish.image"
            :src="dish.image"
            :alt="dish.name"
            class="dish-image"
            loading="lazy"
            @error="dish.image = ''"
          >
          <div v-else class="dish-emoji">{{ dish.emoji || '🍽️' }}</div>
        </div>
        <div class="dish-body">
          <div class="dish-name-row">
            <span class="dish-name">{{ dish.name }}</span>
            <el-tag v-if="dish.spicy" size="small" type="danger" effect="light">🌶 辣</el-tag>
          </div>
          <div class="dish-desc">{{ dish.description }}</div>
          <div class="dish-footer">
            <div class="dish-price"><span class="yen">¥</span>{{ dish.price }}</div>
            <div class="dish-stock" :class="{ low: dish.stock < 20 }">库存 {{ dish.stock }}</div>
          </div>
          <div class="dish-actions">
            <el-button
              :disabled="dish.stock <= 0"
              class="add-btn"
              round
              type="primary"
              @click="addToCart(dish)"
            >
              ＋ 加入购物车
            </el-button>
          </div>
        </div>
      </div>

      <el-empty v-if="!loading && filtered.length === 0" description="没有找到相关菜品" />
    </div>

    <!-- 悬浮购物车条 -->
    <transition name="cart-slide">
      <div v-if="cartCount > 0" class="cart-bar">
        <div class="cart-summary">
          <span class="cart-icon">🛒</span>
          <span class="cart-count">{{ cartCount }} 件</span>
          <span class="cart-total">¥{{ cartTotal }}</span>
        </div>
        <el-button type="primary" round size="large" @click="$emit('navigate', 'Checkout')">
          去结算 →
        </el-button>
      </div>
    </transition>
  </div>
</template>

<script>
import { ref, computed, onMounted } from 'vue';
import { useStore } from 'vuex';
import { ElMessage } from 'element-plus';
import { api } from '../api';

export default {
  emits: ['navigate'],
  setup(_, { emit }) {
    const store = useStore();
    const loading = ref(false);
    const keyword = ref('');
    const category = ref('');

    const menu = computed(() => store.state.menu);
    const categories = computed(() => store.getters.categories);
    const cartCount = computed(() => store.getters.cartCount);
    const cartTotal = computed(() => store.getters.cartTotal);

    const filtered = computed(() => {
      let list = menu.value;
      if (category.value) list = list.filter((d) => d.category === category.value);
      if (keyword.value) {
        const kw = keyword.value.trim();
        list = list.filter((d) => d.name.includes(kw) || (d.description || '').includes(kw));
      }
      return list;
    });

    const loadMenu = async () => {
      loading.value = true;
      try {
        const list = await api.getMenu();
        store.commit('setMenu', list);
      } catch (e) {
        ElMessage.warning(`菜单加载失败：${e.message}`);
      } finally {
        loading.value = false;
      }
    };

    const selectCategory = (cat) => {
      category.value = cat;
    };

    const filterMenu = () => {
      /* computed 已响应 keyword */
    };

    const addToCart = (dish) => {
      if (dish.stock <= 0) {
        ElMessage.warning('该菜品已售罄');
        return;
      }
      store.commit('addToCart', {
        dishId: dish.id,
        name: dish.name,
        price: dish.price,
        emoji: dish.emoji,
        quantity: 1
      });
      ElMessage.success(`已加入：${dish.name}`);
    };

    onMounted(loadMenu);

    return {
      loading,
      keyword,
      category,
      menu,
      categories,
      filtered,
      cartCount,
      cartTotal,
      addToCart,
      selectCategory,
      filterMenu,
      emit
    };
  }
};
</script>

<style scoped>
.ordering-page {
  position: relative;
  min-height: 100%;
  padding-bottom: 90px;
}

.toolbar {
  padding: 14px 18px;
  margin-bottom: 18px;
  display: flex;
  align-items: center;
  gap: 18px;
  flex-wrap: wrap;
}

.search-input {
  width: 260px;
}

.category-chips {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.chip {
  padding: 5px 16px;
  border-radius: 16px;
  background: var(--bg-page);
  color: var(--ink-700);
  font-size: 13px;
  cursor: pointer;
  transition: all 0.15s;
  border: 1px solid transparent;
}

.chip:hover {
  color: var(--brand-primary);
}

.chip.active {
  background: var(--brand-soft);
  color: var(--brand-primary-dark);
  border-color: #ffd9c2;
  font-weight: 600;
}

.dish-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(250px, 1fr));
  gap: 16px;
  min-height: 200px;
}

.dish-card {
  overflow: hidden;
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}

.dish-card:hover {
  transform: translateY(-4px);
  box-shadow: var(--shadow-lg);
}

.dish-media {
  height: 132px;
  overflow: hidden;
}

.dish-image {
  width: 100%;
  height: 132px;
  object-fit: cover;
  display: block;
}

.dish-emoji {
  height: 132px;
  background: linear-gradient(135deg, #fff3ea 0%, #ffe8d8 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 52px;
}

.dish-body {
  padding: 14px 16px 16px;
}

.dish-name-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}

.dish-name {
  font-size: 15.5px;
  font-weight: 700;
}

.dish-desc {
  font-size: 12px;
  color: var(--ink-500);
  min-height: 34px;
  margin-bottom: 10px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.dish-footer {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: 12px;
}

.dish-price {
  color: var(--brand-primary);
  font-size: 20px;
  font-weight: 800;
}

.dish-price .yen {
  font-size: 13px;
}

.dish-stock {
  font-size: 11.5px;
  color: var(--ink-500);
}

.dish-stock.low {
  color: var(--danger);
}

.add-btn {
  width: 100%;
}

.cart-bar {
  position: fixed;
  right: 32px;
  bottom: 26px;
  background: #fff;
  border-radius: 999px;
  box-shadow: var(--shadow-lg);
  padding: 10px 12px 10px 22px;
  display: flex;
  align-items: center;
  gap: 18px;
  z-index: 20;
}

.cart-summary {
  display: flex;
  align-items: center;
  gap: 10px;
  font-weight: 600;
}

.cart-icon {
  font-size: 18px;
}

.cart-total {
  color: var(--brand-primary);
  font-size: 16px;
  font-weight: 800;
}

.cart-slide-enter-active,
.cart-slide-leave-active {
  transition: all 0.25s ease;
}

.cart-slide-enter-from,
.cart-slide-leave-to {
  opacity: 0;
  transform: translateY(16px);
}
</style>
