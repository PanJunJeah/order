package com.order.agent.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.order.agent.domain.Dish;
import com.order.agent.service.MenuService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.List;

/**
 * 只读工具 1/3：查菜单。
 */
public class MenuTool {

    private final MenuService menuService;
    private final ObjectMapper objectMapper;

    public MenuTool(MenuService menuService, ObjectMapper objectMapper) {
        this.menuService = menuService;
        this.objectMapper = objectMapper;
    }

    @Tool(description = """
            查询在售菜品/菜单。可按关键词或分类筛选。
            返回菜品 id、名称、分类、价格、库存、辣度等字段。
            这是只读工具：只返回菜单信息，不会产生订单、不会扣款。""")
    public String queryMenu(
            @ToolParam(description = "菜品名称关键词，例如“鱼香”，可为空") String keyword,
            @ToolParam(description = "菜品分类：川菜/家常菜/汤羹/主食/饮品，可为空") String category,
            @ToolParam(description = "最多返回条数，默认 10") Integer limit) {
        List<Dish> dishes = menuService.findAll(keyword, category, limit);
        int total = menuService.findAll(keyword, category, null).size();
        return Json.of(objectMapper)
                .put("count", dishes.size())
                .put("total", total)
                .put("dishes", dishes)
                .toJson();
    }
}
