package com.shine.xumis;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import com.shine.xumis.config.AppProperties;

/**
 * shine星心 外贸独立站展示模板 - 启动类
 * 纯 Java 技术栈：Spring Boot + Thymeleaf（前端视图）+ JPA（后端持久化）
 */
@SpringBootApplication
@EnableConfigurationProperties(AppProperties.class)
public class XumisApplication {
    public static void main(String[] args) {
        SpringApplication.run(XumisApplication.class, args);
    }
}
