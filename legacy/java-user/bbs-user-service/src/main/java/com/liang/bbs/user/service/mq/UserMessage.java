package com.liang.bbs.user.service.mq;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户消息实体
 * 
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserMessage implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * 用户ID
     */
    private Long userId;
    
    /**
     * 用户名
     */
    private String username;
    
    /**
     * 操作类型：register, login, update
     */
    private String action;
    
    /**
     * 消息内容
     */
    private String content;
    
    /**
     * 创建时间
     */
    private LocalDateTime createTime;
    
    /**
     * IP地址
     */
    private String ipAddress;
    
    /**
     * 用户代理
     */
    private String userAgent;
}