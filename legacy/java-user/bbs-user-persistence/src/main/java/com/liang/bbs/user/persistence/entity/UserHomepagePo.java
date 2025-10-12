package com.liang.bbs.user.persistence.entity;

import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Accessors(chain = true)
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserHomepagePo implements Serializable {
    private Long id;

    private Integer userId;

    private Byte status;

    private String type;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private String content;

    private String topic;

    private Integer likeCount;

    private Integer commentCount;

    private static final long serialVersionUID = 1L;
}