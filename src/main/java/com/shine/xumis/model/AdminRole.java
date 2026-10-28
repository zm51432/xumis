package com.shine.xumis.model;

/**
 * 后台管理员角色：
 * OWNER    全权限（含管理员账号管理、商品增删改）
 * OPERATOR 运营（商品/订单/内容维护，不能管理管理员账号）
 * VIEWER   只读（查看数据，不能做任何修改）
 */
public enum AdminRole {
    OWNER("站长/超级管理员"),
    OPERATOR("运营"),
    VIEWER("只读");

    private final String label;

    AdminRole(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
