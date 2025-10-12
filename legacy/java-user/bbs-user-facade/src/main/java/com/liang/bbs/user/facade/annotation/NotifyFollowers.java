package com.liang.bbs.user.facade.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 通知关注者的注解
 * 
 * 在方法执行后，根据targetId和type获取所有关注该目标的用户，
 * 并为这些用户发送通知
 * 
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface NotifyFollowers {
    
    /**
     * 目标ID的SpEL表达式
     * 例如："#{#methodArgs['targetId']}" 或 "#{#returnValue.id}"
     */
    String targetId() default "";
    
    /**
     * 关注类型的SpEL表达式
     * 例如："#{#methodArgs['type']}" 或 "user"
     */
    String type() default "user";
    
    /**
     * 通知内容的SpEL表达式
     */
    String content() default "";
    
    /**
     * 通知类型的SpEL表达式
     */
    String notificationType() default "1";
    
    /**
     * 发送者ID的SpEL表达式
     * 如果为空，则使用当前登录用户ID
     */
    String senderId() default "";
    
    /**
     * 额外数据的SpEL表达式
     */
    String extra() default "{}";
    
    /**
     * 是否异步执行
     */
    boolean async() default true;
}