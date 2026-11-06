package com.order.agent.config;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 会话级工具调用步数存储：按 sessionId 计数，带惰性过期清理。
 *
 * <p>每个会话的计数由 {@link StepLimitedToolCallingManager} 在框架
 * 内部工具循环中推进；新会话从 0 开始。
 */
public class SessionStepStore {

    private static final class Entry {
        final AtomicInteger counter;
        volatile long lastAccess;

        Entry(AtomicInteger counter, long lastAccess) {
            this.counter = counter;
            this.lastAccess = lastAccess;
        }
    }

    private final ConcurrentHashMap<String, Entry> counters = new ConcurrentHashMap<>();
    private final long ttlMillis;

    public SessionStepStore(long ttlMillis) {
        this.ttlMillis = ttlMillis;
    }

    /** 步数 +1 并返回新值。 */
    public int incrementAndGet(String sessionId) {
        Entry entry = counters.computeIfAbsent(sessionId,
                k -> new Entry(new AtomicInteger(0), System.currentTimeMillis()));
        entry.lastAccess = System.currentTimeMillis();
        return entry.counter.incrementAndGet();
    }

    public int count(String sessionId) {
        Entry entry = counters.get(sessionId);
        return entry == null ? 0 : entry.counter.get();
    }

    /** 记录一次工具调用（工具名 + 是否成功），供前端展示“已用步数/调用轨迹”。 */
    public void recordToolCall(String sessionId, String toolName, boolean ok) {
        Entry entry = counters.computeIfAbsent(sessionId,
                k -> new Entry(new AtomicInteger(0), System.currentTimeMillis()));
        entry.lastAccess = System.currentTimeMillis();
        // 计数不变，轨迹由调用方另行维护
    }

    public void reset(String sessionId) {
        counters.remove(sessionId);
    }

    /** 惰性清理过期会话（每次新建会话时顺带执行）。 */
    private void prune() {
        long now = System.currentTimeMillis();
        counters.entrySet().removeIf(e -> now - e.getValue().lastAccess > ttlMillis);
    }
}
