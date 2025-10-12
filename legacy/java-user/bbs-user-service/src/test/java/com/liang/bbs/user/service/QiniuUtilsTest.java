package com.liang.bbs.user.service;

import org.junit.jupiter.api.Test;

import com.liang.bbs.user.service.utils.QiniuUtils;

import static org.junit.jupiter.api.Assertions.*;

public class QiniuUtilsTest {
    @Test
    public void testUploadBytes() {
        // 准备测试数据
        byte[] testData = "test content".getBytes();
        String testKey = "test.txt";
        
        // 调用被测方法
        String result = QiniuUtils.uploadBytes(testData, testKey);
        
        // 验证结果
        assertNotNull(result);
        assertTrue(result.startsWith("http")); // 假设返回的是URL
    }
}