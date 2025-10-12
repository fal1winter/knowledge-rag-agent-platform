package com.liang.bbs.user.facade.server;

import com.github.pagehelper.PageInfo;
import com.liang.bbs.user.facade.dto.user.UserForumDTO;
import com.liang.bbs.user.facade.dto.user.UserLevelDTO;
import com.liang.bbs.user.facade.dto.user.UserSearchDTO;
import com.liang.bbs.user.facade.dto.user.UserSsoDTO;

import java.util.List;

import org.springframework.cache.annotation.Cacheable;

/**
 */
public interface UserLevelService {
    /**
     * 创建用户等级信息
     *
     * @param userId
     * @return
     */
    Boolean create(Integer userid);

    /**
     * 更新用户等级信息
     *
     * @param userId 用户id
     * @param points 积分
     * @return
     */
    Boolean update(Integer userid,  Integer points);

    /**
     * 更新所有用户等级信息
     *
     * @return
     */
    Boolean updatePointsAll();

    /**
     * 同步所有用户等级信息
     *
     * @return
     */
    Boolean syncAll();

    /**
     * 获取热门作者列表
     *
     * @param userSearchDTO
     * @param currentUser
     * @return
     */
    @Cacheable( value = "userCache",key = "#currentUser.getUserId()")
    PageInfo<UserForumDTO> getHotAuthorsList(UserSearchDTO userSearchDTO, UserSsoDTO currentUser);

    /**
     * 通过用户id获取用户等级信息
     *
     * @param userId
     * @return
     */
    List<UserLevelDTO> getByUserId(Integer userid);

    /**
     * 通过用户id集合获取用户等级信息
     *
     * @param userIds
     * @return
     */
    List<UserLevelDTO> getByUserIds(List<Integer> userIds);

    /**
     * 获取用户信息
     *
     * @param userId
     * @param currentUser
     * @return
     */
    UserForumDTO getUserInfo(Integer userid, UserSsoDTO currentUser);
}
