package com.liang.bbs.user.facade.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 资源类型枚举
 */
@Getter
@AllArgsConstructor
public enum ResourceTypeEnum {
    CHATROOM("chatroom", "聊天室"),
    PAPER("paper", "论文"),
    SCHOLAR("scholar", "学者");

    private final String code;
    private final String description;

    public static ResourceTypeEnum fromCode(String code) {
        for (ResourceTypeEnum type : values()) {
            if (type.getCode().equals(code)) {
                return type;
            }
        }
        return null;
    }
}
