package com.shine.xumis.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.i18n.LocaleChangeInterceptor;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Web 配置：
 * 1) 将上传目录映射为静态资源 /files/**
 * 2) 注册语言切换拦截器（URL 参数 ?lang=en / ?lang=zh）
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AdminAuthInterceptor adminAuthInterceptor;
    private final CsrfInterceptor csrfInterceptor;

    @Value("${app.upload-dir:./uploads}")
    private String uploadDir;

    @Value("${app.upload-url:/files/}")
    private String uploadUrl;

    public WebConfig(AdminAuthInterceptor adminAuthInterceptor, CsrfInterceptor csrfInterceptor) {
        this.adminAuthInterceptor = adminAuthInterceptor;
        this.csrfInterceptor = csrfInterceptor;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path root = Paths.get(uploadDir).toAbsolutePath().normalize();
        String location = root.toUri().toString();
        registry.addResourceHandler(uploadUrl + "**")
                .addResourceLocations(location);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        LocaleChangeInterceptor interceptor = new LocaleChangeInterceptor();
        interceptor.setParamName("lang");
        registry.addInterceptor(interceptor);

        // CSRF：所有写操作必须带会话令牌（/api/** 走 API Key，不拦截）
        registry.addInterceptor(csrfInterceptor);

        // 后台必须登录（登录/登出页自身放行）
        registry.addInterceptor(adminAuthInterceptor)
                .addPathPatterns("/admin/**")
                .excludePathPatterns("/admin/login", "/admin/logout");
    }
}
