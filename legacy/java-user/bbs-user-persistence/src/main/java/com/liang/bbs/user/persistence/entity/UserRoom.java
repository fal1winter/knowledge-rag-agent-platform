package com.liang.bbs.user.persistence.entity;

import java.io.Serializable;
import java.util.Date;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserRoom implements Serializable {
    private Integer id;

    private Integer userId;

    private Integer chatroomId;

    private LocalDateTime joinTime;

    private String role;

    private Byte subscribe;

    private Byte exited;

    private static final long serialVersionUID = 1L;
}