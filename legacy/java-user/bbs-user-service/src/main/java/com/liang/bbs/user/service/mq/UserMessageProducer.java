package com.liang.bbs.user.service.mq;

import com.liang.bbs.common.constant.RabbitMQConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 用户消息生产者
 * 
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserMessageProducer {

    private final RabbitTemplate rabbitTemplate;

    /**
     * 发送用户注册消息
     */
    public void sendUserRegisterMessage(Long userId, String username, String ipAddress, String userAgent) {
        UserMessage message = new UserMessage();
        message.setUserId(userId);
        message.setUsername(username);
        message.setAction("register");
        message.setContent("用户注册成功");
        message.setCreateTime(LocalDateTime.now());
        message.setIpAddress(ipAddress);
        message.setUserAgent(userAgent);

        rabbitTemplate.convertAndSend(
                RabbitMQConstants.USER_EXCHANGE,
                RabbitMQConstants.USER_REGISTER_ROUTING_KEY,
                message
        );
        log.info("发送用户注册消息: {}", message);
    }

    /**
     * 发送用户登录消息
     */
    public void sendUserLoginMessage(Long userId, String username, String ipAddress, String userAgent) {
        UserMessage message = new UserMessage();
        message.setUserId(userId);
        message.setUsername(username);
        message.setAction("login");
        message.setContent("用户登录成功");
        message.setCreateTime(LocalDateTime.now());
        message.setIpAddress(ipAddress);
        message.setUserAgent(userAgent);

        rabbitTemplate.convertAndSend(
                RabbitMQConstants.USER_EXCHANGE,
                RabbitMQConstants.USER_LOGIN_ROUTING_KEY,
                message
        );
        log.info("发送用户登录消息: {}", message);
    }

    /**
     * 发送用户更新消息
     */
    public void sendUserUpdateMessage(Long userId, String username, String ipAddress, String userAgent) {
        UserMessage message = new UserMessage();
        message.setUserId(userId);
        message.setUsername(username);
        message.setAction("update");
        message.setContent("用户信息更新成功");
        message.setCreateTime(LocalDateTime.now());
        message.setIpAddress(ipAddress);
        message.setUserAgent(userAgent);

        rabbitTemplate.convertAndSend(
                RabbitMQConstants.USER_EXCHANGE,
                RabbitMQConstants.USER_UPDATE_ROUTING_KEY,
                message
        );
        log.info("发送用户更新消息: {}", message);
    }
}