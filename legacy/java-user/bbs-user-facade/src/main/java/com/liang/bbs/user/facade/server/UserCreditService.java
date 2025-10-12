package com.liang.bbs.user.facade.server;

import com.bbs.common.response.PageResult;
import com.liang.bbs.user.facade.dto.user.UserCreditLogDTO;

/**
 * 用户积分服务接口
 */
public interface UserCreditService {

    /**
     * 增加积分
     *
     * @param userId 用户ID
     * @param amount 积分数量（正数）
     * @param type 类型
     * @param remark 备注
     * @param operatorId 操作人ID（管理员充值时使用）
     * @param relatedId 关联ID
     * @return 是否成功
     */
    boolean addCredits(Integer userId, Integer amount, String type, String remark, Integer operatorId, Long relatedId);

    /**
     * 扣减积分
     *
     * @param userId 用户ID
     * @param amount 积分数量（正数）
     * @param type 类型
     * @param relatedId 关联ID
     * @return 是否成功
     */
    boolean deductCredits(Integer userId, Integer amount, String type, Long relatedId);

    /**
     * 查询用户积分余额
     *
     * @param userId 用户ID
     * @return 积分余额
     */
    Integer getCredits(Integer userId);

    /**
     * 查询积分流水（分页）
     *
     * @param userId 用户ID
     * @param pageNum 页码
     * @param pageSize 每页数量
     * @return 积分流水列表
     */
    PageResult<UserCreditLogDTO> getCreditLogs(Integer userId, Integer pageNum, Integer pageSize);

    /**
     * 判断用户是否为VIP
     *
     * @param userId 用户ID
     * @return 是否VIP
     */
    boolean isVip(Integer userId);
}
