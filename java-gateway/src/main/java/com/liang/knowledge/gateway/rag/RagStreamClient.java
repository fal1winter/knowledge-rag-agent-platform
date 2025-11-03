package com.liang.knowledge.gateway.rag;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

/**
 * RAG 流式调用客户端。
 * 通过 SSE 协议逐行读取 Python 端 /api/chat/stream 的输出，
 * 每接收一行事件立即推送到回调函数。
 */
@Component
public class RagStreamClient {
    private final String ragBaseUrl;
    private final String gatewaySecret;

    public RagStreamClient(
            @Value("${rag.python-api-base-url}") String ragBaseUrl,
            @Value("${auth.gateway-internal-secret:}") String gatewaySecret
    ) {
        this.ragBaseUrl = ragBaseUrl;
        this.gatewaySecret = gatewaySecret;
    }

    /**
     * 发起流式请求，逐行回调 SSE 数据。
     *
     * @param requestJson 序列化后的请求体 JSON
     * @param onEvent     每行 data: 内容的回调
     * @param onComplete  流结束回调
     * @param onError     异常回调
     */
    public void stream(String requestJson, Consumer<String> onEvent, Runnable onComplete, Consumer<Exception> onError) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(ragBaseUrl + "/api/chat/stream");
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
            connection.setRequestProperty(HttpHeaders.ACCEPT, "text/event-stream");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(120000);

            if (gatewaySecret != null && !gatewaySecret.isEmpty()) {
                connection.setRequestProperty("X-Gateway-Secret", gatewaySecret);
            }

            try (OutputStream os = connection.getOutputStream()) {
                os.write(requestJson.getBytes(StandardCharsets.UTF_8));
                os.flush();
            }

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("data: ")) {
                        String data = line.substring(6);
                        if ("[DONE]".equals(data)) {
                            break;
                        }
                        onEvent.accept(data);
                    }
                }
            }
            onComplete.run();
        } catch (Exception e) {
            onError.accept(e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
}
