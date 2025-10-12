package com.liang.bbs.rest.controller;

import com.liang.bbs.common.web.basic.ResponseResult;
import com.liang.bbs.material.facade.dto.MaterialOrderDTO;
import com.liang.bbs.material.facade.dto.MaterialPurchaseRequest;
import com.liang.bbs.material.facade.service.MaterialOrderService;
import com.liang.bbs.user.facade.utils.UserContextUtils;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.Reference;
import org.apache.dubbo.rpc.RpcException;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;

/**
 * 资料订单Controller
 *
 */
@Slf4j
@RestController
@RequestMapping("/bbs/material/order")
@Api(tags = "资料订单管理")
public class MaterialOrderController {

    @Reference
    private MaterialOrderService orderService;

    @PostMapping("/create")
    @ApiOperation("创建订单")
    public ResponseResult<MaterialOrderDTO> createOrder(@RequestBody MaterialPurchaseRequest request) {
        Long userId = Long.valueOf(UserContextUtils.currentUser().getUserId());
        log.info("用户 {} 创建订单: materialId={}, payType={}",
            userId, request.getMaterialId(), request.getPayType());

        MaterialOrderDTO order = orderService.createOrder(request, userId);

        return ResponseResult.success(order);
    }

    @PostMapping("/pay/{orderNo}")
    @ApiOperation("支付订单")
    public ResponseResult<Void> payOrder(@PathVariable String orderNo) {
        Long userId = Long.valueOf(UserContextUtils.currentUser().getUserId());
        log.info("用户 {} 支付订单: {}", userId, orderNo);

        orderService.payOrder(orderNo, userId);

        return ResponseResult.success(null);
    }

    @PostMapping("/cancel/{orderNo}")
    @ApiOperation("取消订单")
    public ResponseResult<Void> cancelOrder(@PathVariable String orderNo) {
        Long userId = Long.valueOf(UserContextUtils.currentUser().getUserId());
        log.info("用户 {} 取消订单: {}", userId, orderNo);

        orderService.cancelOrder(orderNo, userId);

        return ResponseResult.success(null);
    }

    @GetMapping("/{orderNo}")
    @ApiOperation("查询订单")
    public ResponseResult<MaterialOrderDTO> getOrder(@PathVariable String orderNo) {
        Long userId = Long.valueOf(UserContextUtils.currentUser().getUserId());
        log.info("用户 {} 查询订单: {}", userId, orderNo);

        MaterialOrderDTO order = orderService.getOrderByNo(orderNo);

        if (!order.getBuyerId().equals(userId) && !order.getSellerId().equals(userId)) {
            return ResponseResult.build(com.liang.bbs.common.enums.ResponseCode.ERROR, null);
        }

        return ResponseResult.success(order);
    }

    @GetMapping("/my-purchase")
    @ApiOperation("我的购买订单")
    public ResponseResult<List<MaterialOrderDTO>> myPurchaseOrders(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        Long userId = Long.valueOf(UserContextUtils.currentUser().getUserId());
        log.info("查询用户 {} 的购买订单", userId);
        try {
            List<MaterialOrderDTO> orders = orderService.getMyPurchaseOrders(userId, pageNum, pageSize);
            return ResponseResult.success(orders);
        } catch (RpcException e) {
            // 降级返回，避免前端直接出现 RPC_EXCEPTION(-3)
            log.error("查询用户 {} 购买订单发生RPC异常，降级返回空列表", userId, e);
            return ResponseResult.success(Collections.emptyList());
        }
    }

    @GetMapping("/my-sales")
    @ApiOperation("我的销售订单")
    public ResponseResult<List<MaterialOrderDTO>> mySalesOrders(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        Long userId = Long.valueOf(UserContextUtils.currentUser().getUserId());
        log.info("查询用户 {} 的销售订单", userId);
        try {
            List<MaterialOrderDTO> orders = orderService.getMySalesOrders(userId, pageNum, pageSize);
            return ResponseResult.success(orders);
        } catch (RpcException e) {
            log.error("查询用户 {} 销售订单发生RPC异常，降级返回空列表", userId, e);
            return ResponseResult.success(Collections.emptyList());
        }
    }
}
