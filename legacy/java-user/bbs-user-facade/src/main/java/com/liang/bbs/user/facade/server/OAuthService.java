package com.liang.bbs.user.facade.server;

import com.liang.bbs.user.facade.dto.oauth.GitHubUserDTO;
import com.liang.bbs.user.facade.dto.oauth.WeChatUserDTO;
import com.liang.bbs.user.facade.dto.user.UserTokenDTO;

/**
 * OAuth 第三方登录服务接口
 */
public interface OAuthService {

    // ==================== GitHub OAuth ====================

    /**
     * 获取 GitHub 授权 URL
     *
     * @param redirectUri 回调地址
     * @return 授权URL
     */
    String getGitHubAuthUrl(String redirectUri);

    /**
     * GitHub 登录回调处理
     *
     * @param code 授权码
     * @param state 状态参数
     * @return 用户Token
     */
    UserTokenDTO githubCallback(String code, String state);

    /**
     * 通过授权码获取 GitHub 访问令牌
     *
     * @param code 授权码
     * @return 访问令牌
     */
    String getGitHubAccessToken(String code);

    /**
     * 通过访问令牌获取 GitHub 用户信息
     *
     * @param accessToken 访问令牌
     * @return GitHub用户信息
     */
    GitHubUserDTO getGitHubUserInfo(String accessToken);

    // ==================== WeChat OAuth ====================

    /**
     * 获取微信授权 URL
     *
     * @param redirectUri 回调地址
     * @return 授权URL
     */
    String getWeChatAuthUrl(String redirectUri);

    /**
     * 微信登录回调处理
     *
     * @param code 授权码
     * @param state 状态参数
     * @return 用户Token
     */
    UserTokenDTO wechatCallback(String code, String state);

    /**
     * 通过授权码获取微信访问令牌和 openid
     *
     * @param code 授权码
     * @return 包含 access_token 和 openid 的数组 [access_token, openid]
     */
    String[] getWeChatAccessToken(String code);

    /**
     * 通过访问令牌获取微信用户信息
     *
     * @param accessToken 访问令牌
     * @param openid 用户openid
     * @return 微信用户信息
     */
    WeChatUserDTO getWeChatUserInfo(String accessToken, String openid);

    // ==================== Common ====================

    /**
     * 生成随机 state 参数
     *
     * @return state
     */
    String generateState();

    /**
     * 验证 state 参数
     *
     * @param state 状态参数
     * @return 是否有效
     */
    Boolean validateState(String state);
}
