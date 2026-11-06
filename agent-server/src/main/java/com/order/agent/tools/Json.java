package com.order.agent.tools;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 工具返回值 JSON 序列化小工具。
 */
public final class Json {

    private final ObjectMapper objectMapper;
    private final Map<String, Object> map = new LinkedHashMap<>();

    private Json(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public static Json of(ObjectMapper objectMapper) {
        return new Json(objectMapper);
    }

    public Json put(String key, Object value) {
        map.put(key, value);
        return this;
    }

    public String toJson() {
        try {
            return objectMapper.writeValueAsString(map);
        } catch (Exception e) {
            // 兜底：字段都是简单对象，序列化失败时返回错误摘要
            return "{\"error\":\"工具结果序列化失败: " + e.getMessage() + "\"}";
        }
    }
}
