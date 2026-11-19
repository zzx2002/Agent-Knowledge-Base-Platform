package com.agentkb.auth.mapper;

import com.agentkb.auth.entity.SysUser;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

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
}
