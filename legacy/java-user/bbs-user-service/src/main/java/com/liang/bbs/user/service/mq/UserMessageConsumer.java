package com.liang.bbs.user.service.mq;

import com.liang.bbs.common.mq.MessageHandler;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 用户消息消费者
 * 
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserMessageConsumer {

    private final UserMessageHandler userMessageHandler;

    /**
     * 监听用户注册队列
     */
    @RabbitListener(queues = "user.register.queue")
    public void handleUserRegisterMessage(UserMessage message, Channel channel, Message mqMessage) throws IOException {
        try {
            log.info("接收到用户注册消息: {}", message);
            
            // 使用注入的处理器处理消息
            userMessageHandler.handleMessage(message);
            
            // 手动确认消息
            channel.basicAck(mqMessage.getMessageProperties().getDeliveryTag(), false);
            log.info("用户注册消息处理完成: {}", message.getUserId());
        } catch (Exception e) {
            log.error("处理用户注册消息失败: {}", message, e);
            // 拒绝消息并重新入队
            channel.basicNack(mqMessage.getMessageProperties().getDeliveryTag(), false, true);
        }
    }

    /**
     * 监听用户登录队列
     */
    @RabbitListener(queues = "user.login.queue")
    public void handleUserLoginMessage(UserMessage message, Channel channel, Message mqMessage) throws IOException {
        try {
            log.info("接收到用户登录消息: {}", message);
            
            // 使用注入的处理器处理消息
            userMessageHandler.handleMessage(message);
            
            // 手动确认消息
            channel.basicAck(mqMessage.getMessageProperties().getDeliveryTag(), false);
            log.info("用户登录消息处理完成: {}", message.getUserId());
        } catch (Exception e) {
            log.error("处理用户登录消息失败: {}", message, e);
            // 拒绝消息并重新入队
            channel.basicNack(mqMessage.getMessageProperties().getDeliveryTag(), false, true);
        }
    }

    /**
     * 监听用户更新队列
     */
    @RabbitListener(queues = "user.update.queue")
    public void handleUserUpdateMessage(UserMessage message, Channel channel, Message mqMessage) throws IOException {
        try {
            log.info("接收到用户更新消息: {}", message);
            
            // 使用注入的处理器处理消息
            userMessageHandler.handleMessage(message);
            
            // 手动确认消息
            channel.basicAck(mqMessage.getMessageProperties().getDeliveryTag(), false);
            log.info("用户更新消息处理完成: {}", message.getUserId());
        } catch (Exception e) {
            log.error("处理用户更新消息失败: {}", message, e);
            // 拒绝消息并重新入队
            channel.basicNack(mqMessage.getMessageProperties().getDeliveryTag(), false, true);
        }
    }
}