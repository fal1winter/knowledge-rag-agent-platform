package com.liang.knowledge.gateway.order;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Component
public class HttpMaterialOrderGateway implements MaterialOrderGateway {
    private final RestTemplate restTemplate;
    private final String materialOrderEndpoint;

    public HttpMaterialOrderGateway(
            RestTemplate restTemplate,
            @Value("${material-service.order-endpoint}") String materialOrderEndpoint
    ) {
        this.restTemplate = restTemplate;
        this.materialOrderEndpoint = trimTrailingSlash(materialOrderEndpoint);
    }

    @Override
    public MaterialOrderSnapshot getPaymentSnapshot(String orderNo) {
        Map response = restTemplate.getForObject(
                materialOrderEndpoint + "/internal/material/orders/" + orderNo + "/payment",
                Map.class
        );
        Object data = response == null ? null : response.get("data");
        if (!(data instanceof Map)) {
            throw new IllegalStateException("material order payment snapshot is empty: " + orderNo);
        }
        Map snapshotMap = (Map) data;
        MaterialOrderSnapshot snapshot = new MaterialOrderSnapshot();
        snapshot.setOrderNo(asString(snapshotMap.get("orderNo")));
        snapshot.setBuyerId(asLong(snapshotMap.get("buyerId")));
        snapshot.setPriceCredits(asInteger(snapshotMap.get("priceCredits")));
        snapshot.setPayType(asInteger(snapshotMap.get("payType")));
        snapshot.setStatus(asInteger(snapshotMap.get("status")));
        return snapshot;
    }

    @Override
    public void confirmPaid(String orderNo, Long userId, String paymentOrderNo, String providerTradeNo, String payChannel) {
        Map<String, Object> body = new HashMap<>();
        body.put("userId", userId);
        body.put("paymentOrderNo", paymentOrderNo);
        body.put("providerTradeNo", providerTradeNo);
        body.put("payChannel", payChannel);
        restTemplate.postForObject(materialOrderEndpoint + "/internal/material/orders/" + orderNo + "/paid", body, Void.class);
    }

    private String trimTrailingSlash(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Long asLong(Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return value == null ? null : Long.valueOf(String.valueOf(value));
    }

    private Integer asInteger(Object value) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return value == null ? null : Integer.valueOf(String.valueOf(value));
    }
}
