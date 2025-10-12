package com.liang.bbs.user.facade.server;

/**
 * 通知内容服务接口
 * 专门处理通知内容的占位符替换、HTML标签清理和长度截断
 * 
 */
public interface NotiContentService {

    /**
     * 处理通知内容中的占位符、清理HTML标签并截断长度
     * 
     * @param content 原始通知内容
     * @return 处理后的通知内容
     */
    String processNotificationContent(String content);

    /**
     * 示例方法：处理通知内容
     * 将"!user32观看了!paper3"替换为"刘观看了机器学习"
     * 
     * @param originalContent 原始内容
     * @return 处理后的内容
     */
    String getProcessedContent(String originalContent);
}