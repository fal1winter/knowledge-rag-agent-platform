package com.liang.bbs.user.facade.dto;

import com.liang.bbs.user.facade.dto.user.UserDTO;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 好友信息DTO
 */
@Data
@Accessors(chain = true)
public class FriendDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    private Integer id;

    /**
     * 用户ID
     */
    private Integer userId;

    /**
     * 好友ID
     */
    private Integer friendId;

    /**
     * 好友备注
     */
    private String remark;

    /**
     * 状态(0:已删除,1:正常,2:拉黑)
     */
    private Byte status;

    /**
     * 添加时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

    /**
     * 好友用户信息（用于前端显示）
     */
    private UserDTO friendUserInfo;

    // ===== 扁平化好友信息字段（绕过Dubbo嵌套对象序列化问题） =====

    /**
     * 好友用户名
     */
    private String friendName;

    /**
     * 好友头像
     */
    private String friendPicture;

    /**
     * 好友邮箱
     */
    private String friendEmail;

    /**
     * 好友职位
     */
    private String friendPosition;

    /**
     * 好友公司
     */
    private String friendCompany;

    /**
     * 好友简介
     */
    private String friendIntro;
}

