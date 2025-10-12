package com.liang.bbs.user.facade.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 自动创建通知注解
 * 标记在方法或Controller上，执行后自动创建通知
 *
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface AutoCreateNoti {
    
    /**
     * 通知内容模板，支持SpEL表达式
     */
    String content() default "";
    
    /**
     * 通知类型
     */
    int type() default 1;
    
    /**
     * 发送者ID，支持SpEL表达式
     */
    String senderId() default "";
    
    /**
     * 接收者ID，支持SpEL表达式
     */
    String receiverId() default "";
    
    /**
     * 额外数据，支持SpEL表达式，JSON格式
     */
    String extra() default "{}";
    
    /**
     * 是否异步执行
     */
    boolean async() default true;
}