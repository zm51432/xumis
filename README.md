# shine星心 · 外贸独立站（Web + Android + 发布服务）

> **shine星心** 跨境 B2C 独立站，主营 **饰品 / 手工品**，支持 **客户来图定制**。
> 后端 **Java 17 + Spring Boot 3.2**（JPA + Thymeleaf + REST API），配套 **原生 Android 客户端**，
> 以及一套自建的 **APK 版本推送服务**（上传即发布，客户端应用内一键升级）。
>
> 线上站点：<https://shine.xumois.cn/>　·　下载页：<https://shine.xumois.cn/download/>
> 发布管理台：<https://shine.xumois.cn/apk/ota/>

---

## 🗂 仓库结构（按职责分为五类）

```
xumis/
├── server/         ① 网站服务端（Spring Boot，Java 17）
├── android/        ② 安卓客户端（Kotlin，ViewBinding）
├── deploy/         ③ 部署与运维（nginx / systemd / 下载页 / APK 推送服务）
├── docs/           ④ 文档（部署指南、一键脚本说明、运营问答）
├── archive/        ⑤ 历史归档（早期 personal-site 版本）
├── docker-compose.yml   根级编排：应用 + MySQL 8
├── README.md            本文件：项目总览
└── CHANGELOG.md         逐版本变更记录
```

| 目录 | 内容 | 入口文档 |
|------|------|----------|
| `server/` | Maven 工程、Spring Boot 源码、启动脚本、Dockerfile | [server/](#-server--网站服务端) · [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md) |
| `android/` | Gradle 工程、Kotlin 源码、离线资源、打包工具 | [android/README.md](android/README.md) |
| `deploy/` | nginx 站点配置、systemd 单元、APK 下载页、OTA 推送服务 | [deploy/README.md](deploy/README.md) · [deploy/ota/README.md](deploy/ota/README.md) |
| `docs/` | 部署要求、一键部署逐步骤、用户痛点问答 | [docs/ONECLICK.md](docs/ONECLICK.md) |
| `archive/` | 旧版静态站归档（仅存档，不参与构建） | — |

---

## ① server/ · 网站服务端

### 功能特性

| 模块 | 说明 |
|------|------|
| 🏠 首页 | 品牌故事 + 精选商品 + 来图定制入口，温馨清新视觉 |
| 🛍 商品展示 | 列表 + 分类筛选（饰品 / 手工品 / 其他）+ **关键词搜索 / 排序 / 分页** + 详情页 |
| 🛒 购物车与订单 | 会话购物车、加入/改数量/删除、结算生成订单、订单成功页 |
| 💱 多币种 | USD / CNY / EUR 一键切换（Cookie 记忆），价格按汇率换算 |
| ⭐ 商品评价 | 用户提交评分与评价，后台审核后展示，详情页展示平均分 |
| 🔎 SEO | `sitemap.xml` / `robots.txt`、页面 meta、商品 JSON-LD 结构化数据 |
| 📧 邮件订阅 | 页脚 Newsletter 订阅，后台可查看订阅列表 |
| 🍪 Cookie 同意 | GDPR 风格同意横幅，本地记录选择 |
| 🎨 来图定制 | 客户提交想法描述 + 参考图上传（文件落盘） |
| ✉️ 询盘联系 | 留言表单，含邮箱校验 |
| 🌐 国际化 | 中 / 英 一键切换（Cookie 记忆） |
| 🔌 REST API | `/api/products`、`/api/products/search`、`/api/currencies`、`/api/custom-ideas`、`/api/inquiries` |
| 📱 手机端 API | `/api/m/**` 分页接口（供 Android 客户端使用，支持 X-API-KEY 校验） |
| 🧩 ERP 预留 | `ErpClient` 抽象层 + 开关，商品/定制/询盘/**订单**均可同步至用友/金蝶/SAP/自研中台 |
| 📲 PWA | 内置 manifest + 图标，可「添加到主屏幕」当作轻量 App |
| 🔐 后台账号体系 | `/admin/login` 账号登录 + 角色权限（站长 / 运营 / 只读），口令 PBKDF2 哈希存储 |
| 👥 C 端账号 | `/register`、`/login`、`/account`（收货资料 + 我的订单） |
| 📦 后台商品管理 | `/admin/products` 上传图片 / 填图片链接 / 改信息 / 删除 / 精选开关 |
| 🚚 订单进度 | `/order/track` 订单号 + 邮箱查询，含状态时间线 |
| 📊 后台仪表盘 | `/admin` 统计卡片 + 订单/商品/客户/定制/询盘/订阅/评价管理 |
| 💾 数据存储 | **MySQL 8**（唯一数据源，`DB_*` 环境变量配置，首次启动自动建库建表） |
| 🚀 一键部署 | `start.sh` / `start.bat` / `install.ps1`，Windows / Linux / macOS 目录内零手工安装 |

### 目录

```
server/
├── pom.xml                    # Maven 构建配置
├── src/main/java/com/shine/xumis/
│   ├── XumisApplication.java
│   ├── config/                # AppProperties / Web / i18n / Locale / 种子数据
│   ├── controller/            # 页面 + REST API + 手机端 API + 购物车 + ERP + SEO + 后台
│   ├── integration/erp/       # ERP 对接抽象层（预留）
│   ├── model/                 # Product / Order / OrderItem / Review / Subscription / CustomIdea / Inquiry
│   ├── repository/            # JPA 仓库
│   ├── service/               # 业务 + 文件存储 + 购物车 + 币种 + ERP 同步
│   └── dto/                   # 表单对象 / 购物车行 / 搜索结果 / 手机端视图
├── src/main/resources/
│   ├── application.yml        # 默认(MySQL) / prod / h2(应急) 三组配置
│   ├── messages*.properties   # 中英文文案
│   ├── static/                # css / js / manifest / icon
│   └── templates/             # Thymeleaf 页面
├── sql/init.sql               # MySQL 建库脚本（应用也会自动建库，可选执行）
├── start.sh / start.bat       # 一键启动（Linux / macOS · Windows）
├── install.ps1                # Windows 一键安装 + 构建 + 启动
├── Dockerfile                 # 多阶段构建镜像
├── .tools/                    # 【自动生成·不入库】JDK 17 / Maven
├── uploads/                   # 【自动生成·不入库】客户上传图 / 商品图
├── runtime/                   # 【自动生成·不入库】日志与 PID
└── target/                    # 【自动生成·不入库】构建产物 shine-xumis.jar
```

### 快速开始

```bash
cd server

# Linux / macOS：自动准备 JDK 17 + Maven（下载到 .tools/，不污染系统）→ 构建 → 启动
chmod +x start.sh && ./start.sh
./start.sh --port=9090      # 指定端口
./start.sh --skip-build     # 已有 jar 时跳过构建
./start.sh --h2             # 没有 MySQL 时用内置文件库应急预览
./start.sh --stop           # 停止服务
```

```powershell
# Windows：双击 server\start.bat，或在 PowerShell 中执行
cd server
.\install.ps1 -Start
.\install.ps1 -Start -Port 9090
.\install.ps1 -Stop
```

```bash
# Docker（应用 + MySQL 8）：在仓库根执行
docker compose up -d --build
```

数据库默认 `127.0.0.1:3306` / 库 `shine` / 用户 `root`，可用环境变量覆盖：

```bash
DB_HOST=127.0.0.1 DB_PORT=3306 DB_NAME=shine DB_USER=root DB_PASSWORD=xxx ./start.sh
```

启动后：

- 店铺前台 <http://localhost:8080/>
- 后台 <http://localhost:8080/admin>（首次启动创建站长账号 `admin` / `Admin@12345`，登录后请立刻改密）
- 商品管理 <http://localhost:8080/admin/products>
- 订单查询 <http://localhost:8080/order/track>

### 手动构建

```bash
cd server
mvn spring-boot:run                                       # 直接运行
mvn clean package -DskipTests && java -jar target/shine-xumis.jar
```

### 技术栈

Java 17 · Spring Boot 3.2 · Spring MVC · Thymeleaf · Spring Data JPA · MySQL 8 · Bean Validation · Maven

---

## ② android/ · 安卓客户端

Kotlin + ViewBinding 原生 App，minSdk 24 / targetSdk 34，线上版本 **1.5.0（versionCode 6）**。

| 能力 | 说明 |
|------|------|
| 原生页面 | 启动页、首页商品网格、搜索、商品详情、购物车/账户（WebView 桥接站点流程） |
| 离线可用 | 内置离线商品册（12 件商品原图 + 6 套字体 + 三张本地页面），断网自动兜底 |
| 四项权限 | 仅定位 / 相册 / 相机 / 蓝牙，适配小米、OPPO、vivo 的权限页跳转 |
| 应用内升级 | 前台静默检查、更新说明、下载进度、SHA-256 校验、系统安装器、强制更新 |
| 界面无地址 | 全部界面不出现服务器地址与接口 URL |

```bash
cd android
./gradlew assembleDebug        # 产物 app/build/outputs/apk/debug/app-debug.apk
```

细节见 [android/README.md](android/README.md)。

---

## ③ deploy/ · 部署与运维

```
deploy/
├── nginx.conf                   # 站点反向代理（80 → HTTPS → 127.0.0.1:8080）
├── Caddyfile                    # 备选：Caddy 一键 HTTPS
├── systemd/xumis.service        # 后端常驻单元
├── apk/                         # APK 下载页
│   ├── index.html               # 读 version.json 动态显示版本
│   ├── nginx-download.conf      # /download/ 静态目录 + apk MIME
│   └── remote-publish.sh        # 服务器侧挂载下载目录
└── ota/                         # APK 版本推送服务（自包含，可整体上传）
    ├── ota_server.py            # Python3 标准库实现的 OTA 服务（127.0.0.1:8095）
    ├── web/admin.html           # 发布管理台（https://shine.xumois.cn/apk/ota/）
    ├── nginx-ota.conf           # /apk/ota 反向代理片段
    ├── shine-ota.service        # systemd 单元
    └── install.sh               # 幂等安装脚本
```

**推送服务做什么**：拖入 APK → 自动解析包名 / versionCode / versionName / 签名指纹 / SHA-256 →
一键推送为线上版本，同时把安装包与 `version.json` 同步到下载页；支持回滚、强制更新、下载统计与操作审计。
换签名的包会被拒绝推送（避免老用户装不上）。详见 [deploy/ota/README.md](deploy/ota/README.md)。

---

## ④ docs/ · 文档

| 文档 | 内容 |
|------|------|
| [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md) | 环境要求、手动构建、生产部署、HTTPS 与反向代理、Docker |
| [docs/ONECLICK.md](docs/ONECLICK.md) | 一键脚本逐步骤说明、参数表、常见故障 |
| [docs/独立站用户痛点50问自解.md](docs/独立站用户痛点50问自解.md) | 独立站运营常见 50 问的应答口径 |
| [CHANGELOG.md](CHANGELOG.md) | 逐版本改动与验证记录 |

---

## 🔗 接口示例

```bash
# 商品列表 / 分类
curl http://localhost:8080/api/products
curl "http://localhost:8080/api/products?cat=JEWELRY"
curl http://localhost:8080/api/m/categories
# 手机端分页（配置 app.mobile-api-key 后需带 X-API-KEY）
curl "http://localhost:8080/api/m/products?page=0&size=10"
# 提交询盘
curl -X POST http://localhost:8080/api/inquiries \
  -H "Content-Type: application/json" \
  -d '{"name":"Tom","email":"tom@example.com","subject":"Wholesale","message":"Hi, I want 100pcs."}'
```

## 🧩 ERP 对接（预留）

业务侧统一调用 `ErpSyncService`，由 `app.erp.*` 开关控制是否真正同步；`ErpClient` 为抽象接口，
内置 `NoopErpClient`（开发期）与 `HttpErpClient`（JSON POST 骨架），新增实现即可对接具体 ERP。

```yaml
app:
  erp:
    enabled: true            # 总开关
    type: http               # noop(仅日志) | http(调外部接口)
    base-url: https://erp.example.com/api
    api-key: "xxxx"
```

---

## 🔒 安全加固（10 轮审计修复摘要）

| # | 问题 | 修复 |
|---|------|------|
| 1 | 全站 POST 无 CSRF 防护 | 会话级 CSRF 令牌，写请求必校验，`/api/**` 走 API Key |
| 2 | 登录可无限试密码 | 同账号连续失败 5 次锁定 10 分钟 |
| 3 | 登录不换 Session / 登出用 GET | 登录后 `changeSessionId()`，登出改 POST 并 `invalidate()` |
| 4 | 上传只看扩展名 | 文件头（magic bytes）校验 + 大小上限 |
| 5 | 购物车不校验库存 | 加购受库存约束，结算前清理失效条目，下单扣减 |
| 6 | 商品不存在返回 200 空白页 | 真 404 + 站内风格错误页 |
| 7 | 订单号纯时间戳撞唯一约束 | 订单号加 3 位随机后缀 |
| 8 | 订单状态可写任意值 | 白名单校验 |
| 9 | 搜索把整表读进内存再排序 | 数据库侧 JPQL 分页 + 排序 |
| 10 | 评价/订阅/询盘/定制可刷屏 | 同 IP 10 分钟最多 5 次 |

另：安全响应头（CSP / X-Frame-Options / nosniff / Referrer-Policy）、会话 Cookie HttpOnly + SameSite、
关闭错误页堆栈回显、`?key=` 应急入口默认关闭、`admin-key` 常量时间比较。

**生产提示**：后台需接入正式鉴权（Spring Security / OAuth2）；上传目录应指向独立存储或对象存储；
演示商品图请替换为自有图床；APK 正式发版必须使用**同一个 keystore**，否则老用户无法覆盖安装。
