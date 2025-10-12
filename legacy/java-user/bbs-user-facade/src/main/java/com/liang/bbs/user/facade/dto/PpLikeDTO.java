package com.liang.bbs.user.facade.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Date;

/**
 * 点赞数据传输对象
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PpLikeDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 点赞ID
     */
    private Integer id;

    /**
     * 用户ID
     */
    private Integer userId;

    /**
     * 目标ID（文章ID/评论ID等）
     */
    private Integer targetId;

    /**
     * 点赞状态：1-点赞，0-取消点赞
     */
    private String status;

    /**
     * 点赞时间
     */
    private Date time;

    /**
     * 点赞类型：article-文章，comment-评论
     */
    private String type;
}