package com.agentkb.security;

import com.agentkb.auth.entity.SysUser;
import com.agentkb.auth.mapper.SysUserMapper;
import com.agentkb.common.BusinessException;
import com.agentkb.knowledge.entity.KnowledgeBase;
import com.agentkb.knowledge.mapper.KnowledgeBaseMapper;
import com.agentkb.security.mapper.AccessMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * 知识库级数据隔离与权限判定。
 *
 * <p>规则：
 * <ol>
 *   <li>ADMIN / KB_ADMIN 拥有全部知识库权限；</li>
 *   <li>知识库 owner 拥有该库的全部权限；</li>
 *   <li>其他用户只能获得 kb_permission 中显式授予且 granted=true 的权限；</li>
 *   <li>角色授权（subject_type=ROLE）按用户所属角色生效， kb_id 为 NULL 表示全局授权。</li>
 * </ol>
 */
@Service
public class KbAccessService {

    public static final String PERM_READ = "READ";
    public static final String PERM_WRITE = "WRITE";
    public static final String PERM_DELETE = "DELETE";
    public static final String PERM_ADMIN = "ADMIN";

    private static final String ROLE_ADMIN = "ADMIN";
    private static final String ROLE_KB_ADMIN = "KB_ADMIN";
    private static final String ROLE_EDITOR = "EDITOR";

    private final SysUserMapper userMapper;
    private final AccessMapper accessMapper;
    private final KnowledgeBaseMapper knowledgeBaseMapper;

    public KbAccessService(SysUserMapper userMapper,
                           AccessMapper accessMapper,
                           KnowledgeBaseMapper knowledgeBaseMapper) {
        this.userMapper = userMapper;
        this.accessMapper = accessMapper;
        this.knowledgeBaseMapper = knowledgeBaseMapper;
    }

    /** 当前登录用户 ID；未认证时抛 401。 */
    public Long currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            throw BusinessException.unauthorized("未登录");
        }
        return requireUserByUsername(authentication.getName()).getId();
    }

    /** 当前登录用户名；未认证时抛 401。 */
    public String currentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            throw BusinessException.unauthorized("未登录");
        }
        return authentication.getName();
    }

    private SysUser requireUserByUsername(String username) {
        SysUser user = userMapper.selectOne(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getUsername, username));
        if (user == null || !"ACTIVE".equalsIgnoreCase(user.getStatus())) {
            throw BusinessException.unauthorized("用户不存在或已被禁用");
        }
        return user;
    }

    public List<String> currentRoleCodes() {
        Long userId = currentUserId();
        List<String> roles = accessMapper.selectRoleCodes(userId);
        return roles == null ? Collections.emptyList() : roles;
    }

    public boolean isAdmin() {
        return currentRoleCodes().contains(ROLE_ADMIN);
    }

    public boolean isGlobalAdmin() {
        List<String> roles = currentRoleCodes();
        return roles.contains(ROLE_ADMIN) || roles.contains(ROLE_KB_ADMIN);
    }

    /** 是否允许创建知识库：管理员、知识库管理员、编辑者。 */
    public boolean canCreateKnowledgeBase() {
        List<String> roles = currentRoleCodes();
        return roles.contains(ROLE_ADMIN) || roles.contains(ROLE_KB_ADMIN) || roles.contains(ROLE_EDITOR);
    }

    public void requireCanCreateKnowledgeBase() {
        if (!canCreateKnowledgeBase()) {
            throw BusinessException.forbidden("当前角色无权创建知识库");
        }
    }

    /** 判断当前用户对指定知识库是否拥有某项权限。 */
    public boolean hasPermission(Long kbId, String permission) {
        if (kbId == null) {
            return false;
        }
        KnowledgeBase kb = knowledgeBaseMapper.selectById(kbId);
        if (kb == null) {
            throw BusinessException.notFound("知识库不存在");
        }
        if (isGlobalAdmin()) {
            return true;
        }
        Long userId = currentUserId();
        if (kb.getOwnerId() != null && kb.getOwnerId().equals(userId)) {
            return true;
        }
        if (accessMapper.countUserGrant(userId, kbId, permission) > 0) {
            return true;
        }
        for (String role : currentRoleCodes()) {
            if (accessMapper.countRoleGrant(role, kbId, permission) > 0) {
                return true;
            }
        }
        return false;
    }

    public void require(Long kbId, String permission) {
        if (!hasPermission(kbId, permission)) {
            throw BusinessException.forbidden("无权访问该知识库");
        }
    }

    /**
     * 当前用户可读的知识库 ID 列表。
     * 返回 null 表示不做过滤（全局管理员或拥有全局 READ 授权）。
     */
    public List<Long> readableKbIds() {
        Long userId = currentUserId();
        if (isGlobalAdmin() || accessMapper.countGlobalReadGrant(userId) > 0) {
            return null;
        }
        return accessMapper.selectReadableKbIds(userId);
    }
}
