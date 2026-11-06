package com.order.agent;

import com.order.agent.config.AgentProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * 在线点餐平台 · Agent 编排层入口。
 *
 * <p>该模块只负责「意图驱动的工具调用」，边界如下：
 * <ul>
 *   <li>暴露给大模型（Agent）的工具仅 4 个：查菜单 / 查配送范围 / 试算优惠 / 生成草稿；</li>
 *   <li>真实的「创建订单 / 扣款」接口绝不注册为工具，只能由前端用户人工确认后调用
 *       {@code POST /api/orders/confirm}；</li>
 *   <li>草稿写入按「sessionId + 菜品哈希」幂等；单次对话工具调用步数受限（默认 6 步）。</li>
 * </ul>
 */
@SpringBootApplication
@EnableConfigurationProperties(AgentProperties.class)
public class OrderAgentServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderAgentServerApplication.class, args);
    }
}
