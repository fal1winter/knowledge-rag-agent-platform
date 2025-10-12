package com.liang.bbs.user.service.config;

import com.liang.bbs.common.config.RestTemplateConfig;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.context.annotation.Configuration;

/**
 * 公共配置
 *
 */
@ImportAutoConfiguration(value = {RestTemplateConfig.class})
@Configuration
public class CommonConfig {
}
