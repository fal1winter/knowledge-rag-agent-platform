package com.liang.bbs.user.facade.server;

import com.liang.bbs.user.facade.dto.PpLikeDTO;

import java.util.List;

/**
 * 点赞服务接口
 */
public interface PpLikeService {

    /**
     * 添加点赞
     * @param ppLikeDTO 点赞信息
     * @return 操作结果
     */
    Boolean addLike(PpLikeDTO ppLikeDTO);

    /**
     * 取消点赞
     * @param userId 用户ID
     * @param targetId 目标ID
     * @param type 点赞类型
     * @return 操作结果
     */
    Boolean cancelLike(Integer userId, Integer targetId, String type);

    /**
     * 检查用户是否点赞
     * @param userId 用户ID
     * @param targetId 目标ID
     * @param type 点赞类型
     * @return 点赞状态
     */
    Boolean checkLikeStatus(Integer userId, Integer targetId, String type);

    /**
     * 获取目标的点赞数
     * @param targetId 目标ID
     * @param type 点赞类型
     * @return 点赞数量
     */
    Long getLikeCount(Integer targetId, String type);

    /**
     * 获取用户的点赞列表
     * @param userId 用户ID
     * @param type 点赞类型
     * @return 点赞列表
     */
    List<PpLikeDTO> getUserLikes(Integer userId, String type);
}