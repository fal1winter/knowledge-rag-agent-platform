package com.liang.bbs.user.facade.enums;

/**
 * 数据权限范围枚举
 */
public enum DataScopeEnum {
    ALL(1, "全部数据"),
    INSTITUTION(2, "本机构数据"),
    INSTITUTION_AND_BELOW(3, "本机构及下级数据"),
    SELF(4, "仅本人数据");

    private final int code;
    private final String desc;

    DataScopeEnum(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public int getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }

    public static DataScopeEnum fromCode(int code) {
        for (DataScopeEnum scope : values()) {
            if (scope.code == code) {
                return scope;
            }
        }
        return SELF;
    }
}
