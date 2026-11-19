package com.agentkb.auth.controller;

import com.agentkb.auth.dto.LoginRequest;
import com.agentkb.auth.dto.LoginResponse;
import com.agentkb.auth.dto.UserInfo;
import com.agentkb.auth.service.AuthService;
import com.agentkb.common.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口：/api/v1/auth/*
 */
@Tag(name = "认证接口")
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @Operation(summary = "用户登录")
    @PostMapping("/login")
    public R<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return R.ok(authService.login(request));
    }

    @Operation(summary = "用户退出")
    @PostMapping("/logout")
    public R<Void> logout() {
        authService.logout();
        return R.ok();
    }

    @Operation(summary = "刷新 Token")
    @PostMapping("/refresh")
    public R<LoginResponse> refresh(@RequestHeader("Authorization") String authorization) {
        String token = authorization.startsWith("Bearer ")
                ? authorization.substring(7)
                : authorization;
        return R.ok(authService.refresh(token));
    }

    @Operation(summary = "获取当前用户信息")
    @GetMapping("/me")
    public R<UserInfo> me() {
        return R.ok(authService.currentUser());
    }
}
