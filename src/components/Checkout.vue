<template>
  <div class="checkout-page">
    <div class="checkout-grid">
      <!-- 左：购物车明细 -->
      <div class="cart-panel panel-card">
        <div class="panel-head">
          <h2 class="page-title">🛒 购物车 · 桌号 {{ tableNo }}</h2>
          <el-button size="small" text type="danger" @click="clearAll">清空购物车</el-button>
        </div>

        <el-table v-if="cart.length" :data="cart" style="width: 100%">
          <el-table-column label="菜品" min-width="160">
            <template #default="{ row }">
              <span class="item-emoji">{{ row.emoji || '🍽️' }}</span>
              <span class="item-name">{{ row.name }}</span>
            </template>
          </el-table-column>
          <el-table-column label="单价" width="90">
            <template #default="{ row }">¥{{ row.price }}</template>
          </el-table-column>
          <el-table-column label="数量" width="150">
            <template #default="{ row }">
              <el-input-number
                :model-value="row.quantity"
                :min="1"
                :max="99"
                size="small"
                @change="(v) => updateQty(row, v)"
              />
            </template>
          </el-table-column>
          <el-table-column label="小计" width="100">
            <template #default="{ row }">
              <span class="subtotal">¥{{ row.price * row.quantity }}</span>
            </template>
          </el-table-column>
          <el-table-column width="70">
            <template #default="{ row }">
              <el-button text type="danger" size="small" @click="removeItem(row)">移除</el-button>
            </template>
          </el-table-column>
        </el-table>

        <el-empty v-else description="购物车是空的，去点几道菜吧">
          <el-button type="primary" round @click="$emit('navigate', 'Ordering')">去点餐</el-button>
        </el-empty>
      </div>

      <!-- 右：配送与优惠 -->
      <div class="side-panel">
        <div class="panel-card side-card">
          <div class="panel-head"><h3>🛵 配送方式</h3></div>
          <el-radio-group v-model="dineType" class="dine-types">
            <el-radio-button value="堂食">🍽 堂食</el-radio-button>
            <el-radio-button value="外卖">🛵 外卖</el-radio-button>
          </el-radio-group>

          <div v-if="dineType === '外卖'" class="address-row">
            <el-select v-model="address" placeholder="选择收货地址" style="width: 100%">
              <el-option v-for="a in addresses" :key="a" :label="a" :value="a" />
            </el-select>
            <div v-if="delivery" class="delivery-info" :class="{ out: !delivery.deliverable }">
              <template v-if="delivery.deliverable">
                ✅ {{ delivery.zone }} · 约 {{ delivery.distanceKm }}km ·
                配送费 ¥{{ delivery.deliveryFee }} · 预计 {{ delivery.etaMinutes }} 分钟
              </template>
              <template v-else>
                ❌ 该地址超出配送范围（最远 10 公里）
              </template>
            </div>
          </div>
        </div>

        <div class="panel-card side-card">
          <div class="panel-head"><h3>🎟 优惠</h3></div>
          <el-select v-model="promoCode" placeholder="选择优惠码（可不选）" clearable style="width: 100%">
            <el-option label="NEW10 · 满60减10" value="NEW10" />
            <el-option label="WELCOME15 · 满80减15" value="WELCOME15" />
            <el-option label="FULL20 · 满120减20" value="FULL20" />
          </el-select>
          <div v-if="quote" class="quote-info">
            <div class="quote-line"><span>小计</span><span>¥{{ quote.subtotal }}</span></div>
            <div class="quote-line"><span>优惠（{{ quote.rule }}）</span><span class="discount">-¥{{ quote.discount }}</span></div>
            <div v-if="quote.note" class="quote-note">{{ quote.note }}</div>
          </div>
        </div>
      </div>
    </div>

    <!-- 底部结算条 -->
    <div class="pay-bar panel-card" v-if="cart.length">
      <div class="pay-summary">
        <div class="pay-line"><span>小计</span><span>¥{{ subtotal }}</span></div>
        <div class="pay-line"><span>优惠</span><span class="discount">-¥{{ discount }}</span></div>
        <div class="pay-line"><span>配送费</span><span>¥{{ deliveryFee }}</span></div>
        <div class="pay-total">应付 <b>¥{{ payable }}</b></div>
      </div>
      <el-button
        type="primary"
        size="large"
        round
        class="pay-btn"
        :disabled="!canPay"
        :loading="paying"
        @click="checkout"
      >
        确认支付 ¥{{ payable }}
      </el-button>
    </div>
  </div>
</template>

<script>
import { ref, computed, watch, onMounted } from 'vue';
import { useStore } from 'vuex';
import { ElMessage, ElMessageBox } from 'element-plus';
import { api } from '../api';

export default {
  emits: ['navigate'],
  setup(_, { emit }) {
    const store = useStore();
    const paying = ref(false);
    const dineType = ref('堂食');
    const address = ref('万达广场');
    const promoCode = ref('');
    const quote = ref(null);
    const delivery = ref(null);
    const successOrder = ref(null);

    const addresses = ['万达广场', '火车站', '大学城', '高新区'];
    const cart = computed(() => store.state.cart);
    const tableNo = computed(() => store.state.tableNo);
    const subtotal = computed(() => store.getters.cartTotal);

    const discount = computed(() => (quote.value ? quote.value.discount : 0));
    const deliveryFee = computed(() => {
      if (dineType.value === '堂食') return 0;
      return delivery.value && delivery.value.deliverable ? delivery.value.deliveryFee : 0;
    });
    const payable = computed(() => Math.max(0, subtotal.value - discount.value + deliveryFee.value));
    const canPay = computed(
      () => cart.value.length > 0 && (dineType.value === '堂食' || (delivery.value && delivery.value.deliverable))
    );

    const refreshQuote = async () => {
      if (!cart.value.length) {
        quote.value = null;
        return;
      }
      try {
        quote.value = await api.trialDiscount({
          items: cart.value.map((i) => ({ dishId: i.dishId, quantity: i.quantity })),
          promoCode: promoCode.value || null
        });
      } catch (e) {
        quote.value = null;
      }
    };

    const refreshDelivery = async () => {
      if (dineType.value !== '外卖') {
        delivery.value = null;
        return;
      }
      try {
        delivery.value = await api.deliveryQuote(address.value);
      } catch (e) {
        delivery.value = null;
      }
    };

    watch([cart, promoCode], refreshQuote, { deep: true });
    watch([dineType, address], refreshDelivery);

    const updateQty = (row, v) => {
      store.commit('updateCartQuantity', { dishId: row.dishId, quantity: v });
    };

    const removeItem = (row) => {
      store.commit('removeFromCart', row.dishId);
    };

    const clearAll = async () => {
      try {
        await ElMessageBox.confirm('确定清空购物车吗？', '提示', { type: 'warning' });
        store.commit('clearCart');
      } catch {
        /* 取消 */
      }
    };

    const checkout = async () => {
      if (!canPay.value) {
        ElMessage.warning(delivery.value && !delivery.value.deliverable ? '该地址超出配送范围' : '购物车为空');
        return;
      }
      paying.value = true;
      try {
        const res = await api.checkout({
          tableNo: tableNo.value,
          address: dineType.value === '堂食' ? '堂食' : address.value,
          remark: null,
          promoCode: promoCode.value || null,
          items: cart.value.map((i) => ({ dishId: i.dishId, quantity: i.quantity }))
        });
        successOrder.value = res.data;
        store.commit('addOrder', res.data);
        store.commit('clearCart');
        ElMessage.success(`支付成功！订单号 ${res.data.orderId}`);
      } catch (e) {
        ElMessage.error(`结算失败：${e.message}`);
      } finally {
        paying.value = false;
      }
    };

    onMounted(() => {
      refreshQuote();
      refreshDelivery();
    });

    return {
      cart, tableNo, addresses, dineType, address, promoCode,
      quote, delivery, subtotal, discount, deliveryFee, payable,
      canPay, paying, successOrder, updateQty, removeItem, clearAll, checkout
    };
  }
};
</script>

<style scoped>
.checkout-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.checkout-grid {
  display: grid;
  grid-template-columns: 1fr 340px;
  gap: 16px;
  align-items: start;
}

.cart-panel {
  padding: 18px 20px;
}

.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 14px;
}

.panel-head h3 {
  margin: 0;
  font-size: 15px;
  font-weight: 700;
}

.item-emoji {
  margin-right: 8px;
  font-size: 18px;
}

.item-name {
  font-weight: 600;
}

.subtotal {
  color: var(--brand-primary);
  font-weight: 700;
}

.side-panel {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.side-card {
  padding: 16px 18px;
}

.dine-types {
  margin-bottom: 12px;
}

.delivery-info {
  margin-top: 10px;
  font-size: 12.5px;
  color: var(--success);
  background: #ecfaf2;
  border-radius: 8px;
  padding: 8px 12px;
}

.delivery-info.out {
  color: var(--danger);
  background: #fdf1f1;
}

.quote-info {
  margin-top: 12px;
  border-top: 1px dashed var(--line);
  padding-top: 10px;
}

.quote-line {
  display: flex;
  justify-content: space-between;
  font-size: 13px;
  padding: 3px 0;
  color: var(--ink-700);
}

.quote-line .discount,
.pay-line .discount {
  color: var(--success);
}

.quote-note {
  font-size: 11.5px;
  color: var(--warning);
  margin-top: 4px;
}

.pay-bar {
  position: sticky;
  bottom: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 22px;
  background: rgba(255, 255, 255, 0.96);
  backdrop-filter: blur(6px);
}

.pay-summary {
  display: flex;
  gap: 22px;
  align-items: center;
}

.pay-line {
  font-size: 13px;
  color: var(--ink-500);
}

.pay-total {
  font-size: 15px;
  color: var(--ink-900);
}

.pay-total b {
  color: var(--brand-primary);
  font-size: 22px;
}

.pay-btn {
  min-width: 190px;
  box-shadow: 0 6px 16px rgba(255, 106, 43, 0.3);
}
</style>
