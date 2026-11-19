package com.agentkb.audit.interceptor;

import com.agentkb.common.R;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/** 将统一响应码写入请求属性，供审计拦截器记录真实业务结果。 */
@RestControllerAdvice
public class AuditResultAdvice implements ResponseBodyAdvice<Object> {

    public static final String ATTR_SUCCESS = "audit_success";
    public static final String ATTR_CODE = "audit_code";
    public static final String ATTR_MESSAGE = "audit_message";

    @Override
    public boolean supports(MethodParameter returnType, Class converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
                                  Class selectedConverterType, ServerHttpRequest request,
                                  ServerHttpResponse response) {
        var attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes servletAttrs && body instanceof R<?> result) {
            servletAttrs.setAttribute(ATTR_SUCCESS, result.getCode() == 200, 0);
            servletAttrs.setAttribute(ATTR_CODE, result.getCode(), 0);
            servletAttrs.setAttribute(ATTR_MESSAGE, result.getMessage(), 0);
        }
        return body;
    }
}
