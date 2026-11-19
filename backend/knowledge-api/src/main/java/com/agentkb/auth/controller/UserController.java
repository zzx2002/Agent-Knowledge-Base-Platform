package com.agentkb.auth.controller;

import com.agentkb.auth.dto.CreateUserRequest;
import com.agentkb.auth.dto.RoleOptionDTO;
import com.agentkb.auth.dto.UpdateUserRequest;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** 用户与角色管理（仅 ADMIN）。 */
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
        requireAdmin();
        String roleCode = normalizeRole(request.getRoleCode());
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
        requireAdmin();
        return R.ok(userMapper.selectList(Wrappers.<SysUser>lambdaQuery()
                        .orderByDesc(SysUser::getCreatedAt))
                .stream().map(this::toInfo).collect(Collectors.toList()));
    }

    @Operation(summary = "角色选项")
    @GetMapping("/roles")
    public R<List<RoleOptionDTO>> roles() {
        requireAdmin();
        return R.ok(userMapper.selectRoles());
    }

    @Operation(summary = "修改用户角色、状态或密码")
    @PutMapping("/{id}")
    @Transactional(rollbackFor = Exception.class)
    public R<UserInfo> update(@PathVariable Long id, @RequestBody UpdateUserRequest request) {
        requireAdmin();
        SysUser user = requireUser(id);
        preventSelfChange(user);

        if (request.getRoleCode() != null && !request.getRoleCode().isBlank()) {
            String roleCode = normalizeRole(request.getRoleCode());
            Long roleId = userMapper.selectRoleIdByCode(roleCode);
            if (roleId == null) {
                throw BusinessException.badRequest("角色不存在");
            }
            userMapper.deleteUserRoles(id);
            userMapper.insertUserRole(IdWorker.getId(), id, roleId);
        }
        if (request.getStatus() != null && !request.getStatus().isBlank()) {
            String status = request.getStatus().toUpperCase();
            if (!Set.of("ACTIVE", "DISABLED").contains(status)) {
                throw BusinessException.badRequest("状态仅支持 ACTIVE / DISABLED");
            }
            user.setStatus(status);
        }
        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            if (request.getPassword().length() < 6) {
                throw BusinessException.badRequest("密码长度不能少于 6 位");
            }
            user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        }
        userMapper.updateById(user);
        return R.ok(toInfo(user));
    }

    @Operation(summary = "删除用户")
    @DeleteMapping("/{id}")
    @Transactional(rollbackFor = Exception.class)
    public R<Void> delete(@PathVariable Long id) {
        requireAdmin();
        SysUser user = requireUser(id);
        preventSelfChange(user);
        // 删除用户时同时置为已禁用，保留可审计状态
        user.setStatus("DISABLED");
        userMapper.updateById(user);
        userMapper.deleteById(id);
        userMapper.deleteUserRoles(id);
        return R.ok();
    }

    private void requireAdmin() {
        if (!accessService.isAdmin()) {
            throw BusinessException.forbidden("仅系统管理员可执行该操作");
        }
    }

    private SysUser requireUser(Long id) {
        SysUser user = userMapper.selectById(id);
        if (user == null) {
            throw BusinessException.notFound("用户不存在");
        }
        return user;
    }

    private void preventSelfChange(SysUser user) {
        if (user.getUsername().equals(accessService.currentUsername())) {
            throw BusinessException.badRequest("不能修改或删除当前登录账号");
        }
    }

    private String normalizeRole(String roleCode) {
        String code = roleCode == null ? "" : roleCode.toUpperCase();
        if (!ALLOWED_ROLES.contains(code)) {
            throw BusinessException.badRequest("角色仅支持 ADMIN / KB_ADMIN / EDITOR / VIEWER");
        }
        return code;
    }

    private UserInfo toInfo(SysUser user) {
        UserInfo info = new UserInfo();
        info.setUserId(user.getId());
        info.setUsername(user.getUsername());
        info.setDisplayName(user.getDisplayName());
        info.setEmail(user.getEmail());
        info.setPhone(user.getPhone());
        info.setStatus(user.getStatus());
        info.setCreatedAt(user.getCreatedAt());
        RoleOptionDTO role = userMapper.selectRoleByUserId(user.getId());
        if (role != null) {
            info.setRoleCode(role.getRoleCode());
            info.setRoleName(role.getRoleName());
        }
        return info;
    }
}
