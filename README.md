# shine星心 · 外贸独立站展示模板

> 一个用 **Java（Spring Boot）** 构建的外贸独立站（跨境 B2C 展示站）完整模板。
> 店名 **shine星心**，主营 **饰品 / 手工品**，并支持 **客户自定义上传想法（来图定制）**。
> 前后端一体：后端 Spring Boot + JPA，前端 Thymeleaf 视图，另提供 REST API 供 SPA / 小程序对接。

---

## ✨ 功能特性

| 模块 | 说明 |
|------|------|
| 🏠 首页 | 品牌故事 + 精选商品 + 来图定制入口，温馨清新视觉 |
| 🛍 商品展示 | 列表 + 分类筛选（饰品 / 手工品 / 其他）+ **关键词搜索 / 排序 / 分页** + 详情页 |
| 🛒 购物车与订单 | 会话购物车、加入/改数量/删除、结算生成订单、订单成功页（对标主流独立站） |
| 💱 多币种 | USD / CNY / EUR 一键切换（Cookie 记忆），价格按汇率换算 |
| ⭐ 商品评价 | 用户提交评分与评价，后台审核后展示，详情页展示平均分 |
| 🔎 SEO | `sitemap.xml` / `robots.txt`、页面 meta、商品 JSON-LD 结构化数据 |
| 📧 邮件订阅 | 页脚 Newsletter 订阅，后台可查看订阅列表 |
| 🍪 Cookie 同意 | GDPR 风格同意横幅，本地记录选择 |
| 🎨 来图定制 | 客户提交想法描述 + 参考图上传（文件落盘） |
| ✉️ 询盘联系 | 留言表单，含邮箱校验 |
| 🌐 国际化 | 中 / 英 一键切换（Cookie 记忆） |
| 🔌 REST API | `/api/products`、`/api/products/search`、`/api/currencies`、`/api/custom-ideas`、`/api/inquiries` |
| 📱 手机端 API | `/api/m/**` 分页接口（预留 App / 小程序接入，支持 X-API-KEY 校验） |
| 🧩 ERP 预留 | `ErpClient` 抽象层 + 开关，商品/定制/询盘/**订单**均可同步至用友/金蝶/SAP/自研中台 |
| 📲 PWA | 内置 manifest + 图标，可「添加到主屏幕」当作轻量 App |
| 📊 后台仪表盘 | `/admin?key=...` 统计卡片 + 订单/定制/询盘/订阅/待审评价管理 |
| 💾 数据存储 | 开发用 H2 文件库（目录内持久化），生产可切 MySQL（同一套 JPA 代码） |
| 🚀 一键部署 | `start.sh` / `start.bat` / `install.ps1`，Windows / Linux / macOS 均可在目录内零手工安装完成 |

---

## 🧱 技术栈

- **Java 17** + **Spring Boot 3.2**
- **Spring MVC** + **Thymeleaf**（前端视图层）
- **Spring Data JPA**（后端持久化）
- **H2**（开发）/ **MySQL**（生产）
- **Bean Validation** 表单校验
- **Maven** 构建

---

## 📁 目录结构

```
xumis/
├── pom.xml                       # Maven 构建配置
├── start.sh                      # 一键启动（Linux / macOS）
├── start.bat                     # 一键启动（Windows，双击即可）
├── install.ps1                   # Windows 一键安装 + 构建 + 启动（PowerShell）
├── Dockerfile / docker-compose.yml / .dockerignore
├── README.md / DEPLOYMENT.md / ONECLICK.md
├── archive/                      # 仓库旧版 personal-site 归档
├── .tools/                       # 【自动生成】一键脚本下载的 JDK / Maven（不入库）
├── data/                         # 【自动生成】H2 数据库文件（重启不丢数据）
├── uploads/                      # 【自动生成】客户上传的参考图
├── runtime/                      # 【自动生成】日志与 PID
└── src/main/
    ├── java/com/shine/xumis/
    │   ├── XumisApplication.java
    │   ├── config/              # AppProperties / Web / i18n / Locale / 种子数据
    │   ├── controller/          # 页面 + REST API + 手机端 API + 购物车 + ERP + SEO + 后台
    │   ├── integration/erp/     # ERP 对接抽象层（预留）
    │   ├── model/               # Product / Order / OrderItem / Review / Subscription / CustomIdea / Inquiry
    │   ├── repository/          # JPA 仓库
    │   ├── service/             # 业务 + 文件存储 + 购物车 + 币种 + ERP 同步
    │   └── dto/                 # 表单对象 / 购物车行 / 搜索结果 / 手机端视图
    └── resources/
        ├── application.yml      # 含 dev / prod 双 profile
        ├── messages*.properties # 中英文文案
        ├── static/              # css / js / manifest / icon
        └── templates/           # Thymeleaf 页面（首页/商品/详情/购物车/结算/成功/定制/询盘/后台）
```

---

## 🚀 一键部署（推荐，无需手工装环境）

脚本会自动检测 JDK 17 与 Maven，缺失时**下载到本项目 `.tools/` 目录**（不污染系统、不需要管理员权限），
然后编译打包并启动。JDK / Maven / 数据库 / 上传文件**全部在项目目录内**，删除目录即卸载。

```bash
# Linux / macOS
chmod +x start.sh
./start.sh                 # 一键：装环境 → 构建 → 启动
./start.sh --port=9090     # 指定端口
./start.sh --skip-build    # 已有 jar 时跳过构建
./start.sh --stop          # 停止服务
```

```powershell
# Windows：双击 start.bat，或在 PowerShell 中执行
.\install.ps1 -Start
.\install.ps1 -Start -Port 9090
.\install.ps1 -Stop
```

```bash
# Docker（应用 + MySQL 8）
docker compose up -d --build
```

启动后访问：
- 店铺前台： http://localhost:8080/
- H2 控制台（开发）： http://localhost:8080/h2-console （JDBC URL `jdbc:h2:file:./data/shinedb`，用户 `sa`，密码空）
- 简易后台： http://localhost:8080/admin?key=admin

种子数据会在首次启动自动写入演示商品。

> **每一步在做什么、失败了怎么办**，见 **ONECLICK.md**（逐步骤说明）与 **DEPLOYMENT.md**（生产部署）。

---

## 🛠 手动启动（已有 JDK 17 + Maven）

```bash
mvn spring-boot:run
# 或
mvn clean package && java -jar target/shine-xumis.jar
```

---

## 🔗 REST API 示例

```bash
# 商品列表
curl http://localhost:8080/api/products
# 按分类
curl "http://localhost:8080/api/products?cat=JEWELRY"
# 提交询盘（JSON）
curl -X POST http://localhost:8080/api/inquiries \
  -H "Content-Type: application/json" \
  -d '{"name":"Tom","email":"tom@example.com","subject":"Wholesale","message":"Hi, I want 100pcs."}'
```

## 📱 手机端 API（预留接入）

路径前缀 `/api/m`，支持分页与 `Accept-Language` 语言选择；配置 `app.mobile-api-key` 后需带请求头 `X-API-KEY`。

```bash
# 分页商品（开发期可免密钥）
curl "http://localhost:8080/api/m/products?page=0&size=10"
# 带密钥 + 英文
curl -H "X-API-KEY: your-key" -H "Accept-Language: en" "http://localhost:8080/api/m/products?cat=HANDMADE"
# 分类
curl "http://localhost:8080/api/m/categories"
```

## 🧩 ERP 对接（预留）

业务侧统一调用 `ErpSyncService`，由 `app.erp.*` 开关控制是否真正同步：

```yaml
app:
  erp:
    enabled: true            # 总开关
    type: http               # noop(仅日志) | http(调外部接口)
    base-url: https://erp.example.com/api
    api-key: "xxxx"
```

- `ErpClient` 为抽象接口，新增 `YourErpClient` 实现并在 `ErpConfig` 切换 `type` 即可接入具体 ERP。
- 已内置 `NoopErpClient`（开发期）与 `HttpErpClient`（JSON POST 骨架），同步失败不影响主流程。
- 手动全量同步：`POST /api/erp/sync-products?key=ADMIN_KEY`。

> 完整部署、生产数据库切换、HTTPS、反向代理与 Docker 说明见 **DEPLOYMENT.md**。

---

## ⚠️ 安全提示

- 后台 `/admin` 仅用 `app.admin-key` 做演示性保护，**生产必须接入正式鉴权**（Spring Security / OAuth2）。
- 上传文件落盘目录生产请指向独立存储或对象存储（S3 / OSS / COS）。
- 演示商品图片使用占位图服务，请替换为自有图床。
