package com.order.agent.agent;

import org.springframework.ai.chat.messages.Message;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 会话记忆：按 sessionId 保存消息历史（有上限与过期时间）。
 */
public class ConversationStore {

    private static final class Entry {
        final List<Message> messages;
        volatile long lastAccess;

        Entry(List<Message> messages, long lastAccess) {
            this.messages = messages;
            this.lastAccess = lastAccess;
        }
    }

    private final Map<String, Entry> store = new ConcurrentHashMap<>();
    private final int maxMessages;
    private final long ttlMillis;

    public ConversationStore(int maxMessages, long ttlMillis) {
        this.maxMessages = maxMessages;
        this.ttlMillis = ttlMillis;
    }

    public void append(String sessionId, Message message) {
        Entry entry = store.computeIfAbsent(sessionId, k -> new Entry(new ArrayList<>(), 0));
        synchronized (entry.messages) {
            entry.messages.add(message);
            while (entry.messages.size() > maxMessages) {
                entry.messages.remove(0);
            }
        }
        entry.lastAccess = System.currentTimeMillis();
        prune();
    }

    public List<Message> list(String sessionId) {
        Entry entry = store.get(sessionId);
        if (entry == null) {
            return new ArrayList<>();
        }
        synchronized (entry.messages) {
            entry.lastAccess = System.currentTimeMillis();
            return new ArrayList<>(entry.messages);
        }
    }

    public void clear(String sessionId) {
        store.remove(sessionId);
    }

    private void prune() {
        long now = System.currentTimeMillis();
        store.entrySet().removeIf(e -> now - e.getValue().lastAccess > ttlMillis);
    }
}
