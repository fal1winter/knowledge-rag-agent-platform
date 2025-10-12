package com.liang.bbs.user.facade.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.io.Serializable;
import java.util.Date;
import java.util.Map;

/**
 * MongoDB用户访问日志DTO
 * 对应MongoDB中的user_access_logs集合
 */
@Data
@Document(collection = "user_access_logs")
public class UserAccessLogMongoDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    private String id;

    /**
     * 用户ID
     */
    @Field("userId")
    private Long userId;

    /**
     * 目标类型：scholar, paper, room, article, user
     */
    @Field("targetType")
    private String targetType;

    /**
     * 目标ID
     */
    @Field("targetId")
    private Long targetId;

    /**
     * 动作类型：view, click, download, like, share, comment
     */
    @Field("actionType")
    private String actionType;

    /**
     * 访问时间
     */
    @Field("timestamp")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date timestamp;

    /**
     * 元数据：包含IP、用户代理等信息
     */
    @Field("metadata")
    private Metadata metadata;

    /**
     * 附加数据：根据动作类型存储不同的扩展信息
     */
    @Field("additionalData")
    private Map<String, Object> additionalData;

    /**
     * 元数据内部类
     */
    @Data
    public static class Metadata implements Serializable {
        private static final long serialVersionUID = 1L;

        private String ip;
        private String userAgent;
        private String referer;
        private String device;
        private String browser;
        private String os;
        private String location; // 地理位置
    }

    /**
     * 创建时间（MongoDB自动维护）
     */
    @Field("createdAt")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createdAt;

    /**
     * 更新时间（MongoDB自动维护）
     */
    @Field("updatedAt")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date updatedAt;
}