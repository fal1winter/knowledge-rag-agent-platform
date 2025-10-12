package com.liang.bbs.rest.controller;

import com.bbs.common.response.PageResult;
import com.liang.bbs.common.enums.ResponseCode;
import com.liang.bbs.common.web.basic.ResponseResult;
import com.liang.bbs.rest.config.login.NoNeedLogin;
import com.liang.bbs.user.facade.dto.user.UserCreditLogDTO;
import com.liang.bbs.user.facade.dto.user.UserDTO;
import com.liang.bbs.user.facade.dto.user.UserSsoDTO;
import com.liang.bbs.user.facade.server.UserCreditService;
import com.liang.bbs.user.facade.server.UserService;
import com.liang.bbs.user.facade.utils.UserContextUtils;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.Reference;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 用户积分Controller
 */
@Api(tags = "用户积分")
@RestController
@RequestMapping("/user/credits")
@Slf4j
public class UserCreditController {

    @Reference
    private UserCreditService creditService;

    @Reference
    private UserService userService;

    @ApiOperation("获取当前用户积分")
    @GetMapping("")
    public ResponseResult<Map<String, Object>> getMyCredits() {
        Long userId = getCurrentUserId();
        if (userId == null) {
            return ResponseResult.build(ResponseCode.NOT_LOGIN, null);
        }

        Integer credits = creditService.getCredits(userId.intValue());
        boolean isVip = creditService.isVip(userId.intValue());

        Map<String, Object> result = new HashMap<>();
        result.put("credits", credits);
        result.put("isVip", isVip);

        // 如果是VIP，获取VIP过期时间
        if (isVip) {
            try {
                UserDTO user = userService.getById(userId.intValue());
                if (user != null && user.getVipExpireTime() != null) {
                    result.put("vipExpireTime", user.getVipExpireTime());
                }
            } catch (Exception e) {
                log.warn("get vip expire time failed: userId={}", userId, e);
            }
        }

        return ResponseResult.success(result);
    }

    @ApiOperation("获取积分流水")
    @GetMapping("/logs")
    public ResponseResult<PageResult<UserCreditLogDTO>> getCreditLogs(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        Long userId = getCurrentUserId();
        if (userId == null) {
            return ResponseResult.build(ResponseCode.NOT_LOGIN, null);
        }

        PageResult<UserCreditLogDTO> result = creditService.getCreditLogs(userId.intValue(), pageNum, pageSize);
        return ResponseResult.success(result);
    }

    @ApiOperation("管理员充值积分")
    @PostMapping("/admin/recharge")
    public ResponseResult<Void> adminRecharge(@RequestBody RechargeRequest request) {
        Long operatorId = getCurrentUserId();
        if (operatorId == null) {
            return ResponseResult.build(ResponseCode.NOT_LOGIN, null);
        }

        // TODO: 检查操作人是否为管理员
        // 这里暂时不做权限检查，后续可以添加

        if (request.getUserId() == null || request.getAmount() == null || request.getAmount() <= 0) {
            return ResponseResult.build(ResponseCode.PARAM_ERROR, null);
        }

        boolean success = creditService.addCredits(
                request.getUserId(),
                request.getAmount(),
                "ADMIN_ADD",
                request.getRemark(),
                operatorId.intValue(),
                null
        );

        if (success) {
            return ResponseResult.success(null);
        } else {
            return ResponseResult.build(ResponseCode.ERROR, null);
        }
    }

    @ApiOperation("查询指定用户积分（管理员）")
    @GetMapping("/admin/{userId}")
    @NoNeedLogin  // 临时开放，后续添加管理员权限检查
    public ResponseResult<Map<String, Object>> getUserCredits(@PathVariable Integer userId) {
        Integer credits = creditService.getCredits(userId);
        boolean isVip = creditService.isVip(userId);

        Map<String, Object> result = new HashMap<>();
        result.put("userId", userId);
        result.put("credits", credits);
        result.put("isVip", isVip);

        // 获取用户基本信息
        try {
            UserDTO user = userService.getById(userId);
            if (user != null) {
                result.put("userName", user.getName());
                if (user.getVipExpireTime() != null) {
                    result.put("vipExpireTime", user.getVipExpireTime());
                }
            }
        } catch (Exception e) {
            log.warn("get user info failed: userId={}", userId, e);
        }

        return ResponseResult.success(result);
    }

    private Long getCurrentUserId() {
        try {
            UserSsoDTO user = UserContextUtils.currentUser();
            return user != null ? Long.valueOf(user.getUserId()) : null;
        } catch (Exception e) {
            return null;
        }
    }

    @Data
    public static class RechargeRequest {
        private Integer userId;
        private Integer amount;
        private String remark;
    }
}
