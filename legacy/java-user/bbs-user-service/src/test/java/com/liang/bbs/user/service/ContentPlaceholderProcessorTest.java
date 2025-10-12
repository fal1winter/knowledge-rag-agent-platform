package com.liang.bbs.user.service;

import com.liang.bbs.user.service.impl.ContentPlaceholderProcessor;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ContentPlaceholderProcessorTest {

    @Test
    void testProcessContent() {
        ContentPlaceholderProcessor processor = new ContentPlaceholderProcessor();
        
        // 测试旧的占位符格式
        String content1 = "!user32观看了!paper3";
        String result1 = processor.processContent(content1);
        
        assertNotNull(result1);
        System.out.println("旧格式处理结果: " + result1);
        
        // 测试新的数字占位符格式
        String content2 = "3@32观看了0@3";
        String result2 = processor.processContent(content2);
        
        assertNotNull(result2);
        System.out.println("新格式处理结果: " + result2);
        
        // 测试混合格式
        String content3 = "用户3@32对论文0@3进行了1@15的评分";
        String result3 = processor.processContent(content3);
        
        assertNotNull(result3);
        System.out.println("混合格式处理结果: " + result3);
    }
}