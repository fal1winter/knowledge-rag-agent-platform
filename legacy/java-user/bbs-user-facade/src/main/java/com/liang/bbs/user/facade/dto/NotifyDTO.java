package com.liang.bbs.user.facade.dto;

import lombok.Data;

import java.io.Serializable;

/**
 */
@Data
public class NotifyDTO implements Serializable {
    /**
     * 通知编号
     */
    private Integer id;

    /**
     * 项目id（南生论坛/南生笔记...）
     */
    private Integer projectId;

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

    private static final long serialVersionUID = 1L;

}
