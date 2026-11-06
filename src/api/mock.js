import axios from 'axios';
import MockAdapter from 'axios-mock-adapter';

/**
 * Mock 模式（npm run mock / VITE_API_MOCK=true）：
 * 与真实后端 agent-server 接口形状完全一致，可离线演示前端全部流程。
 * 包含与后端离线 Agent 相同的意图路由与安全边界（拒绝直接交易）。
 */

const instance = axios.create({ baseURL: '/api', timeout: 10000 });
const mock = new MockAdapter(instance, { delayResponse: 350 });

const dishes = [
  { id: 1, name: '宫保鸡丁', category: '川菜', description: '鸡丁、花生、干辣椒，经典下饭', price: 38, stock: 100, emoji: '🍗', spicy: true, image: 'https://upload.wikimedia.org/wikipedia/commons/thumb/2/2f/Kung_Pao_Chicken_at_Liqiaoshun_Restaurant_%2820220310175424%29.jpg/960px-Kung_Pao_Chicken_at_Liqiaoshun_Restaurant_%2820220310175424%29.jpg' },
  { id: 2, name: '鱼香肉丝', category: '川菜', description: '咸甜酸辣兼备的经典川味', price: 32, stock: 100, emoji: '🐟', spicy: false, image: 'https://upload.wikimedia.org/wikipedia/commons/thumb/1/1b/Rice_topped_with_Yuxiang_Rousi_at_Beixinqiao_%2820221031111236%29.jpg/960px-Rice_topped_with_Yuxiang_Rousi_at_Beixinqiao_%2820221031111236%29.jpg' },
  { id: 3, name: '麻婆豆腐', category: '川菜', description: '麻辣鲜香，嫩豆腐配肉末', price: 28, stock: 80, emoji: '🌶️', spicy: true, image: 'https://upload.wikimedia.org/wikipedia/commons/thumb/7/73/Authentic_Mapo_Tofu.jpg/960px-Authentic_Mapo_Tofu.jpg' },
  { id: 4, name: '水煮鱼', category: '川菜', description: '鲜嫩鱼片，麻辣过瘾', price: 68, stock: 50, emoji: '🐠', spicy: true, image: 'https://upload.wikimedia.org/wikipedia/commons/4/47/Shuizhufish2.JPG' },
  { id: 5, name: '红烧肉', category: '家常菜', description: '肥而不腻，入口即化', price: 45, stock: 60, emoji: '🥩', spicy: false, image: 'https://upload.wikimedia.org/wikipedia/commons/thumb/4/4e/Red_braised_pork_%2820141106191221%29.JPG/960px-Red_braised_pork_%2820141106191221%29.JPG' },
  { id: 6, name: '番茄炒蛋', category: '家常菜', description: '酸甜开胃，家常味道', price: 22, stock: 120, emoji: '🍅', spicy: false, image: 'https://upload.wikimedia.org/wikipedia/commons/thumb/d/d4/Tomato_scrambled_eggs_noodles.jpg/960px-Tomato_scrambled_eggs_noodles.jpg' },
  { id: 7, name: '西湖牛肉羹', category: '汤羹', description: '牛肉末与蛋花，鲜美爽滑', price: 26, stock: 70, emoji: '🥣', spicy: false, image: 'https://upload.wikimedia.org/wikipedia/commons/thumb/1/1d/Beef_Noodles_soup_from_Jinyi_Beef_Noodle_Soup_Inn_in_Shenzhen.jpg/960px-Beef_Noodles_soup_from_Jinyi_Beef_Noodle_Soup_Inn_in_Shenzhen.jpg' },
  { id: 8, name: '白米饭', category: '主食', description: '东北大米，粒粒饱满', price: 3, stock: 500, emoji: '🍚', spicy: false, image: 'https://upload.wikimedia.org/wikipedia/commons/thumb/d/d6/A_bowl_of_rice.jpg/960px-A_bowl_of_rice.jpg' },
  { id: 9, name: '可乐', category: '饮品', description: '冰镇可乐 330ml', price: 6, stock: 200, emoji: '🥤', spicy: false, image: 'https://upload.wikimedia.org/wikipedia/commons/thumb/2/2f/Coca-cola_50cl_can_-_Italia.jpg/960px-Coca-cola_50cl_can_-_Italia.jpg' },
  { id: 10, name: '酸梅汤', category: '饮品', description: '古法熬制，解辣解腻', price: 10, stock: 150, emoji: '🧋', spicy: false, image: 'https://upload.wikimedia.org/wikipedia/commons/thumb/6/60/Sour_plum_drink.jpg/960px-Sour_plum_drink.jpg' }
];

const zones = { 万达广场: 1.2, 火车站: 2.8, 大学城: 5.5, 高新区: 7.9, 机场: 12.5 };
const orders = [];
const drafts = new Map();
let orderSeq = 1000;

const ok = (data, message = 'ok') => [200, { success: true, message, data }];
const fail = (message, code = 400) => [code, { success: false, message, data: null }];

// ---------- 菜单 ----------
mock.onGet('/menu').reply((config) => {
  const { keyword, category } = config.params || {};
  let list = dishes;
  if (keyword) list = list.filter((d) => d.name.includes(keyword));
  if (category) list = list.filter((d) => d.category === category);
  return ok([...list]);
});
mock.onPost('/dishes').reply((config) => {
  const d = JSON.parse(config.data);
  const dish = { id: Date.now(), ...d, emoji: d.emoji || '🍽️', stock: d.stock || 100 };
  dishes.push(dish);
  return ok(dish, '菜品上架成功');
});

// ---------- 配送 / 优惠 ----------
mock.onGet('/delivery/quote').reply((config) => {
  const address = config.params?.address || '';
  const km = zones[address] ?? 3.5;
  if (km > 10) return ok({ address, zone: '超出配送范围', distanceKm: km, deliveryFee: 0, etaMinutes: -1, deliverable: false });
  const zone = km <= 3 ? '市中心' : km <= 6 ? '近郊' : '远郊';
  const fee = km <= 3 ? 0 : km <= 6 ? 5 : 8;
  const eta = km <= 3 ? 20 : km <= 6 ? 35 : 50;
  return ok({ address, zone, distanceKm: km, deliveryFee: fee, etaMinutes: eta, deliverable: true });
});
mock.onPost('/discount/trial').reply((config) => {
  const { items, promoCode } = JSON.parse(config.data);
  const list = (items || []).map((i) => {
    const d = dishes.find((x) => x.id === i.dishId);
    return { dishId: i.dishId, name: d.name, price: d.price, quantity: i.quantity };
  });
  const subtotal = list.reduce((s, i) => s + i.price * i.quantity, 0);
  const promos = { NEW10: [60, 10], WELCOME15: [80, 15], FULL20: [120, 20] };
  let discount = subtotal >= 100 ? 20 : subtotal >= 50 ? 5 : 0;
  let rule = discount ? '自动满减' : '无满减活动';
  if (promoCode && promos[promoCode.toUpperCase()]) {
    const [min, off] = promos[promoCode.toUpperCase()];
    if (subtotal >= min) { discount = off; rule = `优惠码 ${promoCode}`; }
  }
  return ok({ subtotal, rule, discount, payable: subtotal - discount, promoCode, promoApplied: !!promoCode && rule.includes('优惠码') });
});

// ---------- 订单（人工操作） ----------
mock.onPost('/checkout').reply((config) => {
  const payload = JSON.parse(config.data);
  const items = (payload.items || []).map((i) => {
    const d = dishes.find((x) => x.id === i.dishId);
    return { dishId: d.id, name: d.name, price: d.price, quantity: i.quantity };
  });
  const subtotal = items.reduce((s, i) => s + i.price * i.quantity, 0);
  const discount = subtotal >= 100 ? 20 : subtotal >= 50 ? 5 : 0;
  const address = payload.address || '堂食';
  const deliveryFee = address === '堂食' ? 0 : (zones[address] ?? 3.5) <= 6 ? 5 : 8;
  const order = {
    orderId: `ORD${++orderSeq}`, source: 'MANUAL', tableNo: payload.tableNo ?? null,
    address, remark: payload.remark, promoCode: payload.promoCode, items,
    subtotal, discount, deliveryFee, payable: subtotal - discount + deliveryFee,
    status: 'CONFIRMED', createdAt: new Date().toISOString(), draftId: null
  };
  orders.push(order);
  return ok(order, '下单成功');
});
mock.onGet('/orders').reply(() => ok([...orders].reverse()));
mock.onPatch(/\/orders\/[^/]+\/status/).reply((config) => {
  const orderId = decodeURIComponent(config.url.split('/')[3]);
  const { status } = JSON.parse(config.data);
  const order = orders.find((o) => o.orderId === orderId);
  if (!order) return fail('订单不存在', 404);
  order.status = status;
  return ok(order);
});
mock.onPost('/orders/confirm').reply((config) => {
  const { draftId } = JSON.parse(config.data);
  const draft = drafts.get(draftId);
  if (!draft) return fail('草稿不存在或已过期');
  if (draft.confirmedOrderId) {
    const existing = orders.find((o) => o.orderId === draft.confirmedOrderId);
    return ok(existing, '该草稿已确认（幂等返回同一订单）');
  }
  const order = {
    orderId: `ORD${++orderSeq}`, source: 'AGENT_DRAFT', tableNo: draft.tableNo,
    address: draft.address, remark: draft.remark, promoCode: draft.promoCode, items: draft.items,
    subtotal: draft.subtotal, discount: draft.discount, deliveryFee: draft.deliveryFee,
    payable: draft.payable, status: 'CONFIRMED', createdAt: new Date().toISOString(), draftId
  };
  orders.push(order);
  draft.confirmedOrderId = order.orderId;
  return ok(order, '已确认下单');
});

// ---------- Agent 聊天（离线意图路由，安全边界与后端一致） ----------
const REFUSAL = '我没有权限直接执行交易，只能生成草稿';
const stepCounts = new Map();

function extractDishes(msg) {
  const wanted = [];
  for (const d of dishes) {
    const re = new RegExp(`([1-9一二两三四五六七八九十])?\\s*[份碗盘杯个]?\\s*${d.name}`);
    const m = msg.match(re);
    if (m) {
      const qty = m[1] ? (parseInt(m[1], 10) || ({ 一: 1, 二: 2, 两: 2, 三: 3, 四: 4, 五: 5, 六: 6, 七: 7, 八: 8, 九: 9, 十: 10 }[m[1]] || 1)) : 1;
      wanted.push({ ...d, quantity: qty });
    }
  }
  return wanted;
}

mock.onPost('/agent/chat').reply((config) => {
  const { sessionId, message } = JSON.parse(config.data);
  const sid = sessionId || `s-${Date.now()}`;
  const steps = [];
  const msg = message || '';
  const wanted = extractDishes(msg);
  const transaction = ['支付', '付款', '扣款', '直接下单', '立即下单', '不用确认', '无需确认'].some((w) => msg.includes(w));
  const addr = ['万达广场', '火车站', '大学城', '高新区', '机场'].find((a) => msg.includes(a));

  let reply = '';
  let draft = null;

  const addStep = (tool, summary) => steps.push({ tool, summary, ok: true });

  if (transaction) {
    reply = `${REFUSAL}。我无法创建真实订单、无法扣款、无法支付。${wanted.length ? '已为您生成一份草稿，' : ''}只有您点击『确认下单』人工确认后，系统才会真正下单。`;
    if (wanted.length) {
      addStep('queryMenu', '查询菜单');
      addStep('trialDiscount', '试算优惠');
      addStep('createOrderDraft', '生成预订单草稿');
      const subtotal = wanted.reduce((s, i) => s + i.price * i.quantity, 0);
      const discount = subtotal >= 100 ? 20 : subtotal >= 50 ? 5 : 0;
      const deliveryFee = addr ? (zones[addr] <= 3 ? 0 : zones[addr] <= 6 ? 5 : 8) : 0;
      draft = {
        draftId: `DR${Math.random().toString(16).slice(2, 12).toUpperCase()}`,
        idempotencyKey: `${sid}-${subtotal}`, reused: false, tableNo: null, address: addr || '堂食',
        remark: null, promoCode: null, items: wanted.map((i) => ({ dishId: i.id, name: i.name, price: i.price, quantity: i.quantity })),
        subtotal, discount, deliveryFee, payable: subtotal - discount + deliveryFee, status: 'DRAFT',
        message: '草稿已生成。注意：这只是一份草稿，不会产生真实订单与扣款；需要您人工确认后，系统才会调用后端接口完成真实下单。'
      };
      drafts.set(draft.draftId, draft);
      reply += `\n\n📝 草稿 ${draft.draftId}：应付 ¥${draft.payable}。请点击卡片上的『确认下单』。`;
    }
  } else if (['菜单', '有什么菜', '推荐'].some((w) => msg.includes(w))) {
    addStep('queryMenu', '查询菜单');
    reply = `为您找到 ${dishes.length} 道菜：\n` + dishes.map((d) => `• ${d.emoji} ${d.name} ¥${d.price}（${d.category}）`).join('\n');
    reply += '\n\n告诉我你想吃什么（如“两份宫保鸡丁”），我可以帮你生成草稿。';
  } else if (['配送', '外卖', '送到', '距离', '运费'].some((w) => msg.includes(w)) && addr) {
    addStep('queryDelivery', '查询配送范围');
    const km = zones[addr];
    if (km > 10) reply = `很抱歉，「${addr}」超出配送范围（最远 10 公里）。`;
    else {
      const zone = km <= 3 ? '市中心' : km <= 6 ? '近郊' : '远郊';
      const fee = km <= 3 ? 0 : km <= 6 ? 5 : 8;
      const eta = km <= 3 ? 20 : km <= 6 ? 35 : 50;
      reply = `「${addr}」可以配送 ✅\n• 区域：${zone}，约 ${km} km\n• 配送费：¥${fee}，预计 ${eta} 分钟送达`;
    }
  } else if (['优惠', '满减', '折扣'].some((w) => msg.includes(w)) && wanted.length) {
    addStep('trialDiscount', '试算优惠');
    const subtotal = wanted.reduce((s, i) => s + i.price * i.quantity, 0);
    const discount = subtotal >= 100 ? 20 : subtotal >= 50 ? 5 : 0;
    reply = `优惠试算：\n• 小计 ¥${subtotal}\n• 规则：${discount ? '自动满减' : '无满减活动'}\n• 减免 -¥${discount}\n• 应付 ¥${subtotal - discount}`;
  } else if (wanted.length) {
    addStep('queryMenu', '查询菜单');
    addStep('trialDiscount', '试算优惠');
    addStep('createOrderDraft', '生成预订单草稿');
    const subtotal = wanted.reduce((s, i) => s + i.price * i.quantity, 0);
    const discount = subtotal >= 100 ? 20 : subtotal >= 50 ? 5 : 0;
    const deliveryFee = addr ? (zones[addr] <= 3 ? 0 : zones[addr] <= 6 ? 5 : 8) : 0;
    draft = {
      draftId: `DR${Math.random().toString(16).slice(2, 12).toUpperCase()}`,
      idempotencyKey: `${sid}-${subtotal}`, reused: false, tableNo: null, address: addr || '堂食',
      remark: null, promoCode: null, items: wanted.map((i) => ({ dishId: i.id, name: i.name, price: i.price, quantity: i.quantity })),
      subtotal, discount, deliveryFee, payable: subtotal - discount + deliveryFee, status: 'DRAFT',
      message: '草稿已生成。注意：这只是一份草稿，不会产生真实订单与扣款；需要您人工确认后，系统才会调用后端接口完成真实下单。'
    };
    drafts.set(draft.draftId, draft);
    reply = `📝 预订单草稿 ${draft.draftId}：\n` + draft.items.map((i) => `• ${i.name} x${i.quantity} ¥${i.price}`).join('\n')
      + `\n小计 ¥${subtotal}，优惠 -¥${discount}，配送费 ¥${deliveryFee}，应付 ¥${draft.payable}`
      + '\n\n⚠️ 这只是一份草稿，不会产生真实订单与扣款。请点击下方卡片上的『确认下单』，经您人工确认后，系统才会调用后端接口完成真实下单。';
  } else {
    reply = `我是「味知」AI 点餐助手。我可以：\n1️⃣ 查菜单（“有什么菜”）；\n2️⃣ 查配送范围（“送到万达广场要多久”）；\n3️⃣ 试算优惠（“算算两份宫保鸡丁的优惠”）；\n4️⃣ 生成预订单草稿（“点一份鱼香肉丝和两碗米饭，送到大学城”）。\n\n⚠️ 注意：${REFUSAL}；草稿需要您人工确认后才会真正下单。`;
  }

  stepCounts.set(sid, (stepCounts.get(sid) || 0) + steps.length);
  const stepsUsed = Math.min(stepCounts.get(sid), 6);
  return ok({
    sessionId: sid, reply, stepsUsed,
    stepsRemaining: Math.max(0, 6 - stepsUsed), limitReached: stepCounts.get(sid) >= 6,
    mode: 'offline', steps, draft
  });
});
mock.onDelete(/\/agent\/chat\/.+/).reply((config) => {
  const sid = decodeURIComponent(config.url.split('/agent/chat/')[1]);
  stepCounts.delete(sid);
  return ok(null, '会话已清空');
});
mock.onGet('/agent/status').reply(() => ok({ mode: 'offline', maxSteps: 6, toolCount: 4, tools: 'queryMenu, queryDelivery, trialDiscount, createOrderDraft' }));

// 与 client.js 相同形状的 API 出口
export const api = {
  getMenu: (params = {}) => instance.get('/menu', { params }).then((r) => r.data.data),
  addDish: (dish) => instance.post('/dishes', dish).then((r) => r.data),
  checkout: (payload) => instance.post('/checkout', payload).then((r) => r.data),
  deliveryQuote: (address) => instance.get('/delivery/quote', { params: { address } }).then((r) => r.data.data),
  trialDiscount: (payload) => instance.post('/discount/trial', payload).then((r) => r.data.data),
  getOrders: () => instance.get('/orders').then((r) => r.data.data),
  updateOrderStatus: (orderId, status) => instance.patch(`/orders/${orderId}/status`, { status }).then((r) => r.data.data),
  confirmDraft: (draftId) => instance.post('/orders/confirm', { draftId }).then((r) => r.data),
  agentChat: (sessionId, message) => instance.post('/agent/chat', { sessionId, message }).then((r) => r.data.data),
  resetAgentSession: (sessionId) => instance.delete(`/agent/chat/${sessionId}`).then((r) => r.data),
  agentStatus: () => instance.get('/agent/status').then((r) => r.data.data)
};

export { instance as mockHttp };
