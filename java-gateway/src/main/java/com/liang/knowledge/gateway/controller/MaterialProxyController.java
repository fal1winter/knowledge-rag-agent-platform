package com.liang.knowledge.gateway.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import javax.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.Enumeration;

@RestController
public class MaterialProxyController {
    private final RestTemplate restTemplate;
    private final String materialServiceBaseUrl;

    public MaterialProxyController(
            RestTemplate restTemplate,
            @Value("${material-service.base-url}") String materialServiceBaseUrl
    ) {
        this.restTemplate = restTemplate;
        this.materialServiceBaseUrl = trimTrailingSlash(materialServiceBaseUrl);
    }

    /**
     * 透传素材服务请求，网关注入 X-User-Id / X-Tenant-Id 头供下游鉴权。
     */
    @RequestMapping("/api/bbs/material/**")
    public ResponseEntity<byte[]> forward(HttpServletRequest request, @RequestBody(required = false) byte[] body) {
        String targetUrl = materialServiceBaseUrl + request.getRequestURI();
        if (request.getQueryString() != null && !request.getQueryString().isEmpty()) {
            targetUrl += "?" + request.getQueryString();
        }
        HttpHeaders headers = copyHeaders(request);
        // 注入鉴权后的用户身份，供下游素材服务做行级权限控制
        headers.set("X-User-Id", String.valueOf(com.liang.knowledge.gateway.auth.SecurityContext.requireUserId()));
        headers.set("X-Tenant-Id", com.liang.knowledge.gateway.auth.SecurityContext.requireTenantId());
        HttpEntity<byte[]> entity = new HttpEntity<>(body, headers);
        return restTemplate.exchange(targetUrl, HttpMethod.valueOf(request.getMethod()), entity, byte[].class);
    }

    private HttpHeaders copyHeaders(HttpServletRequest request) {
        HttpHeaders headers = new HttpHeaders();
        Enumeration<String> names = request.getHeaderNames();
        while (names.hasMoreElements()) {
            String name = names.nextElement();
            if ("host".equalsIgnoreCase(name) || "content-length".equalsIgnoreCase(name)) {
                continue;
            }
            headers.put(name, Collections.list(request.getHeaders(name)));
        }
        return headers;
    }

    private String trimTrailingSlash(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
