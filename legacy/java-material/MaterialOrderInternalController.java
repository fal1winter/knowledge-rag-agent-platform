package com.liang.bbs.rest.controller;

import com.liang.bbs.common.web.basic.ResponseResult;
import com.liang.bbs.material.facade.dto.MaterialOrderDTO;
import com.liang.bbs.material.facade.service.MaterialOrderService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.Reference;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/internal/material/orders")
@Api(tags = "资料订单内部接口")
public class MaterialOrderInternalController {
    @Reference
    private MaterialOrderService orderService;

    @GetMapping("/{orderNo}/payment")
    @ApiOperation("查询资料订单支付快照")
    public ResponseResult<Map<String, Object>> paymentSnapshot(@PathVariable String orderNo) {
        MaterialOrderDTO order = orderService.getOrderByNo(orderNo);
        if (order == null) {
            throw new RuntimeException("订单不存在");
        }
        Map<String, Object> data = new HashMap<>();
        data.put("orderNo", order.getOrderNo());
        data.put("buyerId", order.getBuyerId());
        data.put("priceCredits", order.getPrice());
        data.put("payType", order.getPayType());
        data.put("status", order.getStatus());
        return ResponseResult.success(data);
    }

    @PostMapping("/{orderNo}/paid")
    @ApiOperation("支付网关确认资料订单")
    public ResponseResult<Void> confirmPaidByGateway(
            @PathVariable String orderNo,
            @RequestBody MaterialOrderPaymentConfirmRequest request) {
        log.info("支付网关确认资料订单: orderNo={}, userId={}, channel={}",
            orderNo, request.getUserId(), request.getPayChannel());

        orderService.confirmPaidByGateway(
            orderNo,
            request.getUserId(),
            request.getPaymentOrderNo(),
            request.getProviderTradeNo(),
            request.getPayChannel()
        );

        return ResponseResult.success(null);
    }
}
