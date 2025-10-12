package com.liang.bbs.user.facade.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;
import java.util.List;

/**
 * 资源角色类型枚举
 */
@Getter
@AllArgsConstructor
public enum ResourceRoleEnum {
    OWNER("owner", "所有者", Arrays.asList(
            "view", "edit", "delete", "manage_admin", "transfer_owner",
            "invite", "kick", "mute", "pin_message", "manage_settings",
            "upload_version", "manage_comments", "set_visibility",
            "edit_profile", "manage_publications", "verify"
    )),
    ADMIN("admin", "管理员", Arrays.asList(
            "view", "edit", "manage_admin",
            "invite", "kick", "mute", "pin_message", "manage_settings",
            "upload_version", "manage_comments", "set_visibility",
            "edit_profile", "manage_publications"
    )),
    MEMBER("member", "成员", Arrays.asList(
            "view", "invite"
    ));

    private final String code;
    private final String description;
    private final List<String> defaultPermissions;

    public static ResourceRoleEnum fromCode(String code) {
        for (ResourceRoleEnum role : values()) {
            if (role.getCode().equals(code)) {
                return role;
            }
        }
        return null;
    }
}
