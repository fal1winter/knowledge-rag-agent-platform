package com.liang.bbs.user.facade.server;

import com.github.pagehelper.PageInfo;
import com.liang.bbs.user.facade.dto.NotifyDTO;
import com.liang.bbs.user.facade.dto.NotifyOutDTO;
import com.liang.bbs.user.facade.dto.NotifySearchDTO;
import com.liang.bbs.user.facade.dto.user.UserSsoDTO;

import java.util.List;

/**
 */
public interface NotifyService {
    /**
     * 生成通知信息
     *
     * @param notifyDTO
     * @param needNotifyUserIds
     * @param currentUser
     * @return
     */
    Boolean create(NotifyDTO notifyDTO, List<Long> needNotifyUserIds, UserSsoDTO currentUser);

    /**
     * 获取某一用户未读通知数量
     *
     * @param userId
     * @param type
     * @return
     */
    Integer getNotReadNotifyCount(Long userId, Integer type);

    /**
     * 全部已读
     *
     * @param currentUser
     * @param type
     * @return
     */
    Boolean haveRead(UserSsoDTO currentUser, Integer type);

    /**
     * 标记已读
     *
     * @param notifyIds
     * @param currentUser
     * @return
     */
    Boolean markRead(List<Integer> notifyIds, UserSsoDTO currentUser);

    /**
     * 分页获取反馈信息
     *
     * @param notifySearchDTO
     * @param currentUser
     * @return
     */
    PageInfo<NotifyOutDTO> getList(NotifySearchDTO notifySearchDTO, UserSsoDTO currentUser);
}
