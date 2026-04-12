package com.liang.knowledge.gateway.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.time.Duration;
import java.util.Arrays;

/**
 * HTTP 客户端配置。
 * <p>
 * 职责：
 * - 配置到 Python RAG 后端的 RestTemplate（超时、重试）
 * - 请求日志拦截器（DEBUG 级别记录出站请求）
 * - 内置重试拦截器处理瞬时网络抖动
 * <p>
 * 超时策略：
 * - connectTimeout 3s：建连超时，防止 DNS 或网络分区时长时间阻塞
 * - readTimeout 60s：RAG 生成可能耗时较长（模型推理 + 多轮检索），需要较大读超时
 * <p>
 * 重试策略：
 * - 最多重试 3 次（含首次请求）
 * - 指数退避：初始 500ms，最大 3s，倍率 2.0
 * - 仅对 IOException（连接超时、连接重置）重试，非 4xx/5xx 响应
 */
@Configuration
public class RestClientConfig {
    private static final Logger log = LoggerFactory.getLogger(RestClientConfig.class);

    private static final int MAX_RETRIES = 3;
    private static final long INITIAL_BACKOFF_MS = 500L;
    private static final double BACKOFF_MULTIPLIER = 2.0;
    private static final long MAX_BACKOFF_MS = 3000L;

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(60000);

        RestTemplate restTemplate = builder
                .requestFactory(() -> factory)
                .setConnectTimeout(Duration.ofSeconds(3))
                .setReadTimeout(Duration.ofSeconds(60))
                .build();

        // 请求日志拦截器
        ClientHttpRequestInterceptor loggingInterceptor = (request, body, execution) -> {
            log.debug("HTTP OUT: {} {}", request.getMethod(), request.getURI());
            long start = System.currentTimeMillis();
            ClientHttpResponse response = execution.execute(request, body);
            long elapsed = System.currentTimeMillis() - start;
            log.debug("HTTP IN: {} {} → {} ({}ms)",
                    request.getMethod(), request.getURI(), response.getStatusCode(), elapsed);
            return response;
        };

        // 重试拦截器：对 IOException 进行指数退避重试
        ClientHttpRequestInterceptor retryInterceptor = (request, body, execution) -> {
            IOException lastException = null;
            long backoff = INITIAL_BACKOFF_MS;

            for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
                try {
                    return execution.execute(request, body);
                } catch (IOException e) {
                    lastException = e;
                    if (attempt == MAX_RETRIES) {
                        log.warn("HTTP 重试耗尽: {} {} 第 {}/{} 次失败: {}",
                                request.getMethod(), request.getURI(), attempt, MAX_RETRIES, e.getMessage());
                        break;
                    }
                    log.info("HTTP 重试: {} {} 第 {}/{} 次失败, {}ms 后重试: {}",
                            request.getMethod(), request.getURI(), attempt, MAX_RETRIES, backoff, e.getMessage());
                    try {
                        Thread.sleep(backoff);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw e;
                    }
                    backoff = Math.min((long) (backoff * BACKOFF_MULTIPLIER), MAX_BACKOFF_MS);
                }
            }
            throw lastException;
        };

        // 拦截器按顺序执行：先重试，再日志（日志记录的是单次实际请求）
        restTemplate.setInterceptors(Arrays.asList(retryInterceptor, loggingInterceptor));
        return restTemplate;
    }
}
