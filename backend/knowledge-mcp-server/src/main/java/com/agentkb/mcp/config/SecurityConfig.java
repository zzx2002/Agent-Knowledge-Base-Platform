package com.agentkb.mcp.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

/**
 * 资源服务器安全配置（架构文档 8.2 / 8.3）。
 *
 * <p>MCP 请求（/mcp）必须携带 Bearer Token；/actuator/health 允许匿名访问以便容器探针使用。
 * 解析出的 JWT 由 {@code BearerTokenResolver} 从 SecurityContext 取出，再透传到 knowledge-api。
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // MCP Streamable HTTP 是无状态 API，关闭 CSRF 与 Session
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // 容器健康检查不需要鉴权
                        .requestMatchers("/actuator/health").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));
        return http.build();
    }

    /**
     * 使用 HS256 对称密钥校验访问令牌，同时校验 iss 声明。
     *
     * <p>[SDK 版本校准] 本 Bean 与 MCP SDK 版本无关；若改用 OIDC issuer 自动发现，
     * 删除本 Bean 并在 application.yml 中开启 spring.security.oauth2.resourceserver.jwt.issuer-uri。
     */
    @Bean
    public JwtDecoder jwtDecoder(
            @Value("${kb.security.jwt.secret}") String secret,
            @Value("${kb.security.jwt.issuer}") String issuer) {

        // [安全] 生产环境密钥长度应 >= 32 字节，通过 KB_JWT_SECRET 环境变量注入
        SecretKey key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");

        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        // 校验 iss + exp/nbf
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(issuer));
        return decoder;
    }
}
