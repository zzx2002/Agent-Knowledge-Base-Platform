package com.agentkb.auth.dto;

/** 角色下拉选项。 */
public class RoleOptionDTO {

    private String roleCode;
    private String roleName;

    public RoleOptionDTO() {
    }

    public RoleOptionDTO(String roleCode, String roleName) {
        this.roleCode = roleCode;
        this.roleName = roleName;
    }

    public String getRoleCode() { return roleCode; }
    public void setRoleCode(String roleCode) { this.roleCode = roleCode; }
    public String getRoleName() { return roleName; }
    public void setRoleName(String roleName) { this.roleName = roleName; }
}
