package com.shine.xumis.config;

import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.CookieLocaleResolver;

import java.util.Locale;

/**
 * 国际化配置：中英文切换
 */
@Configuration
public class LocaleConfig {

    @Bean
    public MessageSource messageSource() {
        ReloadableResourceBundleMessageSource ms = new ReloadableResourceBundleMessageSource();
        ms.setBasename("classpath:messages");
        ms.setDefaultEncoding("UTF-8");
        // 关键：中文曾经在服务器上切不回来，根因就是这一行。
        // 默认 fallbackToSystemLocale=true，当找不到 messages_zh 时会回落到「JVM 系统语言」；
        // 服务器 JVM 是 en_US，于是请求 zh 反而加载到 messages_en，页面变英文。
        // 关掉它，并显式声明兜底中文（基础文件 messages.properties 即中文）。
        ms.setFallbackToSystemLocale(false);
        ms.setDefaultLocale(Locale.CHINESE);
        return ms;
    }

    @Bean
    public LocaleResolver localeResolver() {
        CookieLocaleResolver resolver = new CookieLocaleResolver("shine_locale");
        resolver.setDefaultLocale(Locale.CHINESE);
        // 全站生效 + 记住一年，避免"切回中文却被重置"的问题
        resolver.setCookiePath("/");
        resolver.setCookieMaxAge(60 * 60 * 24 * 365);
        resolver.setCookieHttpOnly(true);   // 语言 Cookie 不暴露给 JS
        return resolver;
    }
}
