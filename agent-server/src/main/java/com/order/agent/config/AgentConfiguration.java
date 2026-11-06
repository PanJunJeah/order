package com.order.agent.config;

import com.order.agent.agent.AgentSystemPrompt;
import com.order.agent.agent.ConversationStore;
import com.order.agent.agent.OfflineAgent;
import com.order.agent.agent.AgentOrchestrator;
import com.order.agent.service.DeliveryService;
import com.order.agent.service.DiscountService;
import com.order.agent.service.DraftService;
import com.order.agent.service.MenuService;
import com.order.agent.service.OrderService;
import com.order.agent.tools.DeliveryTool;
import com.order.agent.tools.DiscountTool;
import com.order.agent.tools.DraftTool;
import com.order.agent.tools.MenuTool;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Agent 编排层装配。
 *
 * <p>关键安全约束都在这里固化：
 * <ul>
 *   <li>注册给大模型的工具只有 4 个：查菜单 / 查配送 / 试算优惠 / 生成草稿；</li>
 *   <li>{@link OrderService} 等真实交易能力<b>绝不出现在 defaultTools 中</b>；</li>
 *   <li>全局唯一的 {@link ToolCallingManager} 是带步数上限的守卫实现，
 *       OpenAiChatModel 与 ChatClient 都会通过它执行工具。</li>
 * </ul>
 *
 * <p>注意：这里<b>不自定义 ObjectMapper</b>，直接使用 Spring Boot 自动配置的
 * ObjectMapper（含 JavaTimeModule），避免 Instant 等时间类型序列化失败。
 */
@Configuration
public class AgentConfiguration {

    @Bean
    public MenuTool menuTool(MenuService menuService, ObjectMapper objectMapper) {
        return new MenuTool(menuService, objectMapper);
    }

    @Bean
    public DeliveryTool deliveryTool(DeliveryService deliveryService, ObjectMapper objectMapper,
                                     AgentProperties properties) {
        return new DeliveryTool(deliveryService, objectMapper, properties);
    }

    @Bean
    public DiscountTool discountTool(MenuService menuService, DiscountService discountService,
                                     ObjectMapper objectMapper) {
        return new DiscountTool(menuService, discountService, objectMapper);
    }

    @Bean
    public DraftTool draftTool(MenuService menuService, DiscountService discountService,
                               DraftService draftService, DeliveryService deliveryService,
                               ObjectMapper objectMapper) {
        return new DraftTool(menuService, discountService, draftService, deliveryService, objectMapper);
    }

    @Bean
    public SessionStepStore sessionStepStore(AgentProperties properties) {
        return new SessionStepStore(properties.getConversationTtlMinutes() * 60_000L);
    }

    /**
     * 步数守卫版 ToolCallingManager（全局唯一）。
     * 由于它是容器中唯一的该类型 Bean，ChatClientAutoConfiguration /
     * OpenAiChatAutoConfiguration 自动注入的都会是它。
     */
    @Bean
    public StepLimitedToolCallingManager stepLimitedToolCallingManager(
            AgentProperties properties, SessionStepStore stepStore) {
        return new StepLimitedToolCallingManager(
                ToolCallingManager.builder().build(), stepStore, properties.getMaxSteps());
    }

    @Bean
    public ConversationStore conversationStore(AgentProperties properties) {
        return new ConversationStore(properties.getConversationMaxMessages(),
                properties.getConversationTtlMinutes() * 60_000L);
    }

    @Bean
    public ChatClient agentChatClient(ChatModel chatModel,
                                      MenuTool menuTool, DeliveryTool deliveryTool,
                                      DiscountTool discountTool, DraftTool draftTool) {
        return ChatClient.builder(chatModel)
                .defaultSystem(AgentSystemPrompt.SYSTEM_PROMPT)
                // —— 安全边界：仅注册这 4 个工具，真实下单/扣款接口不在其中 ——
                .defaultTools(menuTool, deliveryTool, discountTool, draftTool)
                .build();
    }

    @Bean
    public OfflineAgent offlineAgent(MenuService menuService, DeliveryService deliveryService,
                                     DiscountService discountService, DraftService draftService,
                                     ObjectMapper objectMapper, AgentProperties properties) {
        return new OfflineAgent(menuService, deliveryService, discountService, draftService,
                objectMapper, properties);
    }

    @Bean
    public AgentOrchestrator agentOrchestrator(ChatClient agentChatClient,
                                               OfflineAgent offlineAgent,
                                               ConversationStore conversationStore,
                                               StepLimitedToolCallingManager stepGuard,
                                               DraftService draftService,
                                               AgentProperties properties,
                                               @Value("${spring.ai.openai.api-key:}") String openAiApiKey) {
        return new AgentOrchestrator(agentChatClient, offlineAgent, conversationStore,
                stepGuard, draftService, properties, openAiApiKey);
    }
}
