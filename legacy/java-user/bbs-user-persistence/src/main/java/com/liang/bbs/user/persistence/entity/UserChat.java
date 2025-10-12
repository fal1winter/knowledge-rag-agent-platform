package com.liang.bbs.user.persistence.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 */
@Data
@Document("userchat")
public class UserChat {
    @Id
    private String id;

    

    /**
     * 文章内容markdown
     */
    private String content;

    /**
     * 文章类型type
     */
    private String type;

    /**
     * 用户id
     */
    private Integer userid;
private Integer roomid;
    /**
     * 时间（创建/更新）
     */
    private LocalDateTime time;
}
