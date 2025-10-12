package com.liang.bbs.user.facade.enums;

/**
 * 聊天室关联实体类型枚举
 */
public enum EntityTypeEnum {
    user("user", "用户"),
    paper("paper", "论文"),
    institution("institution", "机构"),
    scholar("scholar", "学者"),
    rating("rating", "评价");

    private final String code;
    private final String desc;

    EntityTypeEnum(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public String getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }

    public static EntityTypeEnum fromCode(String code) {
        for (EntityTypeEnum type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        return null;
    }

    public static boolean isValid(String code) {
        return fromCode(code) != null;
    }
}
