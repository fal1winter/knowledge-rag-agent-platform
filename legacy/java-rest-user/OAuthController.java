package com.liang.bbs.rest.controller;

import com.liang.bbs.common.constant.AuthSystemConstants;
import com.liang.bbs.common.constant.TimeoutConstants;
import com.liang.bbs.common.web.basic.ResponseResult;
import com.liang.bbs.rest.config.login.NoNeedLogin;
import com.liang.bbs.rest.config.swagger.ApiVersion;
import com.liang.bbs.rest.config.swagger.ApiVersionConstant;
import com.liang.bbs.user.facade.dto.user.UserTokenDTO;
import com.liang.bbs.user.facade.server.OAuthService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.Reference;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * OAuth 第三方登录控制器
 */
@Slf4j
@RestController
@RequestMapping("/oauth")
@Api(tags = "OAuth第三方登录接口")
@CrossOrigin(origins = "*")
public class OAuthController {

    @Reference
    private OAuthService oAuthService;

    @Value("${cookie.domain}")
    private String domain;

    @Value("${oauth.github.frontend-callback-url:http://papervote.top/oauth/callback}")
    private String frontendCallbackUrl;

    @Value("${oauth.wechat.frontend-callback-url:http://papervote.top/oauth/callback}")
    private String wechatFrontendCallbackUrl;

    // ==================== GitHub OAuth ====================

    @NoNeedLogin
    @GetMapping("/github/authorize")
    @ApiOperation(value = "获取GitHub授权URL")
    @ApiVersion(group = ApiVersionConstant.V_300)
    public ResponseResult<Map<String, String>> getGitHubAuthUrl(
            @ApiParam(value = "自定义回调地址") @RequestParam(required = false) String redirectUri) {
        String authUrl = oAuthService.getGitHubAuthUrl(redirectUri);
        Map<String, String> result = new HashMap<>();
        result.put("authUrl", authUrl);
        return ResponseResult.success(result);
    }

    @NoNeedLogin
    @GetMapping("/github/callback")
    @ApiOperation(value = "GitHub登录回调")
    @ApiVersion(group = ApiVersionConstant.V_300)
    public void githubCallback(
            @ApiParam(value = "授权码", required = true) @RequestParam String code,
            @ApiParam(value = "状态参数", required = true) @RequestParam String state,
            HttpServletResponse response) throws IOException {
        try {
            log.info("GitHub callback received, code: {}, state: {}", code, state);
            UserTokenDTO userToken = oAuthService.githubCallback(code, state);
            
            // 设置 Cookie
            addCookie(userToken.getToken(), response);
            
            // 重定向到前端页面，带上登录成功标识
            String redirectUrl = frontendCallbackUrl + "?success=true&token=" + userToken.getToken();
            response.sendRedirect(redirectUrl);
        } catch (Exception e) {
            log.error("GitHub callback failed", e);
            // 重定向到前端页面，带上错误信息
            String redirectUrl = frontendCallbackUrl + "?success=false&error=" + URLEncoder.encode(e.getMessage(), StandardCharsets.UTF_8.toString());
            response.sendRedirect(redirectUrl);
        }
    }

    @NoNeedLogin
    @PostMapping("/github/login")
    @ApiOperation(value = "GitHub登录（前端传递code）")
    @ApiVersion(group = ApiVersionConstant.V_300)
    public ResponseResult<UserTokenDTO> githubLogin(
            @ApiParam(value = "授权码", required = true) @RequestParam String code,
            @ApiParam(value = "状态参数", required = true) @RequestParam String state,
            HttpServletResponse response) {
        log.info("GitHub login request, code: {}, state: {}", code, state);
        UserTokenDTO userToken = oAuthService.githubCallback(code, state);
        
        // 设置 Cookie
        addCookie(userToken.getToken(), response);
        
        return ResponseResult.success(userToken);
    }

    // ==================== WeChat OAuth ====================

    @NoNeedLogin
    @GetMapping("/wechat/authorize")
    @ApiOperation(value = "获取微信授权URL")
    @ApiVersion(group = ApiVersionConstant.V_300)
    public ResponseResult<Map<String, String>> getWeChatAuthUrl(
            @ApiParam(value = "自定义回调地址") @RequestParam(required = false) String redirectUri) {
        String authUrl = oAuthService.getWeChatAuthUrl(redirectUri);
        Map<String, String> result = new HashMap<>();
        result.put("authUrl", authUrl);
        return ResponseResult.success(result);
    }

    @NoNeedLogin
    @GetMapping("/wechat/callback")
    @ApiOperation(value = "微信登录回调")
    @ApiVersion(group = ApiVersionConstant.V_300)
    public void wechatCallback(
            @ApiParam(value = "授权码", required = true) @RequestParam String code,
            @ApiParam(value = "状态参数", required = true) @RequestParam String state,
            HttpServletResponse response) throws IOException {
        try {
            log.info("WeChat callback received, code: {}, state: {}", code, state);
            UserTokenDTO userToken = oAuthService.wechatCallback(code, state);
            
            // 设置 Cookie
            addCookie(userToken.getToken(), response);
            
            // 重定向到前端页面，带上登录成功标识
            String redirectUrl = wechatFrontendCallbackUrl + "?success=true&token=" + userToken.getToken();
            response.sendRedirect(redirectUrl);
        } catch (Exception e) {
            log.error("WeChat callback failed", e);
            // 重定向到前端页面，带上错误信息
            String redirectUrl = wechatFrontendCallbackUrl + "?success=false&error=" + URLEncoder.encode(e.getMessage(), StandardCharsets.UTF_8.toString());
            response.sendRedirect(redirectUrl);
        }
    }

    @NoNeedLogin
    @PostMapping("/wechat/login")
    @ApiOperation(value = "微信登录（前端传递code）")
    @ApiVersion(group = ApiVersionConstant.V_300)
    public ResponseResult<UserTokenDTO> wechatLogin(
            @ApiParam(value = "授权码", required = true) @RequestParam String code,
            @ApiParam(value = "状态参数", required = true) @RequestParam String state,
            HttpServletResponse response) {
        log.info("WeChat login request, code: {}, state: {}", code, state);
        UserTokenDTO userToken = oAuthService.wechatCallback(code, state);
        
        // 设置 Cookie
        addCookie(userToken.getToken(), response);
        
        return ResponseResult.success(userToken);
    }

    // ==================== Common ====================

    /**
     * 增加cookie
     */
    private void addCookie(String token, HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(AuthSystemConstants.NS_ACCOUNT_SSO_COOKIE, token)
                .maxAge(TimeoutConstants.NS_SSO_TIMEOUT)
                .domain(domain)
                .path("/")
                .httpOnly(true)
                .build();
        response.addHeader("Set-Cookie", cookie.toString());
    }
}
