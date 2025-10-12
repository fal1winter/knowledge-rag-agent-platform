package com.liang.knowledge.gateway.user;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Component
public class HttpUserCreditGateway implements UserCreditGateway {
    private final RestTemplate restTemplate;
    private final String creditEndpoint;

    public HttpUserCreditGateway(RestTemplate restTemplate, @Value("${user-service.credit-endpoint}") String creditEndpoint) {
        this.restTemplate = restTemplate;
        this.creditEndpoint = creditEndpoint;
    }

    @Override
    public boolean addCredits(Long userId, int amount, String type, String remark, String relatedOrderNo) {
        Map<String, Object> body = new HashMap<>();
        body.put("userId", userId);
        body.put("amount", amount);
        body.put("type", type);
        body.put("remark", remark);
        body.put("relatedOrderNo", relatedOrderNo);
        Boolean result = restTemplate.postForObject(creditEndpoint + "/internal/user/credits/add", body, Boolean.class);
        return Boolean.TRUE.equals(result);
    }

    @Override
    public boolean deductCredits(Long userId, int amount, String type, String remark, String relatedOrderNo) {
        Map<String, Object> body = new HashMap<>();
        body.put("userId", userId);
        body.put("amount", amount);
        body.put("type", type);
        body.put("remark", remark);
        body.put("relatedOrderNo", relatedOrderNo);
        Boolean result = restTemplate.postForObject(creditEndpoint + "/internal/user/credits/deduct", body, Boolean.class);
        return Boolean.TRUE.equals(result);
    }

    @Override
    public int getCredits(Long userId) {
        Integer result = restTemplate.getForObject(creditEndpoint + "/internal/user/credits/" + userId, Integer.class);
        return result == null ? 0 : result;
    }
}
