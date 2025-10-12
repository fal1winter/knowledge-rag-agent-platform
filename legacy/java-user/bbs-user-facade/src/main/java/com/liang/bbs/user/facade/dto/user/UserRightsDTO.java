package com.liang.bbs.user.facade.dto.user;

import lombok.Data;
import com.liang.bbs.user.facade.dto.RoleSsoDTO;
import java.io.Serializable;
import java.util.List;

/**
 */
@Data
public class UserRightsDTO implements Serializable {
    /**
     * 用户id
     */
    private Integer userId;
    /**
     * 用户名
     */
    private String userName;
    /**
     * 角色列表
     */
    private List<RoleSsoDTO> roles;

    private static final long serialVersionUID = 1L;

}
