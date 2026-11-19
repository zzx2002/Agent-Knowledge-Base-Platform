package com.agentkb.auth.dto;

/** 管理员修改用户请求：角色、状态、密码按需传入。 */
public class UpdateUserRequest {

    private String roleCode;
    private String status;
    private String password;

    public String getRoleCode() { return roleCode; }
    public void setRoleCode(String roleCode) { this.roleCode = roleCode; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}
