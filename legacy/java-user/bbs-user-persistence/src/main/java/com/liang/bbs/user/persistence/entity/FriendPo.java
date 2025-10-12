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
public class FriendPo implements Serializable {
    private Integer id;

    private Integer userId;

    private Integer friendId;

    private String remark;

    private Boolean status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private static final long serialVersionUID = 1L;
}