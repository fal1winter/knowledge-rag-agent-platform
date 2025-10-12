package com.liang.bbs.user.facade.dto.user;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 */
@Data
public class UserStateDTO implements Serializable {
    /**
     * 用户id
     */
    private List<Integer> ids;
    /**
     * 是启用还是禁用(0禁用,1启用)
     */
    private Boolean isEnableOrIsDisable;

    private static final long serialVersionUID = 1L;

}
