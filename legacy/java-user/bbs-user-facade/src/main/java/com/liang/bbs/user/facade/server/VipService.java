package com.liang.bbs.user.facade.server;

import com.bbs.common.response.PageResult;
import com.liang.bbs.user.facade.dto.vip.VipOrderDTO;
import com.liang.bbs.user.facade.dto.vip.VipPlanDTO;

import java.util.List;

/**
 * VIP服务接口
 */
public interface VipService {

    /**
     * 获取所有可用的VIP套餐
     *
     * @return 套餐列表
     */
    List<VipPlanDTO> listAvailablePlans();

    /**
     * 购买VIP
     *
     * @param userId 用户ID
     * @param planId 套餐ID
     * @return 订单信息
     */
    VipOrderDTO purchaseVip(Integer userId, Integer planId);

    /**
     * 获取用户VIP订单列表
     *
     * @param userId 用户ID
     * @param pageNum 页码
     * @param pageSize 每页数量
     * @return 订单列表
     */
    PageResult<VipOrderDTO> getUserOrders(Integer userId, Integer pageNum, Integer pageSize);

    /**
     * 判断用户是否为VIP
     *
     * @param userId 用户ID
     * @return 是否VIP
     */
    boolean isVip(Integer userId);

    /**
     * 获取用户VIP信息
     *
     * @param userId 用户ID
     * @return VIP过期时间，null表示不是VIP
     */
    java.time.LocalDateTime getVipExpireTime(Integer userId);
}
