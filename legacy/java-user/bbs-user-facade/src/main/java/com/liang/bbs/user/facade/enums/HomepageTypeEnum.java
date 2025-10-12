package com.liang.bbs.user.facade.enums;

/**
 * 用户主页类型枚举
 */
public enum HomepageTypeEnum {
    mainpage("mainpage", "个人主页"),
    activity("activity", "动态");

    private final String code;
    private final String desc;

    HomepageTypeEnum(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public String getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }

    public static HomepageTypeEnum fromCode(String code) {
        for (HomepageTypeEnum type : values()) {
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
