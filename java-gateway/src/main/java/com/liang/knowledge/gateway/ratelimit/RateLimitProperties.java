package com.liang.knowledge.gateway.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 限流配置参数。
 * <p>
 * 通过 application.yml 注入：
 * <pre>
 * ratelimit:
 *   user-qpm: 30
 *   tenant-qpm: 200
 *   window-seconds: 60
 * </pre>
 */
@Component
@ConfigurationProperties(prefix = "ratelimit")
public class RateLimitProperties {

    /** 单用户每分钟最大请求数 */
    private int userQpm = 30;

    /** 单租户每分钟最大请求数 */
    private int tenantQpm = 200;

    /** 滑动窗口大小（秒） */
    private int windowSeconds = 60;

    public int getUserQpm() {
        return userQpm;
    }

    public void setUserQpm(int userQpm) {
        this.userQpm = userQpm;
    }

    public int getTenantQpm() {
        return tenantQpm;
    }

    public void setTenantQpm(int tenantQpm) {
        this.tenantQpm = tenantQpm;
    }

    public int getWindowSeconds() {
        return windowSeconds;
    }

    public void setWindowSeconds(int windowSeconds) {
        this.windowSeconds = windowSeconds;
    }
}
