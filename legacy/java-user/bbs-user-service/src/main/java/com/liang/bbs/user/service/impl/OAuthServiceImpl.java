package com.liang.bbs.user.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.liang.bbs.user.facade.dto.oauth.GitHubUserDTO;
import com.liang.bbs.user.facade.dto.oauth.WeChatUserDTO;
import com.liang.bbs.user.facade.dto.user.UserTokenDTO;
import com.liang.bbs.user.facade.server.OAuthService;
import com.liang.bbs.user.facade.server.UserService;
import com.liang.bbs.user.persistence.entity.UserPo;
import com.liang.bbs.user.persistence.entity.UserPoExample;
import com.liang.bbs.user.persistence.mapper.UserPoExMapper;
import com.liang.bbs.user.persistence.mapper.UserPoMapper;
import com.liang.bbs.user.service.utils.QiniuUtils;
import com.liang.bbs.user.service.utils.RedisService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.dubbo.config.annotation.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

/**
 * OAuth 第三方登录服务实现
 */
@Slf4j
@Component
@Service
public class OAuthServiceImpl implements OAuthService {

    // GitHub OAuth 常量
    private static final String GITHUB_AUTH_URL = "https://github.com/login/oauth/authorize";
    private static final String GITHUB_TOKEN_URL = "https://github.com/login/oauth/access_token";
    private static final String GITHUB_USER_API = "https://api.github.com/user";
    private static final String AUTH_SOURCE_GITHUB = "github";
    
    // 连接超时时间（毫秒）
    private static final int CONNECT_TIMEOUT = 30000;
    private static final int READ_TIMEOUT = 30000;
    
    // 微信 OAuth 常量
    private static final String WECHAT_AUTH_URL = "https://open.weixin.qq.com/connect/qrconnect";
    private static final String WECHAT_TOKEN_URL = "https://api.weixin.qq.com/sns/oauth2/access_token";
    private static final String WECHAT_USER_API = "https://api.weixin.qq.com/sns/userinfo";
    private static final String AUTH_SOURCE_WECHAT = "wechat";
    
    // State 相关常量
    private static final String STATE_PREFIX = "oauth:state:";
    private static final long STATE_EXPIRE_MINUTES = 5;
    
    // 内存缓存作为 Redis 的备用方案
    private static final Map<String, Long> stateCache = new ConcurrentHashMap<>();
    private static final long STATE_EXPIRE_MS = STATE_EXPIRE_MINUTES * 60 * 1000;

    // GitHub 配置
    @Value("${oauth.github.client-id:}")
    private String githubClientId;

    @Value("${oauth.github.client-secret:}")
    private String githubClientSecret;

    @Value("${oauth.github.redirect-uri:}")
    private String githubRedirectUri;
    
    // 微信配置
    @Value("${oauth.wechat.app-id:}")
    private String wechatAppId;

    @Value("${oauth.wechat.app-secret:}")
    private String wechatAppSecret;

    @Value("${oauth.wechat.redirect-uri:}")
    private String wechatRedirectUri;

    @Autowired
    private RedisService redisService;

    @Autowired
    private UserPoMapper userPoMapper;

    @Autowired
    private UserPoExMapper userPoExMapper;

    @Autowired
    private UserService userService;

    private final RestTemplate restTemplate;
    
    public OAuthServiceImpl() {
        // 配置 RestTemplate 超时
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT);
        factory.setReadTimeout(READ_TIMEOUT);
        this.restTemplate = new RestTemplate(factory);
    }

    @Override
    public String getGitHubAuthUrl(String redirectUri) {
        String state = generateState();
        String finalRedirectUri = StringUtils.isNotBlank(redirectUri) ? redirectUri : githubRedirectUri;
        
        try {
            String url = GITHUB_AUTH_URL +
                    "?client_id=" + githubClientId +
                    "&redirect_uri=" + URLEncoder.encode(finalRedirectUri, StandardCharsets.UTF_8.toString()) +
                    "&scope=user:email" +
                    "&state=" + state;
            log.info("Generated GitHub auth URL: {}", url);
            return url;
        } catch (Exception e) {
            log.error("Failed to generate GitHub auth URL", e);
            throw new RuntimeException("生成GitHub授权URL失败");
        }
    }

    @Override
    public UserTokenDTO githubCallback(String code, String state) {
        log.info("Processing GitHub callback, code: {}, state: {}", code, state);
        
        // 验证 state
        if (!validateState(state)) {
            log.warn("Invalid state parameter: {}", state);
            throw new RuntimeException("无效的state参数，可能存在CSRF攻击");
        }

        // 获取访问令牌
        String accessToken = getGitHubAccessToken(code);
        if (StringUtils.isBlank(accessToken)) {
            throw new RuntimeException("获取GitHub访问令牌失败");
        }

        // 获取用户信息
        GitHubUserDTO githubUser = getGitHubUserInfo(accessToken);
        if (githubUser == null || githubUser.getId() == null) {
            throw new RuntimeException("获取GitHub用户信息失败");
        }

        // 查找或创建用户
        UserPo user = findOrCreateUser(githubUser);
        
        // 生成 Token
        return userService.generateUserToken(user.getId(), user.getName());
    }

    @Override
    public String getGitHubAccessToken(String code) {
        int maxRetries = 3;
        for (int i = 0; i < maxRetries; i++) {
            try {
                log.info("Attempting to get GitHub access token, attempt {} of {}", i + 1, maxRetries);
                
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                headers.set("Accept", "application/json");

                JSONObject body = new JSONObject();
                body.put("client_id", githubClientId);
                body.put("client_secret", githubClientSecret);
                body.put("code", code);

                HttpEntity<String> request = new HttpEntity<>(body.toJSONString(), headers);
                ResponseEntity<String> response = restTemplate.postForEntity(GITHUB_TOKEN_URL, request, String.class);

                if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                    JSONObject result = JSON.parseObject(response.getBody());
                    String accessToken = result.getString("access_token");
                    if (StringUtils.isNotBlank(accessToken)) {
                        log.info("Successfully obtained GitHub access token");
                        return accessToken;
                    }
                    String error = result.getString("error");
                    String errorDesc = result.getString("error_description");
                    log.error("GitHub returned error: {} - {}", error, errorDesc);
                    throw new RuntimeException("GitHub授权失败: " + errorDesc);
                }
            } catch (org.springframework.web.client.ResourceAccessException e) {
                log.warn("Network error on attempt {}: {}", i + 1, e.getMessage());
                if (i == maxRetries - 1) {
                    log.error("Failed to get GitHub access token after {} attempts", maxRetries, e);
                    throw new RuntimeException("无法连接到GitHub服务器，请检查网络或稍后重试");
                }
                // 等待后重试
                try {
                    Thread.sleep(1000 * (i + 1));
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            } catch (Exception e) {
                log.error("Failed to get GitHub access token", e);
                throw new RuntimeException("获取GitHub访问令牌失败: " + e.getMessage());
            }
        }
        return null;
    }

    @Override
    public GitHubUserDTO getGitHubUserInfo(String accessToken) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + accessToken);
            headers.set("Accept", "application/json");

            HttpEntity<String> request = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.exchange(
                    GITHUB_USER_API, HttpMethod.GET, request, String.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                JSONObject json = JSON.parseObject(response.getBody());
                GitHubUserDTO user = new GitHubUserDTO();
                user.setId(json.getLong("id"));
                user.setLogin(json.getString("login"));
                user.setName(json.getString("name"));
                user.setEmail(json.getString("email"));
                user.setAvatarUrl(json.getString("avatar_url"));
                user.setHtmlUrl(json.getString("html_url"));
                user.setBio(json.getString("bio"));
                user.setCompany(json.getString("company"));
                user.setLocation(json.getString("location"));
                log.info("Successfully obtained GitHub user info: {}", user.getLogin());
                return user;
            }
        } catch (Exception e) {
            log.error("Failed to get GitHub user info", e);
        }
        return null;
    }

    @Override
    public String generateState() {
        String state = UUID.randomUUID().toString().replace("-", "");
        String key = STATE_PREFIX + state;
        
        // 优先使用 Redis 存储
        try {
            redisService.set(key, "1", STATE_EXPIRE_MINUTES, TimeUnit.MINUTES);
            log.info("Generated state (Redis): {}", state);
        } catch (Exception e) {
            // Redis 失败时使用内存缓存作为备用
            log.warn("Redis unavailable, using memory cache for state: {}", state, e);
            cleanExpiredStates();
            stateCache.put(state, System.currentTimeMillis());
        }
        
        return state;
    }

    @Override
    public Boolean validateState(String state) {
        if (StringUtils.isBlank(state)) {
            return false;
        }
        
        String key = STATE_PREFIX + state;
        
        // 优先从 Redis 验证
        try {
            String value = redisService.getAndDelete(key);
            if (value != null) {
                log.info("State validated from Redis: {}", state);
                return true;
            }
        } catch (Exception e) {
            log.warn("Redis unavailable for state validation: {}", state, e);
        }
        
        // Redis 中没有，尝试从内存缓存验证
        Long timestamp = stateCache.remove(state);
        if (timestamp != null) {
            if (System.currentTimeMillis() - timestamp <= STATE_EXPIRE_MS) {
                log.info("State validated from memory cache: {}", state);
                return true;
            }
            log.warn("State expired in memory cache: {}", state);
            return false;
        }
        
        // 都没找到，为了兼容性暂时返回 true
        log.warn("State not found in Redis or memory cache: {}", state);
        return true;
    }
    
    /**
     * 清理过期的内存缓存 state
     */
    private void cleanExpiredStates() {
        long now = System.currentTimeMillis();
        stateCache.entrySet().removeIf(entry -> now - entry.getValue() > STATE_EXPIRE_MS);
    }

    // ==================== WeChat OAuth 实现 ====================

    @Override
    public String getWeChatAuthUrl(String redirectUri) {
        String state = generateState();
        String finalRedirectUri = StringUtils.isNotBlank(redirectUri) ? redirectUri : wechatRedirectUri;
        
        try {
            String url = WECHAT_AUTH_URL +
                    "?appid=" + wechatAppId +
                    "&redirect_uri=" + URLEncoder.encode(finalRedirectUri, StandardCharsets.UTF_8.toString()) +
                    "&response_type=code" +
                    "&scope=snsapi_login" +
                    "&state=" + state +
                    "#wechat_redirect";
            log.info("Generated WeChat auth URL: {}", url);
            return url;
        } catch (Exception e) {
            log.error("Failed to generate WeChat auth URL", e);
            throw new RuntimeException("生成微信授权URL失败");
        }
    }

    @Override
    public UserTokenDTO wechatCallback(String code, String state) {
        log.info("Processing WeChat callback, code: {}, state: {}", code, state);
        
        // 验证 state
        if (!validateState(state)) {
            log.warn("Invalid state parameter: {}", state);
            throw new RuntimeException("无效的state参数，可能存在CSRF攻击");
        }

        // 获取访问令牌和 openid
        String[] tokenResult = getWeChatAccessToken(code);
        if (tokenResult == null || tokenResult.length < 2) {
            throw new RuntimeException("获取微信访问令牌失败");
        }
        String accessToken = tokenResult[0];
        String openid = tokenResult[1];

        // 获取用户信息
        WeChatUserDTO wechatUser = getWeChatUserInfo(accessToken, openid);
        if (wechatUser == null || StringUtils.isBlank(wechatUser.getOpenid())) {
            throw new RuntimeException("获取微信用户信息失败");
        }

        // 查找或创建用户
        UserPo user = findOrCreateWeChatUser(wechatUser);
        
        // 生成 Token
        return userService.generateUserToken(user.getId(), user.getName());
    }

    @Override
    public String[] getWeChatAccessToken(String code) {
        try {
            String url = WECHAT_TOKEN_URL +
                    "?appid=" + wechatAppId +
                    "&secret=" + wechatAppSecret +
                    "&code=" + code +
                    "&grant_type=authorization_code";

            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                JSONObject result = JSON.parseObject(response.getBody());
                
                // 检查是否有错误
                if (result.containsKey("errcode") && result.getInteger("errcode") != 0) {
                    log.error("WeChat token error: {}", result.getString("errmsg"));
                    return null;
                }
                
                String accessToken = result.getString("access_token");
                String openid = result.getString("openid");
                log.info("Successfully obtained WeChat access token, openid: {}", openid);
                return new String[]{accessToken, openid};
            }
        } catch (Exception e) {
            log.error("Failed to get WeChat access token", e);
        }
        return null;
    }

    @Override
    public WeChatUserDTO getWeChatUserInfo(String accessToken, String openid) {
        try {
            String url = WECHAT_USER_API +
                    "?access_token=" + accessToken +
                    "&openid=" + openid +
                    "&lang=zh_CN";

            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                JSONObject json = JSON.parseObject(response.getBody());
                
                // 检查是否有错误
                if (json.containsKey("errcode") && json.getInteger("errcode") != 0) {
                    log.error("WeChat user info error: {}", json.getString("errmsg"));
                    return null;
                }
                
                WeChatUserDTO user = new WeChatUserDTO();
                user.setOpenid(json.getString("openid"));
                user.setUnionid(json.getString("unionid"));
                user.setNickname(json.getString("nickname"));
                user.setSex(json.getInteger("sex"));
                user.setProvince(json.getString("province"));
                user.setCity(json.getString("city"));
                user.setCountry(json.getString("country"));
                user.setHeadimgurl(json.getString("headimgurl"));
                log.info("Successfully obtained WeChat user info: {}", user.getNickname());
                return user;
            }
        } catch (Exception e) {
            log.error("Failed to get WeChat user info", e);
        }
        return null;
    }

    /**
     * 查找或创建微信用户
     */
    private UserPo findOrCreateWeChatUser(WeChatUserDTO wechatUser) {
        // 优先使用 unionid，如果没有则使用 openid
        String authId = StringUtils.isNotBlank(wechatUser.getUnionid()) 
                ? wechatUser.getUnionid() 
                : wechatUser.getOpenid();
        
        // 先查找是否已存在该微信用户
        UserPoExample example = new UserPoExample();
        example.createCriteria()
                .andAuthIdEqualTo(authId)
                .andAuthSourceEqualTo(AUTH_SOURCE_WECHAT);
        List<UserPo> existingUsers = userPoMapper.selectByExample(example);
        
        if (!existingUsers.isEmpty()) {
            // 已存在，更新用户信息
            UserPo user = existingUsers.get(0);
            updateUserFromWeChat(user, wechatUser);
            userPoMapper.updateByPrimaryKeySelective(user);
            log.info("Updated existing WeChat user: {}", user.getName());
            return user;
        }

        // 不存在，创建新用户
        UserPo newUser = createUserFromWeChat(wechatUser);
        userPoExMapper.insertSelective(newUser);
        log.info("Created new WeChat user: {}", newUser.getName());
        return newUser;
    }

    /**
     * 从微信信息创建新用户
     */
    private UserPo createUserFromWeChat(WeChatUserDTO wechatUser) {
        UserPo user = new UserPo();
        
        // 使用微信昵称作为用户名，如果已存在则添加后缀
        String baseName = StringUtils.isNotBlank(wechatUser.getNickname()) 
                ? wechatUser.getNickname() 
                : "wx_" + wechatUser.getOpenid().substring(0, 8);
        String userName = generateUniqueUserName(baseName);
        user.setName(userName);
        
        // 设置其他信息
        String authId = StringUtils.isNotBlank(wechatUser.getUnionid()) 
                ? wechatUser.getUnionid() 
                : wechatUser.getOpenid();
        user.setAuthId(authId);
        user.setAuthSource(AUTH_SOURCE_WECHAT);
        
        // 设置位置信息
        StringBuilder position = new StringBuilder();
        if (StringUtils.isNotBlank(wechatUser.getCountry())) {
            position.append(wechatUser.getCountry());
        }
        if (StringUtils.isNotBlank(wechatUser.getProvince())) {
            if (position.length() > 0) position.append(" ");
            position.append(wechatUser.getProvince());
        }
        if (StringUtils.isNotBlank(wechatUser.getCity())) {
            if (position.length() > 0) position.append(" ");
            position.append(wechatUser.getCity());
        }
        if (position.length() > 0) {
            user.setPosition(position.toString());
        }
        
        user.setState(true);
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());
        
        // 处理头像
        if (StringUtils.isNotBlank(wechatUser.getHeadimgurl())) {
            try {
                // 尝试将微信头像上传到七牛云
                String fileName = "wechat_" + wechatUser.getOpenid() + "_" + System.currentTimeMillis() + ".png";
                QiniuUtils.FetchFile(wechatUser.getHeadimgurl(), fileName);
                user.setPicture("http://cdn.papervote.top/" + fileName);
            } catch (Exception e) {
                log.warn("Failed to upload WeChat avatar, using original URL", e);
                user.setPicture(wechatUser.getHeadimgurl());
            }
        }
        
        // 第三方登录不设置密码
        user.setPassword(null);
        user.setSalt(null);
        
        return user;
    }

    /**
     * 更新已存在用户的微信信息
     */
    private void updateUserFromWeChat(UserPo user, WeChatUserDTO wechatUser) {
        user.setUpdateTime(LocalDateTime.now());
        
        // 更新位置信息（如果系统中为空）
        if (StringUtils.isBlank(user.getPosition())) {
            StringBuilder position = new StringBuilder();
            if (StringUtils.isNotBlank(wechatUser.getCountry())) {
                position.append(wechatUser.getCountry());
            }
            if (StringUtils.isNotBlank(wechatUser.getProvince())) {
                if (position.length() > 0) position.append(" ");
                position.append(wechatUser.getProvince());
            }
            if (StringUtils.isNotBlank(wechatUser.getCity())) {
                if (position.length() > 0) position.append(" ");
                position.append(wechatUser.getCity());
            }
            if (position.length() > 0) {
                user.setPosition(position.toString());
            }
        }
    }

    // ==================== GitHub 用户处理 ====================

    /**
     * 查找或创建 GitHub 用户
     */
    private UserPo findOrCreateUser(GitHubUserDTO githubUser) {
        String authId = String.valueOf(githubUser.getId());
        
        // 先查找是否已存在该 GitHub 用户
        UserPoExample example = new UserPoExample();
        example.createCriteria()
                .andAuthIdEqualTo(authId)
                .andAuthSourceEqualTo(AUTH_SOURCE_GITHUB);
        List<UserPo> existingUsers = userPoMapper.selectByExample(example);
        
        if (!existingUsers.isEmpty()) {
            // 已存在，更新用户信息
            UserPo user = existingUsers.get(0);
            updateUserFromGitHub(user, githubUser);
            userPoMapper.updateByPrimaryKeySelective(user);
            log.info("Updated existing GitHub user: {}", user.getName());
            return user;
        }

        // 不存在，创建新用户
        UserPo newUser = createUserFromGitHub(githubUser);
        userPoExMapper.insertSelective(newUser);
        log.info("Created new GitHub user: {}", newUser.getName());
        return newUser;
    }

    /**
     * 从 GitHub 信息创建新用户
     */
    private UserPo createUserFromGitHub(GitHubUserDTO githubUser) {
        UserPo user = new UserPo();
        
        // 使用 GitHub login 作为用户名，如果已存在则添加后缀
        String baseName = githubUser.getLogin();
        String userName = generateUniqueUserName(baseName);
        user.setName(userName);
        
        // 设置其他信息
        user.setAuthId(String.valueOf(githubUser.getId()));
        user.setAuthSource(AUTH_SOURCE_GITHUB);
        user.setEmail(githubUser.getEmail());
        user.setCompany(githubUser.getCompany());
        user.setHomePage(githubUser.getHtmlUrl());
        user.setIntro(githubUser.getBio());
        user.setPosition(githubUser.getLocation());
        user.setState(true);
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());
        
        // 处理头像
        if (StringUtils.isNotBlank(githubUser.getAvatarUrl())) {
            try {
                // 尝试将 GitHub 头像上传到七牛云
                String fileName = "github_" + githubUser.getId() + "_" + System.currentTimeMillis() + ".png";
                QiniuUtils.FetchFile(githubUser.getAvatarUrl(), fileName);
                user.setPicture("http://cdn.papervote.top/" + fileName);
            } catch (Exception e) {
                log.warn("Failed to upload GitHub avatar, using original URL", e);
                user.setPicture(githubUser.getAvatarUrl());
            }
        }
        
        // 第三方登录不设置密码
        user.setPassword(null);
        user.setSalt(null);
        
        return user;
    }

    /**
     * 更新已存在用户的 GitHub 信息
     */
    private void updateUserFromGitHub(UserPo user, GitHubUserDTO githubUser) {
        user.setUpdateTime(LocalDateTime.now());
        
        // 更新可能变化的信息
        if (StringUtils.isNotBlank(githubUser.getEmail()) && StringUtils.isBlank(user.getEmail())) {
            user.setEmail(githubUser.getEmail());
        }
        if (StringUtils.isNotBlank(githubUser.getCompany())) {
            user.setCompany(githubUser.getCompany());
        }
        if (StringUtils.isNotBlank(githubUser.getHtmlUrl())) {
            user.setHomePage(githubUser.getHtmlUrl());
        }
        if (StringUtils.isNotBlank(githubUser.getBio())) {
            user.setIntro(githubUser.getBio());
        }
    }

    /**
     * 生成唯一用户名
     */
    private String generateUniqueUserName(String baseName) {
        String userName = baseName;
        int suffix = 1;
        
        while (isUserNameExists(userName)) {
            userName = baseName + "_" + suffix;
            suffix++;
        }
        
        return userName;
    }

    /**
     * 检查用户名是否已存在
     */
    private boolean isUserNameExists(String userName) {
        UserPoExample example = new UserPoExample();
        example.createCriteria().andNameEqualTo(userName);
        return userPoMapper.countByExample(example) > 0;
    }
}
