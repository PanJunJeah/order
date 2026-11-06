package com.order.agent.service;

import com.order.agent.domain.Dish;
import com.order.agent.repository.DishRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 菜单服务：MySQL 持久化，首次启动由 {@code DataInitializer} 播种 24 道菜。
 */
@Service
public class MenuService {

    private final DishRepository dishRepository;

    public MenuService(DishRepository dishRepository) {
        this.dishRepository = dishRepository;
    }

    public List<Dish> findAll(String keyword, String category, Integer limit) {
        String kw = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        String cat = category == null ? "" : category.trim();
        List<Dish> result = dishRepository.findAllByOrderByIdAsc().stream()
                .filter(d -> kw.isEmpty()
                        || d.name().toLowerCase(Locale.ROOT).contains(kw)
                        || d.description().toLowerCase(Locale.ROOT).contains(kw))
                .filter(d -> cat.isEmpty() || d.category().equals(cat))
                .toList();
        int n = (limit == null || limit <= 0) ? result.size() : Math.min(limit, result.size());
        return new ArrayList<>(result.subList(0, n));
    }

    public Dish findById(long id) {
        return dishRepository.findById(id).orElse(null);
    }

    @Transactional
    public Dish addDish(String name, String category, String description,
                        BigDecimal price, int stock, String emoji, String image, boolean spicy) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("菜品名称不能为空");
        }
        if (price == null || price.signum() <= 0) {
            throw new IllegalArgumentException("价格必须大于 0");
        }
        if (stock < 0) {
            throw new IllegalArgumentException("库存不能为负");
        }
        long newId = dishRepository.findMaxId().orElse(0L) + 1;
        Dish dish = new Dish(newId, name.trim(),
                category == null || category.isBlank() ? "家常菜" : category.trim(),
                description == null ? "" : description.trim(), price, stock,
                emoji == null || emoji.isBlank() ? "🍽️" : emoji,
                image == null || image.isBlank() ? null : image.trim(), spicy);
        return dishRepository.save(dish);
    }
}
