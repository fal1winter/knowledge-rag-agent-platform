package com.liang.bbs.user.facade.server;

import com.liang.bbs.user.facade.dto.lottery.*;

import java.util.List;

/**
 * 抽奖服务接口
 */
public interface LotteryService {

    /**
     * 获取用户抽奖次数
     */
    LotteryChanceDTO getUserChances(Integer userId);

    /**
     * 执行抽奖
     */
    LotteryDrawResultDTO draw(Integer userId);

    /**
     * 获取用户抽奖记录
     */
    List<LotteryRecordDTO> getUserRecords(Integer userId, Integer limit);

    /**
     * 获取最新抽奖记录（所有用户）
     */
    List<LotteryRecordDTO> getRecentRecords(Integer limit);

    /**
     * 获取奖品配置
     */
    List<PrizeConfigDTO> getPrizeConfig();

    /**
     * 管理员：分发抽奖次数给用户
     */
    void distributeChances(Integer userId, Integer chances);

    /**
     * 管理员：批量分发抽奖次数给多个用户
     */
    int batchDistributeChances(List<Integer> userIds, Integer chances);

    /**
     * 管理员：获取所有有抽奖记录的用户及其次数
     */
    List<UserChanceDTO> listAllUserChances();

    /**
     * 管理员：获取所有用户及其抽奖次数（含0次的用户）
     */
    List<UserChanceDTO> listAllUsersWithChances();
}
