package com.liang.knowledge.gateway.payment.alipay;

import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import com.alipay.api.internal.util.AlipaySignature;
import com.alipay.api.request.AlipayTradePagePayRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.liang.knowledge.gateway.config.PaymentProperties;
import com.liang.knowledge.gateway.payment.PayChannel;
import com.liang.knowledge.gateway.payment.PaymentCallbackResult;
import com.liang.knowledge.gateway.payment.PaymentCreateResult;
import com.liang.knowledge.gateway.payment.PaymentOrder;
import com.liang.knowledge.gateway.payment.PaymentProvider;
import com.liang.knowledge.gateway.payment.PaymentStatus;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class AlipayPaymentProvider implements PaymentProvider {
    private static final String FORMAT = "json";
    private static final String CHARSET = "UTF-8";
    private static final String SIGN_TYPE = "RSA2";

    private final PaymentProperties properties;
    private final ObjectMapper objectMapper;

    public AlipayPaymentProvider(PaymentProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public PayChannel channel() {
        return PayChannel.ALIPAY;
    }

    @Override
    public PaymentCreateResult create(PaymentOrder order) {
        ensureEnabled();
        PaymentProperties.Alipay config = properties.getAlipay();
        AlipayClient client = new DefaultAlipayClient(
                config.getGatewayUrl(),
                config.getAppId(),
                config.getMerchantPrivateKey(),
                FORMAT,
                CHARSET,
                config.getAlipayPublicKey(),
                SIGN_TYPE
        );
        AlipayTradePagePayRequest request = new AlipayTradePagePayRequest();
        request.setNotifyUrl(config.getNotifyUrl());
        request.setReturnUrl(config.getReturnUrl());
        request.setBizContent(toBizContent(order));
        try {
            String form = client.pageExecute(request).getBody();
            return PaymentCreateResult.builder()
                    .orderNo(order.getOrderNo())
                    .channel(PayChannel.ALIPAY)
                    .status(PaymentStatus.PENDING)
                    .payForm(form)
                    .credits(order.getCredits())
                    .build();
        } catch (AlipayApiException e) {
            throw new IllegalStateException("Alipay page pay request failed", e);
        }
    }

    @Override
    public PaymentCallbackResult verifyAndParseNotify(Map<String, String> params, String rawBody, Map<String, String> headers) {
        ensureEnabled();
        try {
            boolean valid = AlipaySignature.rsaCheckV1(
                    params,
                    properties.getAlipay().getAlipayPublicKey(),
                    CHARSET,
                    SIGN_TYPE
            );
            if (!valid) {
                return PaymentCallbackResult.builder().success(false).message("invalid alipay signature").build();
            }
            String tradeStatus = params.get("trade_status");
            boolean paid = "TRADE_SUCCESS".equals(tradeStatus) || "TRADE_FINISHED".equals(tradeStatus);
            return PaymentCallbackResult.builder()
                    .success(paid)
                    .orderNo(params.get("out_trade_no"))
                    .providerTradeNo(params.get("trade_no"))
                    .message(tradeStatus)
                    .build();
        } catch (AlipayApiException e) {
            throw new IllegalStateException("Alipay notify verification failed", e);
        }
    }

    private String toBizContent(PaymentOrder order) {
        try {
            Map<String, Object> content = new LinkedHashMap<>();
            content.put("out_trade_no", order.getOrderNo());
            content.put("total_amount", String.format("%.2f", order.getAmountFen() / 100.0));
            content.put("subject", order.getSubject());
            content.put("product_code", "FAST_INSTANT_TRADE_PAY");
            content.put("passback_params", order.getRelatedBizType());
            return objectMapper.writeValueAsString(content);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to build alipay biz_content", e);
        }
    }

    private void ensureEnabled() {
        if (!properties.getAlipay().isEnabled()) {
            throw new IllegalStateException("Alipay is disabled");
        }
    }
}
