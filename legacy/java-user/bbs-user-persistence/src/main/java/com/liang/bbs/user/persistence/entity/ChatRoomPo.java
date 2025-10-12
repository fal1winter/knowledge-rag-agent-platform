package com.liang.bbs.user.persistence.entity;

import java.io.Serializable;
import java.util.Date;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ChatRoomPo implements Serializable {
    private Integer id;

    private String name;

    private String description;

    private Integer creatorId;

    private Integer maxMembers;

    private Integer isPrivate;

    private String password;

    private Date createdAt;

    private Date updatedAt;

    private Integer status;

    private String avatarUrl;

    private static final long serialVersionUID = 1L;
}