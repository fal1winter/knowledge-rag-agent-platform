package com.liang.bbs.user.facade.dto;

import lombok.Data;

import java.util.Map;

/**
 * 通知上下文对象，用于传递通知相关参数
 *
 */
@Data
public class NotiContext {
    
    /**
     * 通知标题
     */
    private String title;
    
    /**
     * 通知内容
     */
    private String content;
    
    /**
     * 通知类型
     */
    private Integer type;
    
    /**
     * 发送者ID
     */
    private Integer senderId;
    
    /**
     * 接收者ID
     */
    private Integer receiverId;
    
    /**
     * 额外数据，JSON格式字符串
     */
    private String extra;
    
    /**
     * 方法参数
     */
    private Map<String, Object> methodArgs;
    
    /**
     * 方法返回值
     */
    private Object returnValue;
}