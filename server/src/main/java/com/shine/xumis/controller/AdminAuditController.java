package com.shine.xumis.controller;

import com.shine.xumis.config.AdminAuthInterceptor;
import com.shine.xumis.repository.AdminAuditLogRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/** 后台操作审计日志（阶段4）。 */
@Controller
public class AdminAuditController {
    private final AdminAuditLogRepository repo;
    public AdminAuditController(AdminAuditLogRepository repo) { this.repo = repo; }

    @GetMapping("/admin/audit")
    public String list(HttpSession session, Model model) {
        if (AdminAuthInterceptor.current(session) == null) return "redirect:/admin/login";
        model.addAttribute("logs", repo.findTop300ByOrderByIdDesc());
        return "admin-audit";
    }
}
