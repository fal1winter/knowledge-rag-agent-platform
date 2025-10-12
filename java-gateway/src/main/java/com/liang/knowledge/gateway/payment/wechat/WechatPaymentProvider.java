package com.liang.knowledge.gateway.payment.wechat;

import com.liang.knowledge.gateway.config.PaymentProperties;
import com.liang.knowledge.gateway.payment.PayChannel;
import com.liang.knowledge.gateway.payment.PaymentCallbackResult;
import com.liang.knowledge.gateway.payment.PaymentCreateResult;
import com.liang.knowledge.gateway.payment.PaymentOrder;
import com.liang.knowledge.gateway.payment.PaymentProvider;
import com.liang.knowledge.gateway.payment.PaymentStatus;
import com.wechat.pay.java.core.RSAAutoCertificateConfig;
import com.wechat.pay.java.core.notification.NotificationParser;
import com.wechat.pay.java.core.notification.RequestParam;
import com.wechat.pay.java.service.payments.nativepay.NativePayService;
import com.wechat.pay.java.service.payments.nativepay.model.Amount;
import com.wechat.pay.java.service.payments.nativepay.model.PrepayRequest;
import com.wechat.pay.java.service.payments.nativepay.model.PrepayResponse;
import com.wechat.pay.java.service.payments.model.Transaction;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class WechatPaymentProvider implements PaymentProvider {
    private final PaymentProperties properties;

    public WechatPaymentProvider(PaymentProperties properties) {
        this.properties = properties;
    }

    @Override
    public PayChannel channel() {
        return PayChannel.WECHAT;
    }

    @Override
    public PaymentCreateResult create(PaymentOrder order) {
        ensureEnabled();
        NativePayService service = new NativePayService.Builder().config(config()).build();
        PrepayRequest request = new PrepayRequest();
        request.setAppid(properties.getWechat().getAppId());
        request.setMchid(properties.getWechat().getMchId());
        request.setDescription(order.getSubject());
        request.setOutTradeNo(order.getOrderNo());
        request.setNotifyUrl(properties.getWechat().getNotifyUrl());
        Amount amount = new Amount();
        amount.setTotal(order.getAmountFen());
        amount.setCurrency("CNY");
        request.setAmount(amount);
        PrepayResponse response = service.prepay(request);
        return PaymentCreateResult.builder()
                .orderNo(order.getOrderNo())
                .channel(PayChannel.WECHAT)
                .status(PaymentStatus.PENDING)
                .codeUrl(response.getCodeUrl())
                .credits(order.getCredits())
                .build();
    }

    @Override
    public PaymentCallbackResult verifyAndParseNotify(Map<String, String> params, String rawBody, Map<String, String> headers) {
        ensureEnabled();
        RequestParam requestParam = new RequestParam.Builder()
                .serialNumber(headers.get("wechatpay-serial"))
                .nonce(headers.get("wechatpay-nonce"))
                .signature(headers.get("wechatpay-signature"))
                .timestamp(headers.get("wechatpay-timestamp"))
                .body(rawBody)
                .build();
        Transaction transaction = new NotificationParser(config()).parse(requestParam, Transaction.class);
        boolean paid = Transaction.TradeStateEnum.SUCCESS.equals(transaction.getTradeState());
        return PaymentCallbackResult.builder()
                .success(paid)
                .orderNo(transaction.getOutTradeNo())
                .providerTradeNo(transaction.getTransactionId())
                .message(transaction.getTradeState() == null ? "UNKNOWN" : transaction.getTradeState().name())
                .build();
    }

    private RSAAutoCertificateConfig config() {
        PaymentProperties.Wechat wechat = properties.getWechat();
        return new RSAAutoCertificateConfig.Builder()
                .merchantId(wechat.getMchId())
                .privateKeyFromPath(wechat.getPrivateKeyPath())
                .merchantSerialNumber(wechat.getMchSerialNo())
                .apiV3Key(wechat.getApiV3Key())
                .build();
    }

    private void ensureEnabled() {
        if (!properties.getWechat().isEnabled()) {
            throw new IllegalStateException("Wechat Pay is disabled");
        }
    }
}
