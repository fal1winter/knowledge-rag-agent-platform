package com.liang.bbs.user.service.impl;

import com.bbs.common.response.PageResult;
import com.liang.bbs.user.facade.dto.vip.VipOrderDTO;
import com.liang.bbs.user.facade.dto.vip.VipPlanDTO;
import com.liang.bbs.user.facade.server.UserCreditService;
import com.liang.bbs.user.facade.server.VipService;
import com.liang.bbs.user.persistence.entity.UserPo;
import com.liang.bbs.user.persistence.entity.VipOrderPo;
import com.liang.bbs.user.persistence.entity.VipPlanPo;
import com.liang.bbs.user.persistence.mapper.UserPoMapper;
import com.liang.bbs.user.persistence.mapper.VipOrderMapper;
import com.liang.bbs.user.persistence.mapper.VipPlanMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.Service;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * VIP服务实现
 */
@Slf4j
@Service
public class VipServiceImpl implements VipService {

    @Autowired
    private VipPlanMapper vipPlanMapper;

    @Autowired
    private VipOrderMapper vipOrderMapper;

    @Autowired
    private UserPoMapper userMapper;

    @Autowired
    private UserCreditService creditService;

    @Override
    public List<VipPlanDTO> listAvailablePlans() {
        try {
            List<VipPlanPo> poList = vipPlanMapper.selectAvailablePlans();
            List<VipPlanDTO> dtoList = new ArrayList<>();

            for (VipPlanPo po : poList) {
                VipPlanDTO dto = new VipPlanDTO();
                BeanUtils.copyProperties(po, dto);

                // 计算折扣
                if (po.getOriginalPrice() != null && po.getOriginalPrice().compareTo(BigDecimal.ZERO) > 0) {
                    int discount = po.getPrice()
                            .multiply(BigDecimal.valueOf(100))
                            .divide(po.getOriginalPrice(), 0, BigDecimal.ROUND_HALF_UP)
                            .intValue();
                    dto.setDiscount(discount);
                }

                dtoList.add(dto);
            }

            return dtoList;
        } catch (Exception e) {
            log.error("listAvailablePlans error", e);
            return new ArrayList<>();
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public VipOrderDTO purchaseVip(Integer userId, Integer planId) {
        if (userId == null || planId == null) {
            throw new RuntimeException("参数错误");
        }

        try {
            // 查询套餐
            VipPlanPo plan = vipPlanMapper.selectByPrimaryKey(planId);
            if (plan == null || plan.getStatus() != 1) {
                throw new RuntimeException("套餐不存在或已下架");
            }

            // 查询用户
            UserPo user = userMapper.selectByPrimaryKey(userId);
            if (user == null) {
                throw new RuntimeException("用户不存在");
            }

            // 扣减积分
            int priceInt = plan.getPrice().intValue();
            boolean deductSuccess = creditService.deductCredits(userId, priceInt, "PURCHASE_VIP", null);
            if (!deductSuccess) {
                throw new RuntimeException("积分不足");
            }

            // 计算过期时间
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime currentExpireTime = user.getVipExpireTime();
            LocalDateTime newExpireTime;

            if (currentExpireTime != null && currentExpireTime.isAfter(now)) {
                // 如果当前VIP未过期，在当前过期时间基础上延长
                newExpireTime = currentExpireTime.plusDays(plan.getDurationDays());
            } else {
                // 如果当前VIP已过期或没有VIP，从现在开始计算
                newExpireTime = now.plusDays(plan.getDurationDays());
            }

            // 更新用户VIP过期时间
            user.setVipExpireTime(newExpireTime);
            userMapper.updateByPrimaryKeySelective(user);

            // 创建订单
            VipOrderPo order = new VipOrderPo();
            order.setOrderNo(generateOrderNo());
            order.setUserId(userId);
            order.setPlanId(planId);
            order.setPlanName(plan.getName());
            order.setDurationDays(plan.getDurationDays());
            order.setPrice(plan.getPrice());
            order.setExpireTime(newExpireTime);
            order.setStatus(1);
            order.setCreateTime(now);
            vipOrderMapper.insert(order);

            // 转换DTO
            VipOrderDTO dto = new VipOrderDTO();
            BeanUtils.copyProperties(order, dto);

            log.info("purchaseVip success: userId={}, planId={}, expireTime={}", userId, planId, newExpireTime);
            return dto;
        } catch (Exception e) {
            log.error("purchaseVip error: userId={}, planId={}", userId, planId, e);
            throw new RuntimeException(e.getMessage());
        }
    }

    @Override
    public PageResult<VipOrderDTO> getUserOrders(Integer userId, Integer pageNum, Integer pageSize) {
        if (userId == null) {
            return new PageResult<>(new ArrayList<>(), 0L, pageNum, pageSize);
        }

        try {
            long total = vipOrderMapper.countByUserId(userId);
            if (total == 0) {
                return new PageResult<>(new ArrayList<>(), 0L, pageNum, pageSize);
            }

            int offset = (pageNum - 1) * pageSize;
            List<VipOrderPo> poList = vipOrderMapper.selectByUserId(userId, offset, pageSize);

            List<VipOrderDTO> dtoList = new ArrayList<>();
            for (VipOrderPo po : poList) {
                VipOrderDTO dto = new VipOrderDTO();
                BeanUtils.copyProperties(po, dto);
                dtoList.add(dto);
            }

            return new PageResult<>(dtoList, total, pageNum, pageSize);
        } catch (Exception e) {
            log.error("getUserOrders error: userId={}", userId, e);
            return new PageResult<>(new ArrayList<>(), 0L, pageNum, pageSize);
        }
    }

    @Override
    public boolean isVip(Integer userId) {
        if (userId == null) {
            return false;
        }

        try {
            UserPo user = userMapper.selectByPrimaryKey(userId);
            if (user == null || user.getVipExpireTime() == null) {
                return false;
            }

            return user.getVipExpireTime().isAfter(LocalDateTime.now());
        } catch (Exception e) {
            log.error("isVip error: userId={}", userId, e);
            return false;
        }
    }

    @Override
    public LocalDateTime getVipExpireTime(Integer userId) {
        if (userId == null) {
            return null;
        }

        try {
            UserPo user = userMapper.selectByPrimaryKey(userId);
            if (user == null) {
                return null;
            }

            return user.getVipExpireTime();
        } catch (Exception e) {
            log.error("getVipExpireTime error: userId={}", userId, e);
            return null;
        }
    }

    /**
     * 生成订单号
     */
    private String generateOrderNo() {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String uuid = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        return "VIP" + timestamp + uuid;
    }
}
