package com.liang.bbs.user.facade.server;

import com.liang.bbs.common.enums.ResponseCode;
import com.liang.bbs.user.facade.dto.UserAccessLogMongoDTO;
// import com.liang.bbs.user.facade.vo.UserAccessLogVO;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * MongoDB用户访问日志服务接口
 * 提供基于MongoDB的用户访问日志记录和查询功能
 */
public interface UserAccessLogMongoService {

    /**
     * 记录用户访问行为
     * @param dto 访问日志DTO
     * @return 是否成功
     */
    ResponseCode recordAccess(UserAccessLogMongoDTO dto);

    /**
     * 批量记录用户访问行为
     * @param dtoList 访问日志DTO列表
     * @return 是否成功
     */
    ResponseCode batchRecordAccess(List<UserAccessLogMongoDTO> dtoList);

    /**
     * 获取用户的访问记录
     * @param userId 用户ID
     * @param targetType 目标类型（可选）
     * @param actionType 动作类型（可选）
     * @param startTime 开始时间（可选）
     * @param endTime 结束时间（可选）
     * @param limit 限制数量
     * @return 访问记录列表
     */
    List<UserAccessLogMongoDTO> getUserAccessLogs(Long userId, String targetType, String actionType, 
                                                  Date startTime, Date endTime, Integer limit);

    /**
     * 获取目标对象的访问记录
     * @param targetType 目标类型
     * @param targetId 目标ID
     * @param actionType 动作类型（可选）
     * @param startTime 开始时间（可选）
     * @param endTime 结束时间（可选）
     * @param limit 限制数量
     * @return 访问记录列表
     */
    List<UserAccessLogMongoDTO> getTargetAccessLogs(String targetType, Long targetId, String actionType,
                                                    Date startTime, Date endTime, Integer limit);

    /**
     * 获取访问统计数量
     * @param targetType 目标类型（可选）
     * @param targetId 目标ID（可选）
     * @param actionType 动作类型（可选）
     * @param startTime 开始时间（可选）
     * @param endTime 结束时间（可选）
     * @return 访问数量
     */
    long getAccessCount(String targetType, Long targetId, String actionType, 
                        Date startTime, Date endTime);

    /**
     * 获取访问统计分组数据
     * @param groupBy 分组字段（targetType, actionType, date等）
     * @param targetType 目标类型（可选）
     * @param startTime 开始时间（可选）
     * @param endTime 结束时间（可选）
     * @return 分组统计结果
     */
    List<Map<String, Object>> getAccessStatistics(String groupBy, String targetType,
                                                    Date startTime, Date endTime);

    /**
     * 获取热门目标排行
     * @param targetType 目标类型
     * @param actionType 动作类型
     * @param startTime 开始时间（可选）
     * @param endTime 结束时间（可选）
     * @param limit 限制数量
     * @return 热门目标列表
     */
    List<Map<String, Object>> getPopularTargets(String targetType, String actionType,
                                                Date startTime, Date endTime, Integer limit);

    /**
     * 获取用户活跃度统计
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @param limit 限制数量
     * @return 活跃用户列表
     */
    List<Map<String, Object>> getActiveUsers(Date startTime, Date endTime, Integer limit);

    /**
     * 删除指定用户的访问记录
     * @param userId 用户ID
     * @param targetType 目标类型（可选）
     * @param targetId 目标ID（可选）
     * @return 删除的记录数量
     */
    long deleteUserAccessLogs(Long userId, String targetType, Long targetId);

    /**
     * 获取实时访问趋势（按小时）
     * @param hours 过去多少小时
     * @return 每小时访问量
     */
    List<Map<String, Object>> getRealTimeTrend(int hours);

    /**
     * 获取用户行为路径分析
     * @param userId 用户ID
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 用户行为序列
     */
    List<UserAccessLogMongoDTO> getUserBehaviorPath(Long userId, Date startTime, Date endTime);

    /**
     * 获取目标对象的用户画像分析
     * @param targetType 目标类型
     * @param targetId 目标ID
     * @return 用户画像数据
     */
    Map<String, Object> getTargetUserPortrait(String targetType, Long targetId);
}