# 部署要求与指南（DEPLOYMENT）

本文档说明 **shine星心 外贸独立站模板** 的运行环境、构建、生产与容器化部署要求。

---

## 1. 环境要求（硬性）

| 组件         | 最低版本                    | 说明                                     |
| ---------- | ----------------------- | -------------------------------------- |
| JDK        | **17**                  | 必须；不支持 JDK 8/11 运行此 Spring Boot 3.2 应用 |
| Maven      | 3.8+                    | 用于构建（也可直接用打包好的 jar）                    |
| 内存         | ≥ 512 MB（建议 1 GB）       | JVM + 应用                               |
| 磁盘         | ≥ 200 MB（含上传目录增长空间）     | 上传文件默认落在 `./uploads`                   |
| 操作系统       | Linux / macOS / Windows | 跨平台                                    |
| 数据库（开发）    | 内置 H2，无需安装              | 零配置启动                                  |
| 数据库（生产）    | **MySQL 8.0+**          | 通过 prod profile 切换                     |
| 反向代理（生产推荐） | Nginx / Caddy           | 处理 HTTPS、静态资源、域名                       |

---

## 2. 本地构建与运行

```bash
# 进入项目根目录（含 pom.xml）
cd xumis

# 方式一：直接运行（开发，使用 H2）
mvn spring-boot:run

# 方式二：打包为可执行 jar 后运行
mvn clean package -DskipTests
java -jar target/shine-xumis.jar
```

默认监听 `http://localhost:8080`。

---

## 3. 生产部署（切换 MySQL）

### 3.1 准备 MySQL

```sql
CREATE DATABASE shine CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'shine'@'%' IDENTIFIED BY '强密码';
GRANT ALL PRIVILEGES ON shine.* TO 'shine'@'%';
```

### 3.2 通过环境变量注入（推荐，避免明文写配置）

```bash
export SPRING_PROFILES_ACTIVE=prod
export DB_HOST=127.0.0.1
export DB_PORT=3306
export DB_NAME=shine
export DB_USER=shine
export DB_PASSWORD='你的强密码'
export UPLOAD_DIR=/var/lib/shine/uploads
export ADMIN_KEY='请改成随机长字符串'
# 手机端 API 密钥（配置后 /api/m/** 必须带 X-API-KEY）
export MOBILE_API_KEY='随机长字符串'
# ERP 对接（enabled=true 时业务数据才会同步）
export ERP_ENABLED=false
export ERP_TYPE=noop
export ERP_BASE_URL=''
export ERP_API_KEY=''
java -jar target/shine-xumis.jar
```

> `application.yml` 的 `prod` profile 已内置 MySQL 连接串，变量缺失时使用默认值（请勿在生产保留默认口令）。

### 3.4 ERP 与手机端对接配置

| 配置项 | 说明 | 默认值 |
| --- | --- | --- |
| `ERP_ENABLED` | 是否开启 ERP 同步总开关 | `false` |
| `ERP_TYPE` | `noop`（仅日志）/ `http`（调用外部接口） | `noop` |
| `ERP_BASE_URL` | ERP 接口基地址，如 `https://erp.example.com/api` | 空 |
| `ERP_API_KEY` | ERP 接口鉴权密钥（写入请求头 `X-ERP-API-KEY`） | 空 |
| `MOBILE_API_KEY` | 手机端 `/api/m/**` 访问密钥；配置后请求须带 `X-API-KEY` 头 | 空（开发期免校验） |

- ERP 接入：新增 `ErpClient` 实现类，在 `ErpConfig` 中按 `type` 切换即可；同步失败不影响主流程。
- 手动触发商品全量同步：`POST /api/erp/sync-products?key=ADMIN_KEY`。
- 手机端调试（开发期免密钥）：`curl "http://localhost:8080/api/m/products"`。

### 3.3 端口与上下文

- 端口：`server.port`（默认 8080），可用 `-Dserver.port=8081` 覆盖。
- 上传访问前缀：`/files/**`，由 `app.upload-url` 配置。

---

## 4. 反向代理（Nginx 示例）

```nginx
server {
    listen 80;
    server_name shine.example.com;   # 你的域名
    client_max_body_size 12m;        # 须 ≥ 应用的多文件上限

    location / {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

启用 HTTPS（Let's Encrypt）：

```bash
certbot --nginx -d shine.example.com
```

---

## 5. Docker 部署（推荐生产）

### 5.1 构建镜像

项目根目录创建 `Dockerfile`：

```dockerfile
FROM eclipse-temurin:17-jdk
WORKDIR /app
COPY target/shine-xumis.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
```

```bash
mvn clean package -DskipTests
docker build -t shine-xumis:1.0.0 .
```

### 5.2 运行容器（连外部 MySQL）

```bash
docker run -d --name shine \
  -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e DB_HOST=mysql-host \
  -e DB_NAME=shine -e DB_USER=shine -e DB_PASSWORD='强密码' \
  -e UPLOAD_DIR=/data/uploads -v shine-uploads:/data/uploads \
  shine-xumis:1.0.0
```

> 上传目录务必挂载为卷，否则容器重建会丢失客户参考图。

---

## 6. 部署检查清单 ✅

- [ ] JDK 17 已安装（`java -version`）
- [ ] 生产使用 `prod` profile 并指向 MySQL
- [ ] 数据库账号为专用最小权限账号，口令为强随机值
- [ ] `ADMIN_KEY` 已修改为随机长字符串，且 `/admin` 已加正式鉴权
- [ ] 若开放手机端，已设置 `MOBILE_API_KEY` 并在 App 侧携带 `X-API-KEY`
- [ ] 若对接 ERP，已设置 `ERP_ENABLED=true`、`ERP_TYPE`、`ERP_BASE_URL`、`ERP_API_KEY`，并验证同步日志
- [ ] 上传目录已挂载持久卷 / 对象存储
- [ ] 已配置 Nginx + HTTPS（443）
- [ ] `client_max_body_size` ≥ 应用上传上限
- [ ] 防火墙仅开放 80/443，应用端口不对外
- [ ] 已配置日志与定期数据库备份

---

## 7. 常见部署问题

| 现象                                         | 原因 / 解决                              |
| ------------------------------------------ | ------------------------------------ |
| 启动报 `Unsupported class file major version` | JDK 版本过低，请升级到 17                     |
| 上传大图 413                                   | Nginx `client_max_body_size` 太小，调大   |
| 后台无数据                                      | 确认访问 `/admin?key=你的ADMIN_KEY`        |
| 切换英文不生效                                    | 清除浏览器 Cookie 后点右上角 EN/中文             |
| 图片不显示                                      | 检查 `UPLOAD_DIR` 路径权限与 `/files/**` 映射 |

---

## 8. 安全与合规（生产必读）

1. **后台鉴权**：当前 `/admin` 仅作演示保护，**务必接入 Spring Security / OAuth2**，禁止公网裸奔。
2. **文件上传**：限制文件类型（仅图片）、扫描病毒、落盘到独立存储或对象存储（S3/OSS/COS）。
3. **隐私合规**：客户邮箱与上传图片属个人数据，遵守 GDPR / 目标市场法规，明确告知用途。
4. **依赖更新**：定期 `mvn versions:display-dependency-updates` 并升级，避免已知漏洞。
