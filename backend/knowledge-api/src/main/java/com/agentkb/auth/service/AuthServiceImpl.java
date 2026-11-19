package com.agentkb.auth.service;

import com.agentkb.auth.dto.LoginRequest;
import com.agentkb.auth.dto.LoginResponse;
import com.agentkb.auth.dto.UserInfo;
import com.agentkb.auth.entity.SysUser;
import com.agentkb.auth.mapper.SysUserMapper;
import com.agentkb.common.BusinessException;
import com.agentkb.security.JwtTokenProvider;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 账号密码登录、JWT 签发、用户信息查询。
 */
@Service
public class AuthServiceImpl implements AuthService {

    private final SysUserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;

    public AuthServiceImpl(SysUserMapper userMapper,
                           PasswordEncoder passwordEncoder,
                           JwtTokenProvider tokenProvider) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
    }

    @Override
    public LoginResponse login(LoginRequest request) {
        SysUser user = userMapper.selectOne(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getUsername, request.getUsername()));
        if (user == null) {
            throw BusinessException.unauthorized("用户名或密码错误");
        }
        if (!"ACTIVE".equalsIgnoreCase(user.getStatus())) {
            throw BusinessException.forbidden("账号已被禁用");
        }
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw BusinessException.unauthorized("用户名或密码错误");
        }
        return buildLoginResponse(user);
    }

    @Override
    public void logout() {
        // JWT 为无状态令牌，服务端仅需清理当前线程的安全上下文。
        SecurityContextHolder.clearContext();
    }

    @Override
    public LoginResponse refresh(String token) {
        if (token == null || token.isBlank()) {
            throw BusinessException.unauthorized("令牌不能为空");
        }
        if (!tokenProvider.validateToken(token)) {
            throw BusinessException.unauthorized("令牌无效或已过期");
        }
        Long userId = tokenProvider.getUserId(token);
        SysUser user = userId == null ? null : userMapper.selectById(userId);
        if (user == null) {
            throw BusinessException.unauthorized("用户不存在");
        }
        return buildLoginResponse(user);
    }

    @Override
    public UserInfo currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            throw BusinessException.unauthorized("未登录");
        }
        String username = authentication.getName();
        SysUser user = userMapper.selectOne(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getUsername, username));
        if (user == null) {
            throw BusinessException.unauthorized("用户不存在");
        }
        UserInfo info = new UserInfo();
        info.setUserId(user.getId());
        info.setUsername(user.getUsername());
        info.setDisplayName(user.getDisplayName());
        info.setEmail(user.getEmail());
        return info;
    }

    private LoginResponse buildLoginResponse(SysUser user) {
        LoginResponse response = new LoginResponse();
        response.setAccessToken(tokenProvider.generateToken(user.getId(), user.getUsername()));
        response.setTokenType("Bearer");
        response.setExpiresIn(tokenProvider.getExpiration() / 1000);
        response.setUserId(user.getId());
        response.setUsername(user.getUsername());
        response.setDisplayName(user.getDisplayName());
        return response;
    }
}
