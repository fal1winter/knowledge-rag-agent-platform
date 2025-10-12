package com.liang.bbs.user.facade.dto;

import lombok.Data;

import java.io.Serializable;

/**
 */
@Data
public class NotifySearchDTO implements Serializable {
    /**
     * 反馈编号
     */
    private Integer id;

    /**
     * 项目id（南生论坛/南生笔记...）
     */
    private Integer projectId;

    /**
     * 消息类型（0任务提醒，1系统通知）
     */
    private Integer type;

    /**
     * 是否已读（0未读，1已读）
     */
    private Boolean isRead;

    /**
     * 当前页
     */
    private Integer current;

    /**
     * 每页条数
     */
    private Integer size;

    private static final long serialVersionUID = 1L;
}
