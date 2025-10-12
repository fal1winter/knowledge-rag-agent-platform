package com.liang.bbs.user.service.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 记录访问行为的注解
 * 用于AOP方式快速记录用户访问行为
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RecordAccess {
    
    /**
     * 目标类型
     */
    String targetType() default "";
    
    /**
     * 目标ID，支持SpEL表达式
     * 例如："#id" 或 "#article.id"
     */
    String targetId() default "";
    
    /**
     * 动作类型
     */
    String actionType() default "view";
    
    /**
     * 用户ID，支持SpEL表达式
     * 例如："#userId" 或 "#user.id"
     */
    String userId() default "";
    
    /**
     * 额外信息，支持SpEL表达式
     * 例如："#article.title"
     */
    String extraInfo() default "";
    
    /**
     * 是否异步记录
     */
    boolean async() default true;
}