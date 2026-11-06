package com.order.agent.web;

import com.order.agent.domain.DeliveryQuote;
import com.order.agent.service.DeliveryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 配送报价（结算页展示用，只读）。
 */
@RestController
@RequestMapping("/api/delivery")
public class DeliveryController {

    private final DeliveryService deliveryService;

    public DeliveryController(DeliveryService deliveryService) {
        this.deliveryService = deliveryService;
    }

    @GetMapping("/quote")
    public ApiResponse<DeliveryQuote> quote(@RequestParam String address) {
        return ApiResponse.ok(deliveryService.quote(address));
    }
}
