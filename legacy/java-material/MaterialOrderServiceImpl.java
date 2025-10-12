package com.liang.bbs.material.service.impl;

import com.liang.bbs.material.facade.dto.MaterialOrderDTO;
import com.liang.bbs.material.facade.dto.MaterialPurchaseRequest;
import com.liang.bbs.material.facade.enums.OrderEvent;
import com.liang.bbs.material.facade.enums.OrderState;
import com.liang.bbs.material.facade.service.MaterialOrderService;
import com.liang.bbs.material.persistence.entity.Material;
import com.liang.bbs.material.persistence.entity.MaterialAccess;
import com.liang.bbs.material.persistence.entity.MaterialOrder;
import com.liang.bbs.material.persistence.mapper.MaterialAccessMapper;
import com.liang.bbs.material.persistence.mapper.MaterialMapper;
import com.liang.bbs.material.persistence.mapper.MaterialOrderMapper;
import com.liang.bbs.material.service.statemachine.MaterialOrderStateMachineService;
import com.liang.bbs.user.facade.server.UserCreditService;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.Reference;
import org.apache.dubbo.config.annotation.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * 资料订单服务实现
 *
 */
@Slf4j
@Service
public class MaterialOrderServiceImpl implements MaterialOrderService {

    @Autowired
    private MaterialOrderMapper orderMapper;

    @Autowired
    private MaterialOrderStateMachineService stateMachineService;

    @Autowired
    private MaterialMapper materialMapper;

    @Autowired
    private MaterialAccessMapper accessMapper;

    @Reference
    private UserCreditService userCreditService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MaterialOrderDTO createOrder(MaterialPurchaseRequest request, Long buyerId) {
        Material material = materialMapper.selectByPrimaryKey(request.getMaterialId());
        if (material == null) {
            throw new RuntimeException("资料不存在");
        }

        // 检查是否已购买
        int count = orderMapper.countByMaterialIdAndBuyerId(
            request.getMaterialId(), buyerId, 1);
        if (count > 0) {
            throw new RuntimeException("您已购买过该资料");
        }

        // 创建订单
        MaterialOrder order = new MaterialOrder();
        order.setOrderNo(generateOrderNo());
        order.setMaterialId(material.getId());
        order.setBuyerId(buyerId);
        order.setSellerId(material.getSellerId());
        order.setPrice(material.getPrice());
        order.setPayType(request.getPayType());
        order.setStatus(OrderState.CREATED.getCode()); // 待支付

        orderMapper.insert(order);

        return convertToDTO(order);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void payOrder(String orderNo, Long userId) {
        MaterialOrder order = orderMapper.selectByOrderNo(orderNo);
        if (order == null) {
            throw new RuntimeException("订单不存在");
        }

        if (!order.getBuyerId().equals(userId)) {
            throw new RuntimeException("无权操作");
        }

        if (order.getStatus() != OrderState.CREATED.getCode()) {
            throw new RuntimeException("订单状态异常");
        }

        // 扣减积分
        Integer price = order.getPrice();
        boolean deductSuccess = userCreditService.deductCredits(
            userId.intValue(),
            price,
            "MATERIAL_PURCHASE",
            order.getMaterialId()
        );

        if (!deductSuccess) {
            throw new RuntimeException("积分不足或扣减失败");
        }

        // 通过状态机执行支付事件
        OrderState newState = stateMachineService.sendEvent(
            orderNo,
            OrderEvent.PAY,
            userId,
            1, // 操作人类型：1-买家
            "用户支付"
        );

        if (newState == null) {
            throw new RuntimeException("状态转换失败，订单可能已被其他操作改变");
        }

        // 补充支付时间
        MaterialOrder updateOrder = new MaterialOrder();
        updateOrder.setId(order.getId());
        updateOrder.setPayTime(new Date());
        orderMapper.updateByPrimaryKey(updateOrder);

        // 增加销量
        materialMapper.incrementSalesCount(order.getMaterialId());

        // 添加访问权限
        MaterialAccess access = new MaterialAccess();
        access.setUserId(userId);
        access.setMaterialId(order.getMaterialId());
        access.setAccessType(1);
        access.setOrderId(order.getId());
        accessMapper.insert(access);

        log.info("用户 {} 购买资料 {} 成功，扣减积分 {}", userId, order.getMaterialId(), price);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirmPaidByGateway(String orderNo, Long userId, String paymentOrderNo, String providerTradeNo, String payChannel) {
        MaterialOrder order = orderMapper.selectByOrderNo(orderNo);
        if (order == null) {
            throw new RuntimeException("订单不存在");
        }

        if (!order.getBuyerId().equals(userId)) {
            throw new RuntimeException("订单用户不匹配");
        }

        if (order.getStatus() == OrderState.PAID.getCode()) {
            return;
        }

        if (order.getStatus() != OrderState.CREATED.getCode()) {
            throw new RuntimeException("订单状态异常");
        }

        OrderState newState = stateMachineService.sendEvent(
            orderNo,
            OrderEvent.PAY,
            userId,
            2,
            "支付网关确认: " + payChannel
        );

        if (newState == null) {
            throw new RuntimeException("状态转换失败，订单可能已被其他操作改变");
        }

        MaterialOrder updateOrder = new MaterialOrder();
        updateOrder.setId(order.getId());
        updateOrder.setPayTime(new Date());
        orderMapper.updateByPrimaryKey(updateOrder);

        materialMapper.incrementSalesCount(order.getMaterialId());

        MaterialAccess access = new MaterialAccess();
        access.setUserId(userId);
        access.setMaterialId(order.getMaterialId());
        access.setAccessType(1);
        access.setOrderId(order.getId());
        accessMapper.insert(access);

        log.info("支付网关确认资料订单成功, orderNo={}, paymentOrderNo={}, providerTradeNo={}",
            orderNo, paymentOrderNo, providerTradeNo);
    }

    @Override
    public void cancelOrder(String orderNo, Long userId) {
        MaterialOrder order = orderMapper.selectByOrderNo(orderNo);
        if (order == null) {
            throw new RuntimeException("订单不存在");
        }

        if (!order.getBuyerId().equals(userId)) {
            throw new RuntimeException("无权操作");
        }

        // 通过状态机执行取消事件
        OrderState newState = stateMachineService.sendEvent(
            orderNo,
            OrderEvent.CANCEL,
            userId,
            1, // 操作人类型：1-买家
            "用户取消订单"
        );

        if (newState == null) {
            throw new RuntimeException("状态转换失败，订单可能已被其他操作改变");
        }
    }

    @Override
    public MaterialOrderDTO getOrderByNo(String orderNo) {
        MaterialOrder order = orderMapper.selectByOrderNo(orderNo);
        return convertToDTO(order);
    }

    @Override
    public List<MaterialOrderDTO> getMyPurchaseOrders(Long userId, Integer pageNum, Integer pageSize) {
        try {
            List<MaterialOrder> orders = orderMapper.selectByBuyerId(userId);
            return toPagedDTOList(orders, pageNum, pageSize, "purchase", userId);
        } catch (Throwable t) {
            // Dubbo provider 侧兜底，避免单次异常导致消费者直接收到 RPC_EXCEPTION(-3)
            log.error("查询用户 {} 购买订单失败，返回空列表兜底", userId, t);
            return Collections.emptyList();
        }
    }

    @Override
    public List<MaterialOrderDTO> getMySalesOrders(Long userId, Integer pageNum, Integer pageSize) {
        try {
            List<MaterialOrder> orders = orderMapper.selectBySellerId(userId);
            return toPagedDTOList(orders, pageNum, pageSize, "sales", userId);
        } catch (Throwable t) {
            log.error("查询用户 {} 销售订单失败，返回空列表兜底", userId, t);
            return Collections.emptyList();
        }
    }

    @Override
    public boolean hasPurchased(Long userId, Long materialId) {
        int count = orderMapper.countByMaterialIdAndBuyerId(materialId, userId, 1);
        return count > 0;
    }

    private String generateOrderNo() {
        return "MO" + System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 8);
    }

    private MaterialOrderDTO convertToDTO(MaterialOrder order) {
        if (order == null) {
            return null;
        }

        MaterialOrderDTO dto = new MaterialOrderDTO();
        dto.setId(order.getId());
        dto.setOrderNo(order.getOrderNo());
        dto.setMaterialId(order.getMaterialId());
        dto.setBuyerId(order.getBuyerId());
        dto.setSellerId(order.getSellerId());
        dto.setPrice(order.getPrice());
        dto.setPayType(order.getPayType());
        dto.setStatus(order.getStatus());
        dto.setPayTime(order.getPayTime());
        dto.setCreateTime(order.getCreateTime());
        dto.setUpdateTime(order.getUpdateTime());

        if (order.getMaterialId() != null) {
            try {
                Material material = materialMapper.selectByPrimaryKey(order.getMaterialId());
                if (material != null) {
                    dto.setMaterialTitle(material.getTitle());
                    dto.setMaterialCoverUrl(material.getCoverUrl());
                    dto.setMaterialDescription(material.getDescription());
                    dto.setFileType(material.getFileType());
                }
            } catch (Throwable t) {
                log.warn("订单 {} 读取资料信息失败，materialId={}", order.getId(), order.getMaterialId(), t);
            }
        }

        return dto;
    }

    private List<MaterialOrderDTO> toPagedDTOList(List<MaterialOrder> orders, Integer pageNum, Integer pageSize,
                                                   String scene, Long userId) {
        if (orders == null || orders.isEmpty()) {
            return Collections.emptyList();
        }

        int safePageNum = (pageNum == null || pageNum < 1) ? 1 : pageNum;
        int safePageSize = (pageSize == null || pageSize < 1) ? 10 : pageSize;
        int fromIndex = (safePageNum - 1) * safePageSize;
        if (fromIndex >= orders.size()) {
            return Collections.emptyList();
        }
        int toIndex = Math.min(fromIndex + safePageSize, orders.size());

        List<MaterialOrderDTO> result = new ArrayList<>();
        for (MaterialOrder order : orders.subList(fromIndex, toIndex)) {
            try {
                MaterialOrderDTO dto = convertToDTO(order);
                if (dto != null) {
                    result.add(dto);
                }
            } catch (Throwable t) {
                Long orderId = order == null ? null : order.getId();
                log.error("用户 {} 的 {} 订单转换失败，orderId={}", userId, scene, orderId, t);
            }
        }
        return result;
    }
}
