package com.liang.bbs.user.service.mq;

import com.liang.bbs.common.mq.MessageHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 用户消息处理器
 * 
 */
@Slf4j
@Component
public class UserMessageHandler implements MessageHandler<UserMessage> {

    @Override
    public void handleMessage(UserMessage message) {
        String action = message.getAction();
        switch (action) {
            case "register":
                processUserRegister(message);
                break;
            case "login":
                processUserLogin(message);
                break;
            case "update":
                processUserUpdate(message);
                break;
            default:
                log.warn("未知的用户操作类型: {}", action);
        }
    }

    @Override
    public String getMessageType() {
        return "user";
    }

    /**
     * 处理用户注册逻辑
     */
    private void processUserRegister(UserMessage message) {
        // 这里可以添加具体的业务逻辑
        // 例如：发送欢迎邮件、记录注册日志、初始化用户数据等
        log.info("处理用户注册业务逻辑: 用户ID={}, 用户名={}", message.getUserId(), message.getUsername());
    }

    /**
     * 处理用户登录逻辑
     */
    private void processUserLogin(UserMessage message) {
        // 这里可以添加具体的业务逻辑
        // 例如：记录登录日志、更新最后登录时间、发送登录通知等
        log.info("处理用户登录业务逻辑: 用户ID={}, 用户名={}", message.getUserId(), message.getUsername());
    }

    /**
     * 处理用户更新逻辑
     */
    private void processUserUpdate(UserMessage message) {
        // 这里可以添加具体的业务逻辑
        // 例如：同步用户数据、发送更新通知等
        log.info("处理用户更新业务逻辑: 用户ID={}, 用户名={}", message.getUserId(), message.getUsername());
    }
}