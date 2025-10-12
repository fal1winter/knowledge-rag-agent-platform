package com.liang.bbs.user.service.config;

import com.liang.bbs.common.constant.RabbitMQConstants;
import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ配置类 - 用户服务
 * 声明所有用户相关的队列、交换机和绑定关系
 * 
 */
@Configuration
public class RabbitMQConfig {

    /**
     * 用户交换机
     */
    @Bean
    public TopicExchange userExchange() {
        return new TopicExchange(RabbitMQConstants.USER_EXCHANGE, true, false);
    }

    /**
     * 用户注册队列
     */
    @Bean
    public Queue userRegisterQueue() {
        return QueueBuilder.durable(RabbitMQConstants.USER_REGISTER_QUEUE)
                .build();
    }

    /**
     * 用户登录队列
     */
    @Bean
    public Queue userLoginQueue() {
        return QueueBuilder.durable(RabbitMQConstants.USER_LOGIN_QUEUE)
                .build();
    }

    /**
     * 用户更新队列
     */
    @Bean
    public Queue userUpdateQueue() {
        return QueueBuilder.durable(RabbitMQConstants.USER_UPDATE_QUEUE)
                .build();
    }

    /**
     * 用户死信队列
     */
    @Bean
    public Queue userDlxQueue() {
        return QueueBuilder.durable("user.dlx.queue")
                .build();
    }

    /**
     * 死信交换机
     */
    @Bean
    public TopicExchange dlxExchange() {
        return new TopicExchange(RabbitMQConstants.DLX_EXCHANGE, true, false);
    }

    /**
     * 用户注册队列绑定到交换机
     */
    @Bean
    public Binding userRegisterBinding() {
        return BindingBuilder
                .bind(userRegisterQueue())
                .to(userExchange())
                .with(RabbitMQConstants.USER_REGISTER_ROUTING_KEY);
    }

    /**
     * 用户登录队列绑定到交换机
     */
    @Bean
    public Binding userLoginBinding() {
        return BindingBuilder
                .bind(userLoginQueue())
                .to(userExchange())
                .with(RabbitMQConstants.USER_LOGIN_ROUTING_KEY);
    }

    /**
     * 用户更新队列绑定到交换机
     */
    @Bean
    public Binding userUpdateBinding() {
        return BindingBuilder
                .bind(userUpdateQueue())
                .to(userExchange())
                .with(RabbitMQConstants.USER_UPDATE_ROUTING_KEY);
    }

    /**
     * 用户死信队列绑定到死信交换机
     */
    @Bean
    public Binding userDlxBinding() {
        return BindingBuilder
                .bind(userDlxQueue())
                .to(dlxExchange())
                .with("user.dlx.routing.key");
    }
}