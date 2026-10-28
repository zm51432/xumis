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
    │   ├── controller/          # 页面 + REST API + 后台
    │   ├── model/              # Product / CustomIdea / Inquiry / Category
    │   ├── repository/         # JPA 仓库
    │   ├── service/            # 业务 + 文件存储
    │   └── dto/                # 表单对象
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

> 完整部署、生产数据库切换、HTTPS、反向代理与 Docker 说明见 **DEPLOYMENT.md**。

---

## ⚠️ 安全提示

- 后台 `/admin` 仅用 `app.admin-key` 做演示性保护，**生产必须接入正式鉴权**（Spring Security / OAuth2）。
- 上传文件落盘目录生产请指向独立存储或对象存储（S3 / OSS / COS）。
- 演示商品图片使用占位图服务，请替换为自有图床。
