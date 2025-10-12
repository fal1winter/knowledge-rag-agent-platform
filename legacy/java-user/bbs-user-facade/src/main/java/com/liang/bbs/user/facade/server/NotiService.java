package com.liang.bbs.user.facade.server;

import com.liang.bbs.user.facade.dto.NotiDTO;
import com.liang.bbs.user.facade.dto.NotiSearchDTO;
import com.liang.bbs.user.facade.dto.user.UserSsoDTO;
import com.github.pagehelper.PageInfo;

import java.util.List;

/**
 * 通知服务接口
 *
 */
public interface NotiService {

    /**
     * 创建通知
     *
     * @param notiDTO 通知信息
     * @param userId 当前登录用户ID
     * @return 是否创建成功
     */
    Boolean create(NotiDTO notiDTO, Integer userId);

    /**
     * 获取未读通知数量
     *
     * @param userId 用户ID
     * @return 未读通知数量
     */
    Long getNotReadNotiCount(Integer userId);

    /**
     * 标记通知为已读
     *
     * @param id 通知ID
     * @param userId 用户ID
     * @return 是否标记成功
     */
    Boolean haveRead(Integer id, Integer userId);

    /**
     * 批量标记通知为已读
     *
     * @param ids 通知ID列表
     * @param userId 用户ID
     * @return 是否标记成功
     */
    Boolean markRead(List<Integer> ids, Integer userId);

    /**
     * 获取通知列表
     *
     * @param notiSearchDTO 查询条件
     * @param pageNum 页码
     * @param pageSize 每页条数
     * @param userId 用户ID
     * @return 通知列表
     */
    PageInfo<NotiDTO> getList(NotiSearchDTO notiSearchDTO, Integer pageNum, Integer pageSize, Integer userId);

    /**
     * 根据用户ID和已读状态获取通知列表（分页）
     *
     * @param userId 用户ID
     * @param isRead 已读状态（0:未读, 1:已读）
     * @param pageNum 页码
     * @param pageSize 每页条数
     * @return 通知列表
     */
    PageInfo<NotiDTO> getNotiDetail(Integer userId, String isRead, Integer pageNum, Integer pageSize);
    List<NotiDTO> getNotiList(Integer userId);
    /**
     * 根据用户ID获取通知列表（分页）
     *
     * @param userId 用户ID
     * @param pageNum 页码
     * @param pageSize 每页条数
     * @return 通知列表
     */
    PageInfo<NotiDTO> getNotiById(Integer userId, Integer pageNum, Integer pageSize);
}