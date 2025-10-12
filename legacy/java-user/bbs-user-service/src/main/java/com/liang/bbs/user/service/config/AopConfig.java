package com.liang.bbs.user.service.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * AOP配置类
 * 启用AspectJ自动代理和异步执行
 */
@Configuration
@EnableAspectJAutoProxy(proxyTargetClass = true)
@EnableAsync
public class AopConfig {
}