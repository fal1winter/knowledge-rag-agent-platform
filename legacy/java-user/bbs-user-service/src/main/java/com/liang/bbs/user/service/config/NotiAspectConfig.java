package com.liang.bbs.user.service.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * 通知切面配置
 *
 */
@Configuration
@EnableAspectJAutoProxy
@EnableAsync
public class NotiAspectConfig {
}