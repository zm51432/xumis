# shine星心 · 外贸独立站展示模板

> 一个用 **Java（Spring Boot）** 构建的外贸独立站（跨境 B2C 展示站）完整模板。
> 店名 **shine星心**，主营 **饰品 / 手工品**，并支持 **客户自定义上传想法（来图定制）**。
> 前后端一体：后端 Spring Boot + JPA，前端 Thymeleaf 视图，另提供 REST API 供 SPA / 小程序对接。

---

## ✨ 功能特性

| 模块 | 说明 |
|------|------|
| 🏠 首页 | 品牌展示、精选商品、定制 CTA |
| 🛍 商品展示 | 列表 + 分类筛选（饰品 / 手工品 / 其他）+ 详情页 |
| 🎨 来图定制 | 客户提交想法描述 + 参考图上传（文件落盘） |
| ✉️ 询盘联系 | 留言表单，含邮箱校验 |
| 🌐 国际化 | 中 / 英 一键切换（Cookie 记忆） |
| 🔌 REST API | `/api/products`、`/api/custom-ideas`、`/api/inquiries` |
| 📱 手机端 API | `/api/m/**` 分页接口（预留 App / 小程序接入，支持 X-API-KEY 校验） |
| 🧩 ERP 预留 | `ErpClient` 抽象层 + 开关，后续可无缝对接用友/金蝶/SAP/自研中台 |
| 📲 PWA | 内置 manifest，可「添加到主屏幕」当作轻量 App |
| 🛠 简易后台 | `/admin?key=...` 查看定制想法与询盘 |
| 💾 数据存储 | 开发用 H2，生产可切 MySQL（同一套 JPA 代码） |

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
├── pom.xml
├── README.md
├── DEPLOYMENT.md
├── archive/                      # 仓库旧版 personal-site 归档
└── src/main/
    ├── java/com/shine/xumis/
    │   ├── XumisApplication.java
    │   ├── config/              # Web/i18n/种子数据
    │   ├── controller/          # 页面 + REST API + 手机端 API + 后台
    │   ├── integration/erp/     # ERP 对接抽象层（预留）
    │   ├── model/              # Product / CustomIdea / Inquiry / Category
    │   ├── repository/         # JPA 仓库
    │   ├── service/            # 业务 + 文件存储 + ERP 同步
    │   └── dto/                # 表单对象 / 手机端视图
    └── resources/
        ├── application.yml     # 含 dev / prod 双 profile
        ├── messages*.properties # 中英文文案
        ├── static/             # css / js
        └── templates/          # Thymeleaf 页面
```

---

## 🚀 本地快速启动

```bash
# 需要 JDK 17 与 Maven 3.8+
mvn spring-boot:run
# 或
mvn clean package && java -jar target/shine-xumis.jar
```

启动后访问：
- 店铺前台： http://localhost:8080/
- H2 控制台（开发）： http://localhost:8080/h2-console
- 简易后台： http://localhost:8080/admin?key=admin

种子数据会在首次启动自动写入 8 条演示商品。

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
