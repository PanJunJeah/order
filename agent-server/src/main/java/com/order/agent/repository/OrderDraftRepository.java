package com.order.agent.repository;

import com.order.agent.domain.OrderDraft;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 草稿仓储。幂等键唯一索引由 {@code idempotencyKey} 列承担。
 */
public interface OrderDraftRepository extends JpaRepository<OrderDraft, String> {

    Optional<OrderDraft> findByIdempotencyKey(String idempotencyKey);

    Optional<OrderDraft> findTopBySessionIdOrderByCreatedAtDesc(String sessionId);
}
