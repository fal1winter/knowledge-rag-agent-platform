package com.liang.bbs.user.facade.server;

import com.liang.bbs.user.facade.dto.LikeCommentDTO;
import com.liang.bbs.user.facade.dto.user.UserSsoDTO;

import java.time.LocalDateTime;
import java.util.List;

/**
 */
public interface LikeCommentService {

    /**
     * 获取所有通过审核文章的评论的点赞信息
     *
     * @param startTime
     * @param endTime
     * @return
     */
    List<LikeCommentDTO> getPaasAll(LocalDateTime startTime, LocalDateTime endTime);

    /**
     * 获取评论的点赞数量
     *
     * @param commentId
     * @return
     */
    Long getLikeCountCommentId(Integer commentId);

    /**
     * 是否点赞
     *
     * @param commentId
     * @param userId
     * @return
     */
    Boolean isLike(Integer commentId, Integer userid);

    /**
     * 更新点赞状态
     *
     * @param commentId
     * @param currentUser
     * @return
     */
    Boolean updateLikeCommentState(Integer commentId, UserSsoDTO currentUser);

    /**
     * 通过评论id和用户id获取点赞信息
     *
     * @param commentId
     * @param userId
     * @return
     */
    LikeCommentDTO getByCommentIdUserId(Integer commentId, Integer userid);

}
