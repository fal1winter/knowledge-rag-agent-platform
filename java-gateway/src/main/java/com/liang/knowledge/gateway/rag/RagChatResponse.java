package com.liang.knowledge.gateway.rag;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * RAG 对话响应结构体。
 * 统一前端接收格式，避免直接透传 Python 端原始 Map。
 */
@Data
public class RagChatResponse {
    private String answer;
    private RouteInfo route;
    private List<Citation> citations;
    private Map<String, Object> debug;

    @Data
    public static class RouteInfo {
        private String intent;
        private String stage;
        private String model;
        private double confidence;
    }

    @Data
    public static class Citation {
        private String chunkId;
        private String documentId;
        private String title;
        private String snippet;
        private double score;
    }

    /**
     * 从 Python 端返回的原始 Map 构建类型化响应。
     */
    @SuppressWarnings("unchecked")
    public static RagChatResponse fromRawMap(Map<String, Object> raw) {
        RagChatResponse resp = new RagChatResponse();
        if (raw == null) {
            resp.setAnswer("");
            return resp;
        }
        resp.setAnswer((String) raw.getOrDefault("answer", ""));
        resp.setDebug((Map<String, Object>) raw.get("debug"));

        // 解析路由信息
        Object routeObj = raw.get("route");
        if (routeObj instanceof Map) {
            Map<String, Object> routeMap = (Map<String, Object>) routeObj;
            RouteInfo route = new RouteInfo();
            route.setIntent((String) routeMap.getOrDefault("intent", ""));
            route.setStage((String) routeMap.getOrDefault("stage", ""));
            route.setModel((String) routeMap.getOrDefault("model", ""));
            Object conf = routeMap.get("confidence");
            route.setConfidence(conf instanceof Number ? ((Number) conf).doubleValue() : 0.0);
            resp.setRoute(route);
        }

        // 解析引用列表
        Object citationsObj = raw.get("citations");
        if (citationsObj instanceof List) {
            List<Map<String, Object>> citationList = (List<Map<String, Object>>) citationsObj;
            List<Citation> citations = new java.util.ArrayList<>();
            for (Map<String, Object> c : citationList) {
                Citation citation = new Citation();
                citation.setChunkId((String) c.get("chunk_id"));
                citation.setDocumentId((String) c.get("document_id"));
                citation.setTitle((String) c.get("title"));
                citation.setSnippet((String) c.get("snippet"));
                Object score = c.get("score");
                citation.setScore(score instanceof Number ? ((Number) score).doubleValue() : 0.0);
                citations.add(citation);
            }
            resp.setCitations(citations);
        }

        return resp;
    }
}
