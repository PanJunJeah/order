package com.order.agent.service;

import com.order.agent.domain.Order;
import com.order.agent.domain.OrderDraft;
import com.order.agent.domain.OrderItem;
import com.order.agent.repository.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 真实订单服务 —— <b>绝不向 Agent 暴露</b>（MySQL 持久化）。
 *
 * <p>这是「人工确认闸门」的另一半：只有前端用户点击“确认下单”后，
 * {@code confirmDraft(draftId)} 才会被调用并产生真实订单；Agent 的工具集里
 * 没有任何方法能触达这里。确认本身也是幂等的：同一草稿重复确认返回同一订单。
 */
@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final DraftService draftService;

    public OrderService(OrderRepository orderRepository, DraftService draftService) {
        this.orderRepository = orderRepository;
        this.draftService = draftService;
    }

    /**
     * 人工确认闸门：将草稿转为真实订单。
     *
     * @throws IllegalArgumentException 草稿不存在 / 已取消时拒绝
     */
    @Transactional
    public synchronized Order confirmDraft(String draftId) {
        OrderDraft draft = draftService.findById(draftId);
        if (draft == null) {
            throw new IllegalArgumentException("草稿不存在或已过期：" + draftId);
        }
        if (OrderDraft.STATUS_CANCELLED.equals(draft.status())) {
            throw new IllegalArgumentException("草稿已取消，无法下单：" + draftId);
        }
        if (OrderDraft.STATUS_CONFIRMED.equals(draft.status()) && draft.orderId() != null) {
            // 幂等：重复确认返回同一订单，不产生第二笔
            return orderRepository.findById(draft.orderId())
                    .orElseThrow(() -> new IllegalStateException("订单状态异常：" + draft.orderId()));
        }

        String orderId = nextOrderId();
        Order order = new Order(orderId, Order.SOURCE_AGENT_DRAFT, draft.tableNo(),
                draft.address(), draft.remark(), draft.promoCode(), draft.items(),
                draft.subtotal(), draft.discount(), draft.deliveryFee(), draft.payable(),
                Order.STATUS_CONFIRMED, Instant.now(), draftId);
        orderRepository.save(order);
        draftService.markConfirmed(draftId, orderId);
        return order;
    }

    /**
     * 手动结算（购物车流程）——同样是用户本人操作，与 Agent 无关。
     */
    @Transactional
    public synchronized Order createDirectOrder(Integer tableNo, String address, String remark,
                                                String promoCode, List<OrderItem> items,
                                                BigDecimal subtotal, BigDecimal discount,
                                                BigDecimal deliveryFee) {
        BigDecimal payable = subtotal.subtract(discount).add(deliveryFee).max(BigDecimal.ZERO);
        Order order = new Order(nextOrderId(), Order.SOURCE_MANUAL, tableNo, address, remark,
                promoCode, List.copyOf(items), subtotal, discount, deliveryFee, payable,
                Order.STATUS_CONFIRMED, Instant.now(), null);
        orderRepository.save(order);
        return order;
    }

    public List<Order> list() {
        return new ArrayList<>(orderRepository.findAllByOrderByCreatedAtAsc());
    }

    @Transactional
    public Order updateStatus(String orderId, String status) {
        if (!List.of(Order.STATUS_CONFIRMED, Order.STATUS_PAID,
                Order.STATUS_PREPARING, Order.STATUS_SERVED).contains(status)) {
            throw new IllegalArgumentException("非法订单状态：" + status);
        }
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("订单不存在：" + orderId));
        order.setStatus(status);
        return order;
    }

    public long count() {
        return orderRepository.count();
    }

    /** 订单号生成：基于库内累计订单数，重启后依然唯一。 */
    private String nextOrderId() {
        return "ORD" + (1000 + orderRepository.count() + 1);
    }
}
