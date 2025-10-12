package com.liang.bbs.user.persistence.entity;

import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserPo implements Serializable {
    private Integer id;

    private String name;

    private String password;

    private String salt;

    private Integer gender;

    private String birthday;

    private String phone;

    private String email;

    private String picture;

    private String position;

    private String company;

    private String homePage;

    private Integer orgId;

    private Boolean state;

    private String authId;

    private String authSource;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private String intro;

    private Integer credits;

    private LocalDateTime vipExpireTime;

    private static final long serialVersionUID = 1L;
}