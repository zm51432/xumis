# 部署指南（自建服务器）· shine星心 外贸独立站

> **本文档中所有** `example.com`、`https://<你的域名>`、`<服务器公网IP>`、`<DB_PASSWORD>` **都是占位符**，请替换成你自己的值。
> 本文**不包含任何具体的服务器地址或域名**，你照着把占位符换成自己的即可。

---

## 1. 项目简介

**shine星心** 是一个跨境 B2C 外贸独立站模板，主营 **饰品 / 手工品**，并支持 **客户来图定制**。整套系统由三部分组成，三者通过同一条业务域名协同工作：

| 组件 | 目录 | 技术栈 | 作用 |
|------|------|--------|------|
| 网站服务端 | `server/` | Java 17 + Spring Boot 3.2（JPA + Thymeleaf + REST API） | 前台商城 + 后台管理 + 手机端 API |
| 安卓客户端 | `android/` | Kotlin（ViewBinding），minSdk 24，适配 Android 13 | 移动端浏览 / 搜索 / 下单 |
| APK 发布服务 | `deploy/ota/` | Python | 版本发布与 OTA 推送，与站点共用域名 |

### 1.1 网站端功能一览
- 🏠 **首页**：品牌故事 + 精选商品 + 来图定制入口，清新视觉风格。
- 🛍 **商品展示**：列表 + 分类筛选（饰品 / 手工品 / 其他）+ 关键词搜索 / 排序 / 分页 + 详情页。
- 🛒 **购物车与订单**：会话购物车（加 / 改数量 / 删除）、结算生成订单、订单成功页。
- 💱 **多币种**：USD / CNY / EUR 一键切换（Cookie 记忆），按汇率换算。
- ⭐ **商品评价**：用户提交评分与评价，后台审核后展示。
- 🔎 **SEO**：`sitemap.xml` / `robots.txt`、页面 meta、商品 JSON-LD 结构化数据。
- 📧 **邮件订阅 / 🍪 Cookie 同意 / 🌐 中英文切换**。
- 🎨 **来图定制**：客户提交描述 + 参考图（文件落盘）。
- ✉️ **询盘联系**：留言表单，含邮箱校验。
- 🔌 **REST API**：`/api/products`、`/api/products/search`、`/api/currencies`、`/api/custom-ideas`、`/api/inquiries`。
- 📱 **手机端 API**：`/api/m/**` 分页接口（供安卓客户端使用，支持 `X-API-KEY` 校验）。
- 🔐 **账号体系**：后台 `/admin/login`（站长 / 运营 / 只读，口令 PBKDF2 哈希）；C 端 `/register`、`/login`、`/account`。
- 📦 **后台管理**：商品 / 订单 / 客户 / 定制 / 询盘 / 订阅 / 评价管理，商品上传或填图链。
- 📊 **仪表盘**：统计卡片 + 各类管理列表。
- 💾 **数据存储**：MySQL 8（唯一数据源，环境变量配置，首次启动自动建库建表）。
- 🚀 **一键部署**：`start.sh` / `start.bat` / `install.ps1`。
- 📲 **PWA**：内置 manifest + 图标，可「添加到主屏幕」当作轻量 App。

---

## 2. 环境要求（硬性）

| 组件 | 最低版本 | 说明 |
|------|----------|------|
| JDK | **17** | 必须；不支持 JDK 8/11 运行此 Spring Boot 3.2 应用 |
| Maven | 3.8+ | 用于构建（也可直接用打包好的 jar） |
| 内存 | ≥ 512 MB（建议 1 GB） | JVM + 应用 |
| 磁盘 | ≥ 200 MB（含上传目录增长空间） | 上传文件默认落在 `./uploads` |
| 操作系统 | Linux / macOS / Windows | 跨平台 |
| 数据库 | **MySQL 8.0+**（唯一数据源） | 环境变量 `DB_*` 配置 |
| 数据库（应急） | 内置 H2（`h2` profile） | 仅本机无 MySQL 时临时预览，不用于生产 |
| 反向代理（生产推荐） | Nginx / Caddy | 处理 HTTPS、静态资源、域名 |

---

## 3. 获取源码

```bash
git clone https://github.com/zm51432/xumis.git
cd xumis
```

> 若已下载 ZIP，解压后进入 `xumis` 目录即可。

---

## 4. 一键部署（推荐：目录内零手工安装）

脚本会自动准备 JDK 17 与 Maven（下载到项目内 `.tools/`）并编译启动，数据库与上传文件也全部落在项目目录。

```bash
# Linux / macOS（脚本位于 server/ 目录）
cd server && chmod +x start.sh && ./start.sh

# Windows：双击 server\start.bat，或
powershell -ExecutionPolicy Bypass -File .\server\install.ps1 -Start
```

| 脚本 | 平台 | 说明 |
|------|------|------|
| `server/start.sh` | Linux / macOS | 支持 `--port=`、`--skip-build`、`--prod`、`--stop`、`--help` |
| `server/start.bat` | Windows | 双击运行，可传端口：`start.bat 9090` |
| `server/install.ps1` | Windows (PowerShell) | `-Start` / `-Stop` / `-SkipBuild` / `-Port` |
| `docker-compose.yml` | 任意（需 Docker） | 应用 + MySQL 8，一条命令起全套：`docker-compose up -d` |

> 每一步在做什么、失败如何排查，见仓库内 **ONECLICK.md**。

---

## 5. 手动构建与运行

```bash
# 进入服务端目录（含 pom.xml）
cd xumis/server

# 方式一：直接运行（默认连接本机 MySQL，需先启动 MySQL）
mvn spring-boot:run

# 无 MySQL 时临时预览（内置文件库）
mvn spring-boot:run -Dspring-boot.run.profiles=h2

# 方式二：打包为可执行 jar 后运行
mvn clean package -DskipTests
java -jar target/shine-xumis.jar
```

默认监听 `http://localhost:8080`。

---

## 6. 生产部署（MySQL 参数与账号）

### 6.1 准备 MySQL

```bash
mysql -u root -p < server/sql/init.sql      # 建库 + utf8mb4
```

或直接执行：

```sql
CREATE DATABASE shine CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'shine'@'%' IDENTIFIED BY '<DB_PASSWORD>';
GRANT ALL PRIVILEGES ON shine.* TO 'shine'@'%';
```

### 6.2 通过环境变量注入（推荐，避免明文写配置）

```bash
export SPRING_PROFILES_ACTIVE=prod
export DB_HOST=127.0.0.1
export DB_PORT=3306
export DB_NAME=shine
export DB_USER=shine
export DB_PASSWORD='<DB_PASSWORD>'
export UPLOAD_DIR=/var/lib/shine/uploads
export ADMIN_KEY='请改成随机长字符串'
# 手机端 API 密钥（配置后 /api/m/** 必须带 X-API-KEY）
export MOBILE_API_KEY='随机长字符串'
# ERP 对接（enabled=true 时业务数据才会同步）
export ERP_ENABLED=false
```

> **⚠️ 首次启动会自动建表，并在商品表为空时播种 12 个示例商品，同时创建站长账号 `admin / Admin@12345`（登录后请立即改密码）。**

---

## 7. 以服务方式运行（推荐）

```bash
sudo cp deploy/systemd/xumis.service /etc/systemd/system/xumis.service
sudo systemctl daemon-reload
sudo systemctl enable --now xumis
sudo journalctl -u xumis -f
```

---

## 8. 配置你自己的域名与反向代理（重点）

> 这一步就是把 **<你的域名>** 和 **<服务器公网IP>** 换成你自己的，是部署的核心。

1. **域名解析**：在你的域名服务商处，把 `A` 记录指向你的 **<服务器公网IP>**（若用 `www`，也加上 `www` 的 A 记录）。
2. **放行端口**：服务器防火墙 / 云安全组放行 `80` 与 `443`（以及你远程管理用的 `22`，建议限制来源 IP）。
3. **选择反代方案**：

### 方案 A：Nginx（证书用 certbot 申请）

```bash
sudo cp deploy/nginx.conf /etc/nginx/sites-available/xumis
sudo ln -s /etc/nginx/sites-available/xumis /etc/nginx/sites-enabled/
# 把配置文件里的 example.com 改成你的域名（包括 www.example.com）
sudo nginx -t
sudo certbot --nginx -d <你的域名> -d www.<你的域名>
sudo systemctl reload nginx
```

### 方案 B：Caddy（自动 HTTPS，最省事）

```bash
# 安装：https://caddyserver.com/docs/install
# 把 deploy/Caddyfile 里的 example.com 改成你的域名后：
caddy run --config deploy/Caddyfile
# 或常驻： caddy start --config deploy/Caddyfile
```

### ⚠️ 关键：绝对地址必须指向公网域名

社交分享（微信 / Facebook / X）的 OG 图与链接由后端 `GlobalModelAdvice.siteBaseUrl()` 拼**绝对地址**。

- 若后端拿不到真实协议/域名（看到的是 `http://127.0.0.1:8080/...`），分享卡片会裂图。
- 修复：`application.yml` 已加 `server.forward-headers-strategy: native`，后端会读取反代传来的 `X-Forwarded-Proto/Host`，自动生成 `https://<你的域名>/...`。

部署后用 curl 自测：

```bash
curl -s https://<你的域名>/product/1 | grep -o 'og:image" content="[^"]*"'
# 期望输出：og:image" content="https://<你的域名>/img/products/p01_...png"
```

---

## 9. APK 发布服务（OTA）

安卓客户端的版本发布与一键推送由独立小服务承担，与站点共用同一个域名和证书：

```bash
scp deploy/ota/{ota_server.py,shine-ota.service,nginx-ota.conf,install.sh} \
    deploy/ota/web/admin.html root@<服务器公网IP>:/tmp/shine-ota-pkg/
ssh root@<服务器公网IP> 'bash /tmp/shine-ota-pkg/install.sh'
```

- 管理台：`https://<你的域名>/apk/ota/`（口令登录，首次安装会打印初始口令）
- 客户端接口：`/apk/ota/api/check?vc=<versionCode>`、安装包 `/apk/ota/dl/*.apk`
- 推送后自动同步 `/var/www/download/` 的安装包与 `version.json`，下载页随之更新

详见仓库内 `deploy/ota/README.md`。客户端 APK 的获取与安装见仓库内 **android-apk-guide.md**。

---

## 10. 上线前检查清单

- [ ] HTTPS 可达，HTTP 自动跳 HTTPS
- [ ] 域名 `A` 记录已指向你的 **<服务器公网IP>**
- [ ] MySQL 已建库，`DB_PASSWORD` 已换成强密码
- [ ] 站长账号已改密码（`admin / Admin@12345` → 你的密码）
- [ ] `MOBILE_API_KEY` 已配置（公网建议开启 `X-API-KEY` 校验）
- [ ] 防火墙只放行 80 / 443 与必要的 22（建议限制来源 IP）
- [ ] OG 图自测通过（`curl` 检查 `og:image` 为 `https` 绝对地址）

---

## 11. 安全与隐私提示

- 不要把服务器 root 密码、GitHub Token、数据库密码写入仓库或聊天记录。
- 公网服务器建议：禁用 SSH 密码登录改密钥、安全组限制 22 来源 IP、定期 `mysqldump` 备份数据库。
- 后台默认口令 `Admin@12345`、`mobile-api-key` 留空（开发期免校验）属于不安全默认值，生产务必改掉。
