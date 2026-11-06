package com.order.agent.service;

import com.order.agent.domain.OrderDraft;
import com.order.agent.domain.OrderItem;
import com.order.agent.repository.OrderDraftRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/**
 * 草稿服务 —— Agent 唯一被允许写入的载体（MySQL 持久化）。
 *
 * <p><b>幂等控制</b>：幂等键 = sha256(sessionId | tableNo | address | 菜品哈希 | 优惠码)，
 * 落库为唯一索引；模型重试 / 重复生成相同内容时返回同一个草稿（{@code reused=true}），
 * 绝不产生第二条草稿，防止重复下单入口被多次触发。
 */
@Service
public class DraftService {

    private final OrderDraftRepository draftRepository;

    public DraftService(OrderDraftRepository draftRepository) {
        this.draftRepository = draftRepository;
    }

    public record DraftResult(OrderDraft draft, boolean reused) {
    }

    /**
     * 幂等创建草稿。
     */
    @Transactional
    public synchronized DraftResult create(String sessionId, Integer tableNo, String address,
                                           String remark, String promoCode, List<OrderItem> items,
                                           BigDecimal subtotal, BigDecimal discount,
                                           BigDecimal deliveryFee) {
        String key = idempotencyKey(sessionId, tableNo, address, promoCode, items);

        OrderDraft existing = draftRepository.findByIdempotencyKey(key).orElse(null);
        if (existing != null) {
            return new DraftResult(existing, true);
        }

        BigDecimal payable = subtotal.subtract(discount).add(deliveryFee).max(BigDecimal.ZERO);
        String draftId = "DR" + sha256Hex(key).substring(0, 10).toUpperCase();
        OrderDraft draft = new OrderDraft(draftId, key, sessionId, tableNo, address, remark,
                promoCode, List.copyOf(items), subtotal, discount, deliveryFee, payable,
                OrderDraft.STATUS_DRAFT, Instant.now(), null, null);

        draftRepository.save(draft);
        return new DraftResult(draft, false);
    }

    public OrderDraft findById(String draftId) {
        return draftId == null ? null : draftRepository.findById(draftId).orElse(null);
    }

    /** 会话最近一次生成的草稿（供聊天接口返回结构化草稿卡片）。 */
    public OrderDraft findLatestBySession(String sessionId) {
        return sessionId == null ? null
                : draftRepository.findTopBySessionIdOrderByCreatedAtDesc(sessionId).orElse(null);
    }

    /** 草稿被人工确认后落定（绑定真实订单号，防止重复确认）。 */
    @Transactional
    public void markConfirmed(String draftId, String orderId) {
        OrderDraft draft = findById(draftId);
        if (draft != null) {
            draft.markConfirmed(orderId);
        }
    }

    @Transactional
    public void cancel(String draftId) {
        OrderDraft draft = findById(draftId);
        if (draft != null) {
            draft.markCancelled();
        }
    }

    public long count() {
        return draftRepository.count();
    }

    /**
     * 幂等键：sessionId + 业务要素 + 菜品内容哈希。
     */
    public static String idempotencyKey(String sessionId, Integer tableNo, String address,
                                        String promoCode, List<OrderItem> items) {
        String itemsCanonical = items.stream()
                .sorted(Comparator.comparingLong(OrderItem::dishId))
                .map(i -> i.dishId() + "x" + i.quantity())
                .reduce("", (a, b) -> a + "," + b);
        String raw = String.join("|",
                String.valueOf(sessionId),
                String.valueOf(tableNo),
                String.valueOf(address),
                String.valueOf(promoCode),
                itemsCanonical);
        return sha256Hex(raw);
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }
}
