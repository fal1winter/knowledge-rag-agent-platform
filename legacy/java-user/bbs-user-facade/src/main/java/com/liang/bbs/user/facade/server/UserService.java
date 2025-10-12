package com.liang.bbs.user.facade.server;

import com.github.pagehelper.PageInfo;
import com.liang.bbs.user.facade.dto.*;
import com.liang.bbs.user.facade.dto.user.UserDTO;
import com.liang.bbs.user.facade.dto.user.UserLoginDTO;
import com.liang.bbs.user.facade.dto.user.UserRoleOrgDTO;
import com.liang.bbs.user.facade.dto.user.UserSearchDTO;
import com.liang.bbs.user.facade.dto.user.UserSsoDTO;
import com.liang.bbs.user.facade.dto.user.*;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 */

public interface UserService {
    /**
     * 用户登录
     *
     * @param userLoginDTO
     * @return
     */
    UserTokenDTO login(UserLoginDTO userLoginDTO);

    /**
     * 用户注册
     *
     * @param userDTO
     * @return
     */
    UserTokenDTO register(UserDTO userDTO);

    /**
     * 用户注册（三方登录）
     *
     * @param userDTO
     * @return
     */
    UserTokenDTO registerAuth(UserDTO userDTO);

    /**
     * token已过期
     *
     * @param token
     * @return
     */
    Boolean isExpired(String token);

    /**
     * 生成UserToken
     *
     * @param userid
     * @param username
     * @return
     */
    UserTokenDTO generateUserToken(Integer userid, String username);

    List<UserDTO> SearchFuzz(UserDTO userDTO);
    /**
     * 通过token获取UserSsoDTO
     *
     * @param token
     * @return
     */
    UserSsoDTO getUserSsoDTOByToken(String token);

    /**
     * 校验Token是否正确
     *
     * @param token
     * @return
     */
    Boolean verifyToken(String token);

    /**
     * 获取token中的信息
     *
     * @param token
     * @return
     */
    Integer getClaim(String token);

    /**
     * 构造登录网址
     *
     * @param referer
     * @return
     */
    String innerLoginUrl(String referer);

    /**
     * 分页获取用户列表
     *
     * @param userSearchDTO
     * @return
     */
    PageInfo<UserDTO> getList(UserSearchDTO userSearchDTO);

    /**
     * 通过id集合获取用户
     *
     * @param ids
     * @return
     */
    List<UserDTO> getbyIds(List<Integer> ids);
    /**
     * 获取所有的用户信息
     *
     * @return
     */
    List<UserListDTO> getAllList();

    /**
     * 用户登出
     *
     * @param token
     */
    void logout(String token);

    /**
     * 获取所有性别
     *
     * @return
     */
    Map<Integer, String> getGender();

    /**
     * 通过id获取用户
     *
     * @param userid
     * @return
     */
    UserDTO getById(Integer userid);

    /**
     * 通过id获取用户（不包含敏感信息：密码、盐值等）
     * 适用于公开展示的场景
     *
     * @param userid
     * @return
     */
    UserDTO getByIdWithoutSensitive(Integer userid);

    /**
     * 通过id集合获取用户
     *
     * @param userIds
     * @return
     */
    List<UserDTO> getByIds(List<Integer> userIds);

    /**
     * 通过角色id获取用户
     *
     * @param roleId
     * @return
     */
    List<UserDTO> getListByRoleId(Integer roleId);

    /**
     * 不是该角色的用户
     *
     * @param roleIds
     * @return
     */
    List<UserDTO> notUserOfThisRole(List<Integer> roleIds);

    /**
     * 更新用户角色/组织架构
     *
     * @param userRoleOrgDTO
     * @param currentUser
     * @return
     */
    Boolean updateUserRolesOrg(UserRoleOrgDTO userRoleOrgDTO, UserSsoDTO currentUser);

    /**
     * 变更用户状态
     *
     * @param userStateDTO
     * @return
     */
    Boolean changeStatus(UserStateDTO userStateDTO);

    /**
     * 上传用户头像（更新）
     *
     * @param bytes
     * @param sourceFileName
     * @param currentUser
     * @return
     */
    Boolean uploadUserPicture(byte[] bytes, String sourceFileName, UserSsoDTO currentUser);

    /**
     * 获取用户详情
     *
     * @param userid
     * @return
     */
    UserListDTO getUserListById(Integer userid);

    /**
     * 更新用户基本信息
     *
     * @param userDTO
     * @param currentUser
     * @return
     */
    Boolean updateUserBasicInfo(UserDTO userDTO, UserSsoDTO currentUser);

    /**
     * 发送邮件验证码
     *
     * @param email
     * @param currentUser
     * @return
     */
    Boolean sendEmailVerifyCode(String email, UserSsoDTO currentUser);

    /**
     * 发送短信验证码
     *
     * @param phone
     * @param currentUser
     * @return
     * @throws Exception
     */
    Boolean sendSmsVerifyCode(String phone, UserSsoDTO currentUser);

    /**
     * 绑定邮箱
     *
     * @param userEmailDTO
     * @param currentUser
     * @return
     */
    Boolean bindEmail(UserEmailDTO userEmailDTO, UserSsoDTO currentUser);

    /**
     * 绑定手机
     *
     * @param userEmailDTO
     * @param currentUser
     * @return
     */
    Boolean bindPhone(UserEmailDTO userEmailDTO, UserSsoDTO currentUser);

    /**
     * 解绑邮箱
     *
     * @param currentUser
     * @return
     */
    Boolean untieEmail(UserSsoDTO currentUser);

    /**
     * 解绑手机
     *
     * @param currentUser
     * @return
     */
    Boolean untiePhone(UserSsoDTO currentUser);

    /**
     * 更新密码
     *
     * @param passwordDTO
     * @param currentUser
     * @return
     */
    Boolean updatePassword(UserPasswordDTO passwordDTO, UserSsoDTO currentUser);

    /**
     * 邮箱判重
     *
     * @param email
     * @return
     */
    Boolean isValidEmail(String email);

    /**
     * 手机判重
     *
     * @param phone
     * @return
     */
    Boolean isValidPhone(String phone);

    /**
     * 获取新用户
     *
     * @return
     */
    List<UserListDTO> getNewUser();

    /**
     * 上传图片（通用）
     *
     * @param bytes
     * @param sourceFileName
     * @param currentUser
     * @return
     */
    Map<String, Object> uploadCommonPicture(byte[] bytes, String sourceFileName, UserSsoDTO currentUser);

    /**
     * 更新所有用户的基础角色
     */
    void updateAllUserBaseRole();

    /**
     * 用户判重
     *
     * @param userName
     * @param currentUser
     * @return
     */
    Boolean isValidUser(String userName, UserSsoDTO currentUser);

    /**
     * 插入群组关联信息（闲聊）
     *
     * @param userName
     */
    void insertChatUser(String userName);

    /**
     * 插入群组关联信息（闲聊）
     *
     * @param id
     */
    void insertGroup(String id);

    /**
     * 判断手机是否已经绑定
     *
     * @param phone
     * @return
     */
    Boolean isPhoneExist(String phone);

    /**
     * 判断email是否已经绑定
     *
     * @param email
     * @return
     */
    Boolean isEmailExist(String email);

    /**
     * 手机重置密码
     *
     * @param userEmailDTO
     * @return
     */
    Boolean phoneResetPassword(UserEmailDTO userEmailDTO);

    /**
     * 邮箱重置密码
     *
     * @param userEmailDTO
     * @return
     */
    Boolean emailResetPassword(UserEmailDTO userEmailDTO);

    /**
     * 通过手机号获取用户
     *
     * @param phone
     * @return
     */
    UserDTO getByPhone(String phone);

    /**
     * 通过邮箱获取用户
     *
     * @param email
     * @return
     */
    UserDTO getByEmail(String email);

    /**
     * 更新所有用户的默认头像
     */
    void updateAllUserDefaultHead();

    /**
     * 幂等验证
     *
     * @param url
     * @param timeout
     * @param timeUnit
     * @return
     */
    Boolean checkIdempotent(String url, Integer timeout, TimeUnit timeUnit);

    Boolean isNameValid(String name);

    /**
     * 判断用户是否为管理员
     *
     * @param userId 用户ID
     * @return true 表示是管理员
     */
    Boolean isAdmin(Integer userId);

}
