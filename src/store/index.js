import { createStore } from 'vuex';

const newSessionId = () => `s-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 8)}`;

export default createStore({
  state: {
    currentPage: 'Ordering',
    tableNo: Math.floor(Math.random() * 20) + 1,
    menu: [],
    cart: [], // { dishId, name, price, quantity, emoji }
    orders: [],
    sessionId: newSessionId(),
    agentMode: 'unknown',
    agentMaxSteps: 6
  },

  getters: {
    cartTotal: (state) =>
      state.cart.reduce((sum, i) => sum + i.price * i.quantity, 0),
    cartCount: (state) => state.cart.reduce((sum, i) => sum + i.quantity, 0),
    categories: (state) => [...new Set(state.menu.map((d) => d.category))]
  },

  mutations: {
    setCurrentPage(state, page) {
      state.currentPage = page;
    },
    setTableNo(state, tableNo) {
      state.tableNo = tableNo;
    },
    setMenu(state, menu) {
      state.menu = menu;
    },
    addDish(state, dish) {
      state.menu.push(dish);
    },
    addToCart(state, { dishId, name, price, emoji, quantity = 1 }) {
      const item = state.cart.find((i) => i.dishId === dishId);
      if (item) item.quantity += quantity;
      else state.cart.push({ dishId, name, price, emoji, quantity });
    },
    updateCartQuantity(state, { dishId, quantity }) {
      const item = state.cart.find((i) => i.dishId === dishId);
      if (item) item.quantity = Math.max(1, quantity);
    },
    removeFromCart(state, dishId) {
      state.cart = state.cart.filter((i) => i.dishId !== dishId);
    },
    clearCart(state) {
      state.cart = [];
    },
    setOrders(state, orders) {
      state.orders = orders;
    },
    addOrder(state, order) {
      state.orders.unshift(order);
    },
    updateOrderStatus(state, { orderId, status }) {
      const order = state.orders.find((o) => o.orderId === orderId);
      if (order) order.status = status;
    },
    resetSession(state) {
      state.sessionId = newSessionId();
    },
    setAgentMeta(state, { mode, maxSteps }) {
      state.agentMode = mode;
      state.agentMaxSteps = maxSteps;
    }
  }
});
