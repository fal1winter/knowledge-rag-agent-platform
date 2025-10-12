package com.liang.bbs.user.facade.dto;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 */
@Data
@Document("userchat")
public class UserChatDTO implements Serializable {
    private static final long serialVersionUID = 1L;
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
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss",timezone = "GMT+8")
    private LocalDateTime time;
}
