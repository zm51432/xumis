package com.shine.xumis.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * 站点自检记录：定时任务每轮把关键页面/接口的探测结果落库，后台可看历史。
 */
@Entity
@Table(name = "site_check")
public class SiteCheck {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 被检测对象，例如 首页 / 商品列表 / 商品详情 / sitemap.xml / 数据库 */
    @Column(length = 120)
    private String target;

    /** 请求路径，便于排查 */
    @Column(length = 200)
    private String path;

    private boolean ok;

    private int statusCode;

    /** 耗时（毫秒） */
    private int costMs;

    @Column(length = 255)
    private String message;

    private LocalDateTime checkedAt = LocalDateTime.now();

    public SiteCheck() {
    }

    public SiteCheck(String target, String path, boolean ok, int statusCode, int costMs, String message) {
        this.target = target;
        this.path = path;
        this.ok = ok;
        this.statusCode = statusCode;
        this.costMs = costMs;
        this.message = message;
        this.checkedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public String getTarget() { return target; }
    public void setTarget(String target) { this.target = target; }
    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }
    public boolean isOk() { return ok; }
    public void setOk(boolean ok) { this.ok = ok; }
    public int getStatusCode() { return statusCode; }
    public void setStatusCode(int statusCode) { this.statusCode = statusCode; }
    public int getCostMs() { return costMs; }
    public void setCostMs(int costMs) { this.costMs = costMs; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public LocalDateTime getCheckedAt() { return checkedAt; }
    public void setCheckedAt(LocalDateTime checkedAt) { this.checkedAt = checkedAt; }
}
