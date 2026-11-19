package com.agentkb.mcp.util;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * 从 Spring Security 上下文提取当前请求的 Bearer Token。
 *
 * <p>tokens 以字符串形式返回，在调用 WebClient 之前完成取值，
 * 因此不会被 reactor-netty 的 I/O 线程切换影响。
 *
 * <p>[SDK 版本校准] 若某版本 MCP SDK 将工具回调投递到独立线程，
 * SecurityContextHolder 可能为空；此时应改从 McpSyncServerExchange 的
 * transport context 中读取 HTTP Authorization 头作为兜底。
 */
@Component
public class BearerTokenResolver {

    /** 取当前访问令牌；未认证时返回 null。 */
    public String resolve() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return null;
        }
        if (authentication instanceof JwtAuthenticationToken jwtAuthentication) {
            return jwtAuthentication.getToken().getTokenValue();
        }
        if (authentication.getPrincipal() instanceof Jwt jwt) {
            return jwt.getTokenValue();
        }
        return null;
    }

    /** 工具调用必须使用已认证的令牌，否则不允许访问 knowledge-api。 */
    public String requireToken() {
        String token = resolve();
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("未检测到有效的 Bearer Token，MCP 请求必须通过 JWT 鉴权");
        }
        return token;
    }
}
