package com.liang.knowledge.gateway;

import com.liang.knowledge.gateway.config.PaymentProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(PaymentProperties.class)
public class KnowledgeGatewayApplication {
    public static void main(String[] args) {
        SpringApplication.run(KnowledgeGatewayApplication.class, args);
    }
}
