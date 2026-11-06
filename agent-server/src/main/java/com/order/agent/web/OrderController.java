package com.order.agent.web;

import com.order.agent.agent.AgentDtos.DraftView;
import com.order.agent.domain.DiscountQuote;
import com.order.agent.domain.DeliveryQuote;
import com.order.agent.domain.Order;
import com.order.agent.domain.OrderDraft;
import com.order.agent.domain.OrderItem;
import com.order.agent.service.DeliveryService;
import com.order.agent.service.DiscountService;
import com.order.agent.service.DraftService;
import com.order.agent.service.MenuService;
import com.order.agent.service.OrderService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 订单接口 —— 人工确认闸门所在地。
 *
 * <p>{@code POST /api/orders/confirm} 是<b>唯一</b>能把草稿变成真实订单的入口，
 * 且只接受前端用户点击确认后传入的 draftId；Agent 的工具集中不存在任何路径
 * 能触达这里（即“不要向 Agent 暴露任何真实的创建订单/扣款接口”）。
 */
@RestController
@RequestMapping("/api")
public class OrderController {

    private final OrderService orderService;
    private final DraftService draftService;
    private final MenuService menuService;
    private final DiscountService discountService;
    private final DeliveryService deliveryService;

    public OrderController(OrderService orderService, DraftService draftService,
                           MenuService menuService, DiscountService discountService,
                           DeliveryService deliveryService) {
        this.orderService = orderService;
        this.draftService = draftService;
        this.menuService = menuService;
        this.discountService = discountService;
        this.deliveryService = deliveryService;
    }

    public record ItemQty(@NotNull long dishId, @Positive int quantity) {
    }

    public record CheckoutRequest(Integer tableNo, String address, String remark,
                                  String promoCode, @NotEmpty List<@NotNull ItemQty> items) {
    }

    public record ConfirmRequest(@NotBlank(message = "缺少 draftId") String draftId) {
    }

    public record StatusRequest(@NotBlank(message = "缺少 status") String status) {
    }

    /** 手动结算（购物车流程）—— 用户本人操作，与 Agent 无关。 */
    @PostMapping("/checkout")
    public ApiResponse<Order> checkout(@RequestBody CheckoutRequest request) {
        List<OrderItem> items = resolveItems(request.items());
        DiscountQuote quote = discountService.trial(items, request.promoCode());
        BigDecimal deliveryFee = resolveDeliveryFee(request.address());
        Order order = orderService.createDirectOrder(request.tableNo(), request.address(),
                request.remark(), request.promoCode(), items, quote.subtotal(),
                quote.discount(), deliveryFee);
        return ApiResponse.ok("下单成功", order);
    }

    /** 人工确认闸门：用户在前端点击“确认下单”后调用，草稿才转真实订单。 */
    @PostMapping("/orders/confirm")
    public ApiResponse<Order> confirmDraft(@RequestBody ConfirmRequest request) {
        Order order = orderService.confirmDraft(request.draftId());
        return ApiResponse.ok("已确认下单", order);
    }

    @GetMapping("/orders")
    public ApiResponse<List<Order>> orders() {
        return ApiResponse.ok(orderService.list());
    }

    @GetMapping("/orders/drafts/{draftId}")
    public ApiResponse<DraftView> draft(@PathVariable String draftId) {
        OrderDraft draft = draftService.findById(draftId);
        if (draft == null) {
            throw new IllegalArgumentException("草稿不存在：" + draftId);
        }
        return ApiResponse.ok(toDraftView(draft));
    }

    @PatchMapping("/orders/{orderId}/status")
    public ApiResponse<Order> updateStatus(@PathVariable String orderId,
                                           @RequestBody StatusRequest request) {
        return ApiResponse.ok(orderService.updateStatus(orderId, request.status()));
    }

    private List<OrderItem> resolveItems(List<ItemQty> items) {
        List<OrderItem> result = new ArrayList<>();
        for (ItemQty iq : items) {
            var dish = menuService.findById(iq.dishId());
            if (dish == null) {
                throw new IllegalArgumentException("菜品不存在：dishId=" + iq.dishId());
            }
            result.add(new OrderItem(dish.id(), dish.name(), dish.price(), iq.quantity()));
        }
        return result;
    }

    private BigDecimal resolveDeliveryFee(String address) {
        String addr = address == null ? "" : address.trim();
        if (addr.isEmpty() || addr.contains("堂食")) {
            return BigDecimal.ZERO;
        }
        DeliveryQuote quote = deliveryService.quote(addr);
        if (!quote.deliverable()) {
            throw new IllegalArgumentException("该地址超出配送范围：" + addr);
        }
        return quote.deliveryFee();
    }

    private DraftView toDraftView(OrderDraft draft) {
        return new DraftView(draft.draftId(), draft.idempotencyKey(), false,
                draft.tableNo(), draft.address(), draft.remark(), draft.promoCode(),
                draft.items(), draft.subtotal(), draft.discount(), draft.deliveryFee(),
                draft.payable(), draft.status(),
                "草稿状态：" + draft.status() + "。真实下单需要人工确认。");
    }
}
