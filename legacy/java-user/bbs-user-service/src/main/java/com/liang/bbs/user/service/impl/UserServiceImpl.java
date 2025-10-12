package com.liang.bbs.user.service.impl;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.liang.bbs.article.facade.dto.ArticleReadDTO;
import com.liang.bbs.article.facade.server.ArticleService;
import com.liang.bbs.common.enums.ResponseCode;
import com.liang.bbs.common.enums.UserLevelEnum;
import com.liang.bbs.user.facade.dto.user.*;
import com.liang.bbs.user.facade.server.FollowService;
import com.liang.bbs.user.facade.server.UserService;
import com.liang.bbs.user.facade.server.UserLevelService;
import com.liang.bbs.user.persistence.entity.UserPo;
import com.liang.bbs.user.persistence.entity.UserPoExample;
import com.liang.bbs.user.persistence.entity.UserLevelPoExample;
import com.liang.bbs.user.facade.dto.RoleSsoDTO;
import com.liang.bbs.user.persistence.entity.SysRolePo;
import com.liang.bbs.user.persistence.entity.SysUserRolePo;
import com.liang.bbs.user.persistence.entity.SysUserRolePoExample;
import com.liang.bbs.user.persistence.mapper.SysRolePoMapper;
import com.liang.bbs.user.persistence.mapper.SysUserRolePoMapper;
import com.liang.bbs.user.persistence.mapper.UserLevelPoMapper;
import com.liang.bbs.user.persistence.mapper.UserPoExMapper;
import com.liang.bbs.user.persistence.mapper.UserPoMapper;
import com.liang.bbs.user.service.mapstruct.UserLevelMS;
import com.liang.bbs.user.service.mapstruct.UserMS;
import com.liang.bbs.user.service.utils.GenerateUtils;
import com.liang.bbs.user.service.utils.QiniuUtils;
import com.liang.bbs.common.web.basic.ResponseResult;
import com.liang.bbs.common.web.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.dubbo.config.annotation.Reference;
import org.apache.dubbo.config.annotation.Service;
import org.redisson.api.RedissonClient;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import com.liang.bbs.common.util.EncryptUtils;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import com.liang.bbs.user.persistence.mapper.FollowPoMapper;

/**
 */

@Slf4j
@Service
public class UserServiceImpl implements UserService {
private RedissonClient redissonClient;
    private final RedisTemplate redisTemplate;
    @Autowired
    private UserPoMapper userPoMapper;

    @Autowired
    private FollowPoMapper followpomapper;
    @Autowired
    private UserPoExMapper userPoExMapper;

    @Autowired
    private SysUserRolePoMapper sysUserRolePoMapper;

    @Autowired
    private SysRolePoMapper sysRolePoMapper;
    
    @Value("${jwt.secret}")
    private String jwtSecret;
    @Value("${jwt.expiration}")
    private Long jwtExpiration;
    // @Autowired
    // private FollowService followService;

    @Reference
    private ArticleService articleService;

    UserServiceImpl(RedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public UserTokenDTO login(UserLoginDTO userLoginDTO) {
        // 实现登录逻辑
        // redissonClient.getBucket("ok").set(userLoginDTO, 10, TimeUnit.SECONDS);
        // UserLoginDTO userLoginDTO1 = (UserLoginDTO) redissonClient.getBucket("ok").get();
        String name = userLoginDTO.getName();
        String password = userLoginDTO.getPassword();
        UserPo userPo = userPoMapper.selectByName(name);
        if(userPo==null){
            throw BusinessException.build(ResponseCode.NOT_EXISTS);
        }//token只存在reis，也就是在验证时提取源id，再与redis中的token比较
        if(!EncryptUtils.verify(password,userPo.getSalt(),userPo.getPassword())){
            throw BusinessException.build(ResponseCode.NOT_EXISTS);
        }
        return generateUserToken(userPo.getId(), name);
    }

    @Override
    public UserTokenDTO register(UserDTO userDTO) {
        // 实现注册逻辑
        UserPo userPo = UserMS.INSTANCE.toPo(userDTO);
        userPo.setSalt(EncryptUtils.generateSalt());
        userPo.setPassword(EncryptUtils.encrypt(userDTO.getPassword(),userPo.getSalt()));
        userPo.setCreateTime(LocalDateTime.now());
        userPo.setUpdateTime(LocalDateTime.now());
        String fn=System.currentTimeMillis()+".png";
        String hashc=org.apache.commons.codec.digest.DigestUtils.sha256Hex((userPo.getName()+System.currentTimeMillis()).toLowerCase());
        try{// String fileurl="https://gravatar.com/avatar/alexmankl?s=200&r=pg&d=retro";
        //     String res=QiniuUtils.FetchFile(fileurl, userPo.getName()+fn);
        byte[] bytes=GenerateUtils.generateFileName(hashc);
        QiniuUtils.uploadBytes(bytes, userPo.getName()+fn);
userPo.setPicture("http://cdn.papervote.top/"+userPo.getName()+fn);
        }catch(Exception e){
            System.out.println("error_upload");
        }
        int status=userPoExMapper.insertSelective(userPo);
        UserTokenDTO userTokenDTO = generateUserToken(userPo.getId(), userPo.getName());
        return userTokenDTO;
    }

    @Override
    public UserTokenDTO registerAuth(UserDTO userDTO) {
        // 实现三方登录注册逻辑
        return null;
    }
    @Override
    public List<UserDTO> SearchFuzz(UserDTO userDTO) {
        UserPoExample op=new UserPoExample();
        
        // 优先使用 keyword 进行多字段模糊搜索
        String keyword = userDTO.getKeyword();
        if(keyword != null && !keyword.trim().isEmpty()) {
            // 使用 OR 条件搜索多个字段
            String likePattern = "%" + keyword.trim() + "%";
            op.or().andNameLike(likePattern);
            op.or().andEmailLike(likePattern);
            op.or().andPhoneLike(likePattern);
            op.or().andPositionLike(likePattern);
            op.or().andCompanyLike(likePattern);
            op.or().andIntroLike(likePattern);
        } else {
            // 兼容旧的精确字段搜索
            UserPoExample.Criteria c = op.createCriteria();
            boolean hasCondition = false;
            if(userDTO.getName()!=null && !userDTO.getName().trim().isEmpty()) {
                c.andNameLike("%"+userDTO.getName()+"%");
                hasCondition = true;
            }
            if(userDTO.getPosition()!=null && !userDTO.getPosition().trim().isEmpty()) {
                c.andPositionLike("%"+userDTO.getPosition()+"%");
                hasCondition = true;
            }
            if(userDTO.getCompany()!=null && !userDTO.getCompany().trim().isEmpty()) {
                c.andCompanyLike("%"+userDTO.getCompany()+"%");
                hasCondition = true;
            }
            if(userDTO.getIntro()!=null && !userDTO.getIntro().trim().isEmpty()) {
                c.andIntroLike("%"+userDTO.getIntro()+"%");
                hasCondition = true;
            }
            if(userDTO.getPhone()!=null && !userDTO.getPhone().trim().isEmpty()) {
                c.andPhoneLike("%"+userDTO.getPhone()+"%");
                hasCondition = true;
            }
            if(userDTO.getEmail()!=null && !userDTO.getEmail().trim().isEmpty()) {
                c.andEmailLike("%"+userDTO.getEmail()+"%");
                hasCondition = true;
            }
            if(userDTO.getGender()!=null) {
                c.andGenderEqualTo(userDTO.getGender());
                hasCondition = true;
            }
            // 如果没有任何搜索条件，返回空列表而不是全部用户
            if(!hasCondition) {
                return new ArrayList<>();
            }
        }
        
        List<UserPo> userPos=userPoMapper.selectByExample(op);
        List<UserDTO> userDTOs=userPos.stream().map(UserMS.INSTANCE::toDTO).collect(Collectors.toList());
        return userDTOs;
    }
    @Override
    public Boolean isNameValid(String name) {
        // 实现根据ID获取用户信息逻辑
        UserPo userPo = userPoMapper.selectByName(name);
        return userPo!=null;
    }
    @Override
    public List<UserDTO> getbyIds(List<Integer> ids) {
        
        if(CollectionUtils.isEmpty(ids)){
            return new ArrayList<>();
        }
        UserPoExample ex = new UserPoExample();
        ex.createCriteria().andIdIn(ids);
        List<UserPo> userPos = userPoMapper.selectByExample(ex);
        List<UserDTO> userDTOs = userPos.stream().map(UserMS.INSTANCE::toDTO).collect(Collectors.toList());
        return userDTOs;
    }

    @Override
    public Boolean isExpired(String token) {
      try {
        DecodedJWT jwt = JWT.require(Algorithm.HMAC256(jwtSecret))
                .build()
                .verify(token);
        Date expiresAt = jwt.getExpiresAt();
        if (expiresAt == null) {
            // 如果没有设置过期时间，视为不过期
            return false;
        }
        // 当前时间是否已经超过过期时间
        return expiresAt.before(new Date());
    } catch (JWTVerificationException e) {
        // token 无效或验证失败，视为已过期或无效
        return true;
    }
    }

    @Override
    public UserTokenDTO generateUserToken(Integer userid, String username) {
        // 实现token生成
        // String salt=userPoMapper.selectByPrimaryKey(userid).getSalt();
        // if(salt==null){
        //     salt=
        //     SecureRandom random = new SecureRandom();
        // byte[] key = new byte[32]; // 32 bytes = 256 bits
        // random.nextBytes(key);
        // String secret = Base64.getEncoder().encodeToString(key);
        // }
        long expireTime = 1 * 24 * 60 * 60 * 1000L;
       String token = JWT.create()
        .withSubject(username)
        .withClaim("userId", userid)
        .withIssuedAt(new Date())
        .withIssuer("auth0")
        .withExpiresAt(new Date(System.currentTimeMillis() + expireTime))
        .sign(Algorithm.HMAC256(jwtSecret)); 
        UserTokenDTO userTokenDTO = new UserTokenDTO();
        userTokenDTO.setUserid(userid);
        userTokenDTO.setUsername(username);
        userTokenDTO.setToken(token);
        

        return userTokenDTO;
    }

    @Override
    public UserSsoDTO getUserSsoDTOByToken(String token) {
        //实现通过token获取用户信息
        DecodedJWT jwt = JWT.decode(token);
        Integer userId = jwt.getClaim("userId").asInt();
        UserPo userPo = userPoMapper.selectByPrimaryKey(userId);
        UserSsoDTO userSsoDTO = convertPoSso(userPo);

        return userSsoDTO;
        
    }
    
    public UserSsoDTO convertPoSso(UserPo userPo){
        UserSsoDTO userSsoDTO = new UserSsoDTO();
        userSsoDTO.setUserId(userPo.getId());
        userSsoDTO.setUserName(userPo.getName());
        userSsoDTO.setGender(userPo.getGender());
        userSsoDTO.setBirthday(userPo.getBirthday());
        userSsoDTO.setPhone(userPo.getPhone());
        userSsoDTO.setEmail(userPo.getEmail());
        userSsoDTO.setPicture(userPo.getPicture());
        userSsoDTO.setIntro(userPo.getIntro());
        userSsoDTO.setOrgId(userPo.getOrgId());
        // 加载角色列表
        try {
            SysUserRolePoExample example = new SysUserRolePoExample();
            example.createCriteria().andUserIdEqualTo(userPo.getId());
            List<SysUserRolePo> userRoles = sysUserRolePoMapper.selectByExample(example);
            if (!CollectionUtils.isEmpty(userRoles)) {
                List<RoleSsoDTO> roles = new ArrayList<>();
                for (SysUserRolePo ur : userRoles) {
                    SysRolePo role = sysRolePoMapper.selectByPrimaryKey(ur.getRoleId());
                    if (role != null) {
                        RoleSsoDTO roleDto = new RoleSsoDTO();
                        roleDto.setId(role.getId().intValue());
                        roleDto.setCode(role.getRoleCode());
                        roleDto.setName(role.getRoleName());
                        roleDto.setGrade("admin".equals(role.getRoleCode()) ? "NS_SUPER_ADMIN_ROLE" : "NS_BASE_ROLE");
                        roles.add(roleDto);
                    }
                }
                userSsoDTO.setRoles(roles);
            }
        } catch (Exception e) {
            log.warn("加载用户角色失败, userId={}: {}", userPo.getId(), e.getMessage());
        }
        return userSsoDTO;
    }

    @Override
    public Boolean isAdmin(Integer userId) {
        try {
            SysUserRolePoExample example = new SysUserRolePoExample();
            example.createCriteria().andUserIdEqualTo(userId);
            List<SysUserRolePo> userRoles = sysUserRolePoMapper.selectByExample(example);
            if (CollectionUtils.isEmpty(userRoles)) return false;
            for (SysUserRolePo ur : userRoles) {
                SysRolePo role = sysRolePoMapper.selectByPrimaryKey(ur.getRoleId());
                if (role != null && "admin".equals(role.getRoleCode())) {
                    return true;
                }
            }
        } catch (Exception e) {
            log.warn("isAdmin 查询失败, userId={}: {}", userId, e.getMessage());
        }
        return false;
    }

    @Override
    public Boolean verifyToken(String token) {
        // 实现token验证
        try {
            JWT.require(Algorithm.HMAC256(jwtSecret))
            .withIssuer("auth0")
            .build()
            .verify(token);
            return !isExpired(token);
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public Integer getClaim(String token) {
        // 实现获取token中的用户ID
        DecodedJWT jwt = JWT.decode(token);
        Integer userId = jwt.getClaim("userId").asInt();
        return userId;
    }

    @Override
    public String innerLoginUrl(String referer) {
        // 实现构造登录URL
        return "http://papervote.top/login";
    }

    @Override
    public PageInfo<UserDTO> getList(UserSearchDTO userSearchDTO) {
        // 实现分页获取用户列表
        PageHelper.startPage(userSearchDTO.getCurrentPage(), userSearchDTO.getPageSize());
        List<UserPo> userPos = userPoMapper.selectByExample(new UserPoExample());
        List<UserDTO> userListDTOS = userPos.stream().map(UserMS.INSTANCE::toDTO).collect(Collectors.toList());
        return new PageInfo<>(userListDTOS);
    }

    @Override
    public List<UserListDTO> getAllList() {
        // 实现获取所有用户
        return null;
    }

    @Override
    public void logout(String token) {
        // 实现登出逻辑
        // redisTemplate.delete(token);
    }

    @Override
    public Map<Integer, String> getGender() {
        // 实现获取性别选项
        return null;
    }

    @Override
    public UserDTO getById(Integer userid) {
        // 实现通过ID获取用户
        UserPo userPo = userPoMapper.selectByPrimaryKey(userid);
    if (userPo == null) {
        throw BusinessException.build(ResponseCode.NOT_EXISTS, "用户不存在");
    }
    return UserMS.INSTANCE.toDTO(userPo);
    }

    @Override
    public UserDTO getByIdWithoutSensitive(Integer userid) {
        // 实现通过ID获取用户（不包含敏感信息）
        UserPo userPo = userPoMapper.selectByPrimaryKey(userid);
        if (userPo == null) {
            return null; // 返回null而不是抛异常，避免影响列表显示
        }
        UserDTO userDTO = UserMS.INSTANCE.toDTO(userPo);
        // 清除敏感信息
        userDTO.setPassword(null);
        userDTO.setSalt(null);
        return userDTO;
    }

    @Override
    public List<UserDTO> getByIds(List<Integer> userIds) {
        // 实现通过ID列表获取用户
        return null;
    }

    @Override
    public List<UserDTO> getListByRoleId(Integer roleId) {
        // 实现通过角色ID获取用户
        return null;
    }

    @Override
    public List<UserDTO> notUserOfThisRole(List<Integer> roleIds) {
        // 实现获取不属于这些角色的用户
        return null;
    }

    @Override
    public Boolean updateUserRolesOrg(UserRoleOrgDTO userRoleOrgDTO, UserSsoDTO currentUser) {
        // 实现更新用户角色/组织架构
        return null;
    }

    @Override
    public Boolean changeStatus(UserStateDTO userStateDTO) {
        // 实现变更用户状态
        return null;
    }

    @Override
    public Boolean uploadUserPicture(byte[] bytes, String sourceFileName, UserSsoDTO currentUser) {
        // 实现上传用户头像
        String url=QiniuUtils.uploadBytes(bytes, sourceFileName);
        currentUser.setPicture(url);
        UserPo po=userPoMapper.selectByPrimaryKey(currentUser.getUserId());
        po.setPicture(url);
        userPoMapper.updateByPrimaryKeySelective(po);
        return null;
    }

    @Override
    public UserListDTO getUserListById(Integer userid) {
        // 实现获取用户详情
        return null;
    }

    @Override
    public Boolean updateUserBasicInfo(UserDTO userDTO, UserSsoDTO currentUser) {
        // 实现更新用户基本信息
        return null;
    }

    @Override
    public Boolean sendEmailVerifyCode(String email, UserSsoDTO currentUser) {
        // 实现发送邮件验证码
        return null;
    }

    @Override
    public Boolean sendSmsVerifyCode(String phone, UserSsoDTO currentUser) {
        // 实现发送短信验证码
        return null;
    }

    @Override
    public Boolean bindEmail(UserEmailDTO userEmailDTO, UserSsoDTO currentUser) {
        // 实现绑定邮箱
        return null;
    }

    @Override
    public Boolean bindPhone(UserEmailDTO userEmailDTO, UserSsoDTO currentUser) {
        // 实现绑定手机
        return null;
    }

    @Override
    public Boolean untieEmail(UserSsoDTO currentUser) {
        // 实现解绑邮箱
        return null;
    }

    @Override
    public Boolean untiePhone(UserSsoDTO currentUser) {
        // 实现解绑手机
        return null;
    }

    @Override
    public Boolean updatePassword(UserPasswordDTO passwordDTO, UserSsoDTO currentUser) {
        // 实现更新密码
        try {
            UserPo po=userPoMapper.selectByPrimaryKey(currentUser.getUserId());
        String salt=EncryptUtils.generateSalt();
        String password=EncryptUtils.encrypt(passwordDTO.getNewPassword(), salt);
        po.setPassword(password);
        po.setSalt(salt);
        return true;
        } catch (Exception e) {
            // TODO: handle exception
            return false;
        }
    }

    @Override
    public Boolean isValidEmail(String email) {
        // 实现邮箱判重
        return null;
    }

    @Override
    public Boolean isValidPhone(String phone) {
        // 实现手机判重
        return null;
    }

    @Override
    public List<UserListDTO> getNewUser() {
        // 实现获取新用户
        return null;
    }

    @Override
    public Map<String, Object> uploadCommonPicture(byte[] bytes, String sourceFileName, UserSsoDTO currentUser) {
        // 实现上传通用图片
        return null;
    }

    @Override
    public void updateAllUserBaseRole() {
        // 实现更新所有用户的基础角色
    }

    @Override
    public Boolean isValidUser(String userName, UserSsoDTO currentUser) {
        // TODO Auto-generated method stub
        if(currentUser.getUserName().equals(userName))return true;
        else return false;
    }

    @Override
    public void insertChatUser(String userName) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'insertChatUser'");
    }

    @Override
    public void insertGroup(String id) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'insertGroup'");
    }

    @Override
    public Boolean isPhoneExist(String phone) {
        // TODO Auto-generated method stub
        UserPo userPo = userPoMapper.selectByPhoneNumber(phone);
        if(userPo==null)return false;
        else return true;
        
    }

    @Override
    public Boolean isEmailExist(String email) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'isEmailExist'");
    }

    @Override
    public Boolean phoneResetPassword(UserEmailDTO userEmailDTO) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'phoneResetPassword'");
    }

    @Override
    public Boolean emailResetPassword(UserEmailDTO userEmailDTO) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'emailResetPassword'");
    }

    @Override
    public UserDTO getByPhone(String phone) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getByPhone'");
    }

    @Override
    public UserDTO getByEmail(String email) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getByEmail'");
    }

    @Override
    public void updateAllUserDefaultHead() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'updateAllUserDefaultHead'");
    }

    @Override
    public Boolean checkIdempotent(String url, Integer timeout, TimeUnit timeUnit) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'checkIdempotent'");
    }
}
