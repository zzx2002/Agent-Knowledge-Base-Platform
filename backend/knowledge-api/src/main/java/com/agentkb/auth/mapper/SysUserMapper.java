package com.agentkb.auth.mapper;

import com.agentkb.auth.dto.RoleOptionDTO;
import com.agentkb.auth.entity.SysUser;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 用户表 Mapper。
 */
@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {

    @Select("SELECT id FROM sys_role WHERE role_code = #{roleCode}")
    Long selectRoleIdByCode(@Param("roleCode") String roleCode);

    @Insert("INSERT INTO sys_user_role (id, user_id, role_id, created_at) " +
            "VALUES (#{id}, #{userId}, #{roleId}, CURRENT_TIMESTAMP)")
    int insertUserRole(@Param("id") Long id,
                       @Param("userId") Long userId,
                       @Param("roleId") Long roleId);

    @Select("SELECT r.role_code AS roleCode, r.role_name AS roleName " +
            "FROM sys_user_role ur JOIN sys_role r ON r.id = ur.role_id " +
            "WHERE ur.user_id = #{userId} ORDER BY r.id LIMIT 1")
    RoleOptionDTO selectRoleByUserId(@Param("userId") Long userId);

    @Select("SELECT role_code AS roleCode, role_name AS roleName FROM sys_role ORDER BY id")
    List<RoleOptionDTO> selectRoles();

    @Delete("DELETE FROM sys_user_role WHERE user_id = #{userId}")
    int deleteUserRoles(@Param("userId") Long userId);

    @Update("UPDATE sys_user SET updated_at = CURRENT_TIMESTAMP WHERE id = #{userId}")
    int touchUser(@Param("userId") Long userId);
}
