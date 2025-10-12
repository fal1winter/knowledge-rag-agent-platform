package com.liang.bbs.user.service.example;

import com.liang.bbs.user.service.annotation.RecordAccess;
import org.springframework.stereotype.Service;

/**
 * 访问日志记录示例
 * 展示如何使用@RecordAccess注解快速记录访问行为
 */
@Service
public class AccessLogExample {

    /**
     * 示例1：记录文章浏览行为
     */
    @RecordAccess(
        targetType = "article", 
        targetId = "#articleId", 
        actionType = "view",
        userId = "#userId",
        extraInfo = "View Article"
    )
    public void viewArticle(Long userId, Long articleId) {
        // 业务逻辑：处理文章浏览
        System.out.println("用户 " + userId + " 浏览了文章 " + articleId);
    }

    /**
     * 示例2：记录点赞行为
     */
    @RecordAccess(
        targetType = "article", 
        targetId = "#articleId", 
        actionType = "like",
        userId = "#userId",
        extraInfo = "点赞文章"
    )
    public void likeArticle(Long userId, Long articleId) {
        // 业务逻辑：处理文章点赞
        System.out.println("用户 " + userId + " 点赞了文章 " + articleId);
    }

    /**
     * 示例3：记录评论行为
     */
    @RecordAccess(
        targetType = "comment", 
        targetId = "#commentId", 
        actionType = "create",
        userId = "#userId",
        extraInfo = "#content"
    )
    public void createComment(Long userId, Long commentId, String content) {
        // 业务逻辑：处理评论创建
        System.out.println("用户 " + userId + " 创建了评论 " + commentId + ": " + content);
    }

    /**
     * 示例4：记录收藏行为
     */
    @RecordAccess(
        targetType = "article", 
        targetId = "#articleId", 
        actionType = "favorite",
        userId = "#userId",
        extraInfo = "收藏文章"
    )
    public void favoriteArticle(Long userId, Long articleId) {
        // 业务逻辑：处理文章收藏
        System.out.println("用户 " + userId + " 收藏了文章 " + articleId);
    }

    /**
     * 示例5：记录搜索行为
     */
    @RecordAccess(
        targetType = "search", 
        targetId = "#0", // 使用默认值
        actionType = "search",
        userId = "#userId",
        extraInfo = "#keyword"
    )
    public void search(Long userId, String keyword) {
        // 业务逻辑：处理搜索
        System.out.println("用户 " + userId + " 搜索了: " + keyword);
    }
}