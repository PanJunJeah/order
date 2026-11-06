package com.order.agent.web;

import com.order.agent.domain.DiscountQuote;
import com.order.agent.domain.OrderItem;
import com.order.agent.service.DiscountService;
import com.order.agent.service.MenuService;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/**
 * 优惠试算接口（结算页人工预览用，只读）。
 *
 * <p>注意：这是给<b>用户</b>的普通 REST 接口；Agent 工具集中的
 * trialDiscount 是另一条路径，二者互不影响。
 */
@RestController
@RequestMapping("/api/discount")
public class DiscountController {

    private final MenuService menuService;
    private final DiscountService discountService;

    public DiscountController(MenuService menuService, DiscountService discountService) {
        this.menuService = menuService;
        this.discountService = discountService;
    }

    public record ItemQty(@NotNull long dishId, @Positive int quantity) {
    }

    public record TrialRequest(@NotEmpty List<@NotNull ItemQty> items, String promoCode) {
    }

    @PostMapping("/trial")
    public ApiResponse<DiscountQuote> trial(@RequestBody TrialRequest request) {
        List<OrderItem> items = new ArrayList<>();
        for (ItemQty iq : request.items()) {
            var dish = menuService.findById(iq.dishId());
            if (dish == null) {
                throw new IllegalArgumentException("菜品不存在：dishId=" + iq.dishId());
            }
            items.add(new OrderItem(dish.id(), dish.name(), dish.price(), iq.quantity()));
        }
        return ApiResponse.ok(discountService.trial(items, request.promoCode()));
    }
}
