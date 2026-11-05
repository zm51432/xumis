package com.shine.xumis.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 打开定时任务：站点自检（HealthCheckService）依赖它。
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
