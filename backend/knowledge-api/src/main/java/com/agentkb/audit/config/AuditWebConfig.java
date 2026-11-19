package com.agentkb.audit.config;

import com.agentkb.audit.interceptor.AuditLogInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** 注册审计拦截器。 */
@Configuration
public class AuditWebConfig implements WebMvcConfigurer {

    private final AuditLogInterceptor interceptor;

    public AuditWebConfig(AuditLogInterceptor interceptor) {
        this.interceptor = interceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(interceptor).addPathPatterns("/api/v1/**");
    }
}
