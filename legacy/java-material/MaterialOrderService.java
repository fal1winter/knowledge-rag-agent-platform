package com.liang.bbs.material.facade.service;

import com.liang.bbs.material.facade.dto.MaterialOrderDTO;
import com.liang.bbs.material.facade.dto.MaterialPurchaseRequest;

import java.util.List;

/**
 * 资料订单服务接口
 *
 */
public interface MaterialOrderService {

    /**
     * 创建订单
     */
    MaterialOrderDTO createOrder(MaterialPurchaseRequest request, Long buyerId);

    /**
     * 支付订单
     */
    void payOrder(String orderNo, Long userId);

    /**
     * 取消订单
     */
    void cancelOrder(String orderNo, Long userId);

    /**
     * 支付网关确认订单。用于支付宝/微信回调，不再执行积分扣减。
     */
    void confirmPaidByGateway(String orderNo, Long userId, String paymentOrderNo, String providerTradeNo, String payChannel);

    /**
     * 获取订单详情
     */
    MaterialOrderDTO getOrderByNo(String orderNo);

    /**
     * 我的购买订单
     */
    List<MaterialOrderDTO> getMyPurchaseOrders(Long userId, Integer pageNum, Integer pageSize);

    /**
     * 我的销售订单
     */
    List<MaterialOrderDTO> getMySalesOrders(Long userId, Integer pageNum, Integer pageSize);

    /**
     * 检查用户是否已购买资料
     */
    boolean hasPurchased(Long userId, Long materialId);
}
