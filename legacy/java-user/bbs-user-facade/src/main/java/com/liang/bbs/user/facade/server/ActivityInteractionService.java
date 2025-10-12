package com.liang.bbs.user.facade.server;

import java.util.List;
import java.util.Map;

/**
 * 动态互动服务（点赞、评论）
 */
public interface ActivityInteractionService {

    /**
     * 点赞/取消点赞动态
     */
    Map<String, Object> likeActivity(Long activityId, Integer userId);

    /**
     * 获取点赞状态
     */
    Map<String, Object> getLikeStatus(Long activityId, Integer userId);

    /**
     * 批量获取点赞状态
     * @param activityIds 动态ID列表
     * @param userId 用户ID
     * @return Map<activityId, likeStatus>
     */
    Map<Long, Map<String, Object>> getBatchLikeStatus(List<Long> activityIds, Integer userId);

    /**
     * 添加评论
     */
    Map<String, Object> addComment(Long activityId, Integer userId, String content);

    /**
     * 添加评论（支持嵌套）
     * @param activityId 动态ID
     * @param userId 用户ID
     * @param content 评论内容
     * @param parentId 父评论ID，null表示顶级评论
     * @param replyToUserId 回复的目标用户ID
     */
    Map<String, Object> addComment(Long activityId, Integer userId, String content, Long parentId, Integer replyToUserId);

    /**
     * 获取评论列表（含用户头像昵称）
     */
    List<Map<String, Object>> getComments(Long activityId, Integer page, Integer pageSize);

    /**
     * 删除评论
     */
    Boolean deleteComment(Long commentId, Integer userId);
}
