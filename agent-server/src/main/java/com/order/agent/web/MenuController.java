package com.order.agent.web;

import com.order.agent.domain.Dish;
import com.order.agent.service.MenuService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

/**
 * 菜单 / 菜品上架（原有业务接口）。
 */
@RestController
@RequestMapping("/api")
public class MenuController {

    private final MenuService menuService;

    public MenuController(MenuService menuService) {
        this.menuService = menuService;
    }

    public record AddDishRequest(
            @NotBlank(message = "菜品名称不能为空") String name,
            String category,
            String description,
            @NotNull(message = "价格不能为空") @Positive(message = "价格必须大于0") BigDecimal price,
            @PositiveOrZero(message = "库存不能为负") int stock,
            String emoji,
            String image,
            boolean spicy) {
    }

    @GetMapping("/menu")
    public ApiResponse<List<Dish>> menu(@RequestParam(required = false) String keyword,
                                        @RequestParam(required = false) String category,
                                        @RequestParam(required = false) Integer limit) {
        return ApiResponse.ok(menuService.findAll(keyword, category, limit));
    }

    @PostMapping("/dishes")
    public ApiResponse<Dish> addDish(@RequestBody AddDishRequest request) {
        Dish dish = menuService.addDish(request.name(), request.category(), request.description(),
                request.price(), request.stock(), request.emoji(), request.image(), request.spicy());
        return ApiResponse.ok("菜品上架成功", dish);
    }
}
