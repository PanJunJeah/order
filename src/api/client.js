import axios from 'axios';

/**
 * 后端 API 客户端（Agent 编排层 agent-server）。
 * 开发模式由 Vite 代理到 http://localhost:8080。
 */
const http = axios.create({
  baseURL: '/api',
  timeout: 30000
});

http.interceptors.response.use(
  (res) => res,
  (error) => {
    const message =
      error.response?.data?.message ||
      error.message ||
      '网络请求失败';
    return Promise.reject(new Error(message));
  }
);

export const api = {
  // 菜单 / 菜品
  getMenu: (params = {}) => http.get('/menu', { params }).then((r) => r.data.data),
  addDish: (dish) => http.post('/dishes', dish).then((r) => r.data),

  // 结算（人工操作，与 Agent 无关）
  checkout: (payload) => http.post('/checkout', payload).then((r) => r.data),
  deliveryQuote: (address) =>
    http.get('/delivery/quote', { params: { address } }).then((r) => r.data.data),
  trialDiscount: (payload) => http.post('/discount/trial', payload).then((r) => r.data.data),

  // 订单
  getOrders: () => http.get('/orders').then((r) => r.data.data),
  updateOrderStatus: (orderId, status) =>
    http.patch(`/orders/${orderId}/status`, { status }).then((r) => r.data.data),

  // —— 人工确认闸门：唯一能把草稿变成真实订单的入口 ——
  confirmDraft: (draftId) => http.post('/orders/confirm', { draftId }).then((r) => r.data),

  // Agent 对话
  agentChat: (sessionId, message) =>
    http.post('/agent/chat', { sessionId, message }).then((r) => r.data.data),
  resetAgentSession: (sessionId) =>
    http.delete(`/agent/chat/${sessionId}`).then((r) => r.data),
  agentStatus: () => http.get('/agent/status').then((r) => r.data.data)
};
