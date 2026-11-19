package com.agentkb.auth.controller;

import com.agentkb.auth.dto.CreateUserRequest;
import com.agentkb.auth.dto.UserInfo;
import com.agentkb.auth.entity.SysUser;
import com.agentkb.auth.mapper.SysUserMapper;
import com.agentkb.common.BusinessException;
import com.agentkb.common.R;
import com.agentkb.security.KbAccessService;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 用户管理（仅 ADMIN）：用于创建不同角色账号以验证知识库隔离。
 */
@Tag(name = "用户管理")
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private static final Set<String> ALLOWED_ROLES = Set.of("ADMIN", "KB_ADMIN", "EDITOR", "VIEWER");

    private final SysUserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final KbAccessService accessService;

    public UserController(SysUserMapper userMapper,
                          PasswordEncoder passwordEncoder,
                          KbAccessService accessService) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.accessService = accessService;
    }

    @Operation(summary = "创建用户并分配角色")
    @PostMapping
    @Transactional(rollbackFor = Exception.class)
    public R<UserInfo> create(@Valid @RequestBody CreateUserRequest request) {
        if (!accessService.isAdmin()) {
            throw BusinessException.forbidden("仅系统管理员可创建用户");
        }
        String roleCode = request.getRoleCode().toUpperCase();
        if (!ALLOWED_ROLES.contains(roleCode)) {
            throw BusinessException.badRequest("角色仅支持 ADMIN / KB_ADMIN / EDITOR / VIEWER");
        }
        Long exists = userMapper.selectCount(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getUsername, request.getUsername()));
        if (exists != null && exists > 0) {
            throw BusinessException.conflict("用户名已存在");
        }
        Long roleId = userMapper.selectRoleIdByCode(roleCode);
        if (roleId == null) {
            throw BusinessException.badRequest("角色不存在");
        }

        SysUser user = new SysUser();
        user.setId(IdWorker.getId());
        user.setUsername(request.getUsername());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setDisplayName(request.getDisplayName() == null ? request.getUsername() : request.getDisplayName());
        user.setEmail(request.getEmail());
        user.setStatus("ACTIVE");
        user.setDeleted(false);
        userMapper.insert(user);
        userMapper.insertUserRole(IdWorker.getId(), user.getId(), roleId);
        return R.ok(toInfo(user));
    }

    @Operation(summary = "用户列表")
    @GetMapping
    public R<List<UserInfo>> list() {
        if (!accessService.isAdmin()) {
            throw BusinessException.forbidden("仅系统管理员可查看用户列表");
        }
        return R.ok(userMapper.selectList(Wrappers.<SysUser>lambdaQuery()
                        .orderByDesc(SysUser::getCreatedAt))
                .stream()
                .map(this::toInfo)
                .collect(Collectors.toList()));
    }

    private UserInfo toInfo(SysUser user) {
        UserInfo info = new UserInfo();
        info.setUserId(user.getId());
        info.setUsername(user.getUsername());
        info.setDisplayName(user.getDisplayName());
        info.setEmail(user.getEmail());
        return info;
    }
}
