package com.liang.bbs.user.facade.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 */
@Data
public class NotifyOutDTO implements Serializable {
    /**
     * 通知编号
     */
    private Integer id;

    /**
     * 是否已读（0未读，1已读）
     */
    private Boolean isRead;

    /**
     * 项目id（南生论坛/南生笔记...）
     */
    private Integer projectId;

    /**
     * 系统名称
     */
    private String projectName;

    /**
     * 消息内容
     */
    private String message;

    /**
     * 消息类型（0任务提醒，1系统通知）
     */
    private Integer type;

    /**
     * 逻辑删除(0正常,1删除)
     */
    private Boolean isDeleted;

    /**
     * 创建用户id
     */
    private Long createUser;

    /**
     * 创建用户名称
     */
    private String createUserName;

    /**
     * 更新用户id
     */
    private Long updateUser;

    /**
     * 更新用户名称
     */
    private String updateUserName;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

    private static final long serialVersionUID = 1L;

}
