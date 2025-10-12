package com.liang.bbs.user.service.impl;

import com.bbs.common.response.PageResult;
import com.liang.bbs.user.facade.dto.user.UserCreditLogDTO;
import com.liang.bbs.user.facade.dto.user.UserDTO;
import com.liang.bbs.user.facade.server.UserCreditService;
import com.liang.bbs.user.facade.server.UserService;
import com.liang.bbs.user.persistence.entity.UserCreditLogPo;
import com.liang.bbs.user.persistence.entity.UserPo;
import com.liang.bbs.user.persistence.mapper.UserCreditLogMapper;
import com.liang.bbs.user.persistence.mapper.UserPoMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.Service;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 用户积分服务实现
 */
@Slf4j
@Service
public class UserCreditServiceImpl implements UserCreditService {

    @Autowired
    private UserCreditLogMapper creditLogMapper;

    @Autowired
    private UserPoMapper userMapper;

    @Autowired
    private UserService userService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean addCredits(Integer userId, Integer amount, String type, String remark, Integer operatorId, Long relatedId) {
        if (userId == null || amount == null || amount <= 0) {
            log.warn("addCredits failed: invalid params, userId={}, amount={}", userId, amount);
            return false;
        }

        try {
            // 查询用户当前积分
            UserPo user = userMapper.selectByPrimaryKey(userId);
            if (user == null) {
                log.warn("addCredits failed: user not found, userId={}", userId);
                return false;
            }

            Integer currentCredits = user.getCredits() != null ? user.getCredits() : 0;
            Integer newCredits = currentCredits + amount;

            // 更新用户积分
            user.setCredits(newCredits);
            userMapper.updateByPrimaryKeySelective(user);

            // 插入积分流水
            UserCreditLogPo logPo = new UserCreditLogPo();
            logPo.setUserId(userId);
            logPo.setAmount(amount);
            logPo.setBalance(newCredits);
            logPo.setType(type);
            logPo.setRemark(remark);
            logPo.setOperatorId(operatorId);
            logPo.setRelatedId(relatedId);
            logPo.setCreateTime(LocalDateTime.now());
            creditLogMapper.insert(logPo);

            log.info("addCredits success: userId={}, amount={}, newBalance={}, type={}", userId, amount, newCredits, type);
            return true;
        } catch (Exception e) {
            log.error("addCredits error: userId={}, amount={}", userId, amount, e);
            throw e;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deductCredits(Integer userId, Integer amount, String type, Long relatedId) {
        if (userId == null || amount == null || amount <= 0) {
            log.warn("deductCredits failed: invalid params, userId={}, amount={}", userId, amount);
            return false;
        }

        try {
            // 查询用户当前积分
            UserPo user = userMapper.selectByPrimaryKey(userId);
            if (user == null) {
                log.warn("deductCredits failed: user not found, userId={}", userId);
                return false;
            }

            Integer currentCredits = user.getCredits() != null ? user.getCredits() : 0;
            if (currentCredits < amount) {
                log.warn("deductCredits failed: insufficient credits, userId={}, current={}, required={}",
                        userId, currentCredits, amount);
                return false;
            }

            Integer newCredits = currentCredits - amount;

            // 更新用户积分
            user.setCredits(newCredits);
            userMapper.updateByPrimaryKeySelective(user);

            // 插入积分流水（负数）
            UserCreditLogPo logPo = new UserCreditLogPo();
            logPo.setUserId(userId);
            logPo.setAmount(-amount);
            logPo.setBalance(newCredits);
            logPo.setType(type);
            logPo.setRelatedId(relatedId);
            logPo.setCreateTime(LocalDateTime.now());
            creditLogMapper.insert(logPo);

            log.info("deductCredits success: userId={}, amount={}, newBalance={}, type={}", userId, amount, newCredits, type);
            return true;
        } catch (Exception e) {
            log.error("deductCredits error: userId={}, amount={}", userId, amount, e);
            throw e;
        }
    }

    @Override
    public Integer getCredits(Integer userId) {
        if (userId == null) {
            return 0;
        }

        try {
            UserPo user = userMapper.selectByPrimaryKey(userId);
            if (user == null) {
                return 0;
            }
            return user.getCredits() != null ? user.getCredits() : 0;
        } catch (Exception e) {
            log.error("getCredits error: userId={}", userId, e);
            return 0;
        }
    }

    @Override
    public PageResult<UserCreditLogDTO> getCreditLogs(Integer userId, Integer pageNum, Integer pageSize) {
        if (userId == null) {
            return new PageResult<>(new ArrayList<>(), 0L, pageNum, pageSize);
        }

        try {
            // 查询总数
            long total = creditLogMapper.countByUserId(userId);
            if (total == 0) {
                return new PageResult<>(new ArrayList<>(), 0L, pageNum, pageSize);
            }

            // 查询列表
            int offset = (pageNum - 1) * pageSize;
            List<UserCreditLogPo> poList = creditLogMapper.selectByUserId(userId, offset, pageSize);

            // 转换DTO
            List<UserCreditLogDTO> dtoList = new ArrayList<>();
            for (UserCreditLogPo po : poList) {
                UserCreditLogDTO dto = new UserCreditLogDTO();
                BeanUtils.copyProperties(po, dto);

                // 如果有操作人ID，查询操作人名称
                if (po.getOperatorId() != null) {
                    try {
                        UserDTO operator = userService.getById(po.getOperatorId());
                        if (operator != null) {
                            dto.setOperatorName(operator.getName());
                        }
                    } catch (Exception e) {
                        log.warn("get operator name failed: operatorId={}", po.getOperatorId(), e);
                    }
                }

                dtoList.add(dto);
            }

            return new PageResult<>(dtoList, total, pageNum, pageSize);
        } catch (Exception e) {
            log.error("getCreditLogs error: userId={}", userId, e);
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

            // 判断VIP是否过期
            return user.getVipExpireTime().isAfter(LocalDateTime.now());
        } catch (Exception e) {
            log.error("isVip error: userId={}", userId, e);
            return false;
        }
    }
}
