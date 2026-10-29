# server · 网站服务端（Spring Boot）

shine星心 独立站的后端与前台视图，Java 17 + Spring Boot 3.2 + Thymeleaf + JPA + MySQL 8。

## 三条启动路径

```bash
# ① 一键脚本（推荐）：自动下载 JDK 17 / Maven 到 .tools/，构建并启动
chmod +x start.sh && ./start.sh

# ② 手动（本机已有 JDK 17 + Maven）
mvn spring-boot:run

# ③ Docker：在仓库根执行
docker compose up -d --build
```

Windows 双击 `start.bat`，或 `.\install.ps1 -Start`。

## 目录

| 路径 | 说明 |
|------|------|
| `src/main/java/com/shine/xumis/` | controller / service / repository / model / config / dto / integration |
| `src/main/resources/templates/` | Thymeleaf 页面（首页、商品、详情、购物车、结算、定制、询盘、后台） |
| `src/main/resources/static/` | css / js / PWA manifest / icon |
| `sql/init.sql` | 建库脚本（应用首次启动也会自动建表并播种） |
| `Dockerfile` | 多阶段构建（Maven 编译 → JRE 运行） |

## 运行产物（均不入库）

`.tools/`（JDK/Maven 缓存）、`target/`（jar）、`runtime/`（日志与 PID）、`uploads/`（客户与商品图片）、`data/`（H2 应急库）。

配置、生产部署与反向代理见仓库根的 [README](../README.md) 与 [docs/DEPLOYMENT.md](../docs/DEPLOYMENT.md)。
