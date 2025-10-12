package com.liang.bbs.user.service.impl;

import com.liang.bbs.user.facade.server.NotiContentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.apache.dubbo.config.annotation.Service;

/**
 * 通知内容服务实现
 * 专门处理通知内容的占位符替换、HTML标签清理和长度截断
 * 
 */
@Service
public class NotiContentServiceImpl implements NotiContentService {

    @Autowired
    private ContentPlaceholderProcessor contentProcessor;

    @Override
    public String processNotificationContent(String content) {
        if (content == null || content.trim().isEmpty()) {
            return "";
        }
        
        // 1. 清理HTML标签
        String cleanedContent = removeHtmlTags(content);
        
        // 2. 处理占位符
        String processedContent = contentProcessor.processContent(cleanedContent);
        
        // 3. 截断长度（限制为300个字符）
        return truncateContent(processedContent, 300);
    }

    @Override
    public String getProcessedContent(String originalContent) {
        if (originalContent == null) {
            return "";
        }
        
        return processNotificationContent(originalContent);
    }

    /**
     * 移除HTML标签
     * 
     * @param content 包含HTML的内容
     * @return 清理后的纯文本内容
     */
    private String removeHtmlTags(String content) {
        if (content == null || content.trim().isEmpty()) {
            return "";
        }
        
        // 移除HTML标签
        String cleaned = content.replaceAll("<[^>]*>", "");
        
        // 移除HTML实体
        cleaned = cleaned.replaceAll("&[^;]*;", "");
        
        // 移除多余的空白字符
        cleaned = cleaned.trim().replaceAll("\\s+", " ");
        
        return cleaned;
    }

    /**
     * 截断内容长度
     * 
     * @param content 原始内容
     * @param maxLength 最大长度
     * @return 截断后的内容
     */
    private String truncateContent(String content, int maxLength) {
        if (content == null || content.length() <= maxLength) {
            return content;
        }
        
        // 截断并在末尾添加省略号
        return content.substring(0, maxLength - 3) + "...";
    }
}