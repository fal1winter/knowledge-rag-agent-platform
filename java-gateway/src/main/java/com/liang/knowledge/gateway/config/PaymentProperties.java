package com.liang.knowledge.gateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "payment")
public class PaymentProperties {
    private int creditRate = 100;
    private Alipay alipay = new Alipay();
    private Wechat wechat = new Wechat();

    @Data
    public static class Alipay {
        private boolean enabled;
        private String gatewayUrl;
        private String appId;
        private String merchantPrivateKey;
        private String alipayPublicKey;
        private String notifyUrl;
        private String returnUrl;
    }

    @Data
    public static class Wechat {
        private boolean enabled;
        private String appId;
        private String mchId;
        private String mchSerialNo;
        private String apiV3Key;
        private String privateKeyPath;
        private String notifyUrl;
    }
}
