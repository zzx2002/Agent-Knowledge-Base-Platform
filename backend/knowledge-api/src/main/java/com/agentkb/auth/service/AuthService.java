package com.agentkb.auth.service;

import com.agentkb.auth.dto.LoginRequest;
import com.agentkb.auth.dto.LoginResponse;
import com.agentkb.auth.dto.UserInfo;

/**
 * 认证服务接口。
 */
public interface AuthService {

    LoginResponse login(LoginRequest request);

    void logout();

    LoginResponse refresh(String token);

    UserInfo currentUser();
}
