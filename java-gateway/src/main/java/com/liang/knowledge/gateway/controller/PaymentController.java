package com.liang.knowledge.gateway.controller;

import com.liang.knowledge.gateway.payment.PayChannel;
import com.liang.knowledge.gateway.payment.PaymentCallbackResult;
import com.liang.knowledge.gateway.payment.PaymentCreateResult;
import com.liang.knowledge.gateway.payment.PaymentRequest;
import com.liang.knowledge.gateway.payment.PaymentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {
    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/create")
    public PaymentCreateResult create(@Valid @RequestBody PaymentRequest request) {
        return paymentService.create(request);
    }

    @PostMapping("/alipay/notify")
    public String alipayNotify(@RequestParam Map<String, String> params) {
        PaymentCallbackResult result = paymentService.handleNotify(PayChannel.ALIPAY, params, "", new HashMap<>());
        return result.isSuccess() ? "success" : "failure";
    }

    @PostMapping("/wechat/notify")
    public ResponseEntity<Map<String, String>> wechatNotify(@RequestBody String body, @RequestHeader Map<String, String> headers) {
        PaymentCallbackResult result = paymentService.handleNotify(PayChannel.WECHAT, new HashMap<>(), body, headers);
        Map<String, String> response = new HashMap<>();
        response.put("code", result.isSuccess() ? "SUCCESS" : "FAIL");
        response.put("message", result.getMessage());
        return ResponseEntity.ok(response);
    }
}
