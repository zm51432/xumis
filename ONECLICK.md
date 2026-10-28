# shine星心 · 一键部署逐步骤说明

> 目标：**不管 Windows 还是 Linux / macOS，只在本项目目录内执行一条命令即可完成安装、构建与启动。**
> 全程不需要手工安装 JDK、Maven，也不需要管理员/root 权限；
> **数据库使用 MySQL 8，需先启动本机 MySQL**（脚本会预检并提示；没有 MySQL 时可用 `--h2` 临时预览）。
> 依赖与上传文件都落在项目目录里，**删除整个目录即卸载**。

---

## 目录总览（脚本会用到）

| 路径 | 作用 | 是否入库 |
|------|------|----------|
| `start.sh` | Linux / macOS 一键脚本 | ✅ |
| `start.bat` | Windows 双击启动入口 | ✅ |
| `install.ps1` | Windows 安装 + 构建 + 启动 | ✅ |
| `.tools/jdk17` | 自动下载的 JDK 17（缺失时才有） | ❌ |
| `.tools/maven` | 自动下载的 Maven 3.9.9（缺失时才有） | ❌ |
| `uploads/` | 客户上传图 + 后台商品图 | ❌ |
| `sql/init.sql` | MySQL 建库脚本（应用也会自动建库，可选执行） | ✅ |
| `runtime/logs` | 运行日志 `app.log`、`app.err.log` | ❌ |
| `runtime/app.pid` | 进程号，用于 `--stop` / `-Stop` | ❌ |
| `target/shine-xumis.jar` | 构建产物 | ❌ |

---

## 方式一：Windows（推荐，双击即可）

### 第 1 步：取得源码
```powershell
git clone https://github.com/zm51432/xumis.git
cd xumis
```
> 若已下载 ZIP，解压后进入 `xumis` 目录即可。

### 第 2 步：双击 `start.bat`
也可以命令行指定端口：
```cmd
start.bat 9090
```

### 第 3 步：脚本内部做了什么（你会看到这些输出）

| 步骤 | 屏幕提示 | 实际动作 | 耗时 |
|------|----------|----------|------|
| ① 准备目录 | `工作目录：…` | 创建 `.tools`、`data`、`uploads`、`runtime\logs` | 秒级 |
| ② 检查 Java | `步骤 1/4：检查 Java 运行环境` | 依次检查 `JAVA_HOME` → `.tools\jdk17` → 系统 `java`，要求版本 ≥ 17 | 秒级 |
| ②b 缺失则安装 | `自动下载到 .tools\jdk17` | 从 Adoptium 下载 Temurin JDK 17 压缩包并解压到 `.tools\jdk17` | 1–5 分钟（仅首次） |
| ③ 检查 Maven | `步骤 2/4：检查 Maven` | 检查系统 `mvn` → `.tools\maven` | 秒级 |
| ③b 缺失则安装 | `自动下载到 .tools\maven` | 从 Apache 归档下载 Maven 3.9.9 并解压到 `.tools\maven` | 1–3 分钟（仅首次） |
| ④ 编译打包 | `步骤 3/4：编译打包` | 执行 `mvn -B -q clean package -DskipTests`，生成 `target\shine-xumis.jar` | 首次 2–5 分钟（要下载依赖） |
| ⑤ 启动 | `步骤 4/4：启动站点` | 后台启动 Java 进程，轮询 `http://127.0.0.1:8080/` 直到返回 200 | 10–30 秒 |
| ⑥ 打开浏览器 | `正在打开浏览器` | 自动访问 `http://localhost:8080/` | — |

### 第 4 步：看到这个界面就成功了
```
============================================================
[完成]  shine星心 独立站已启动
   前台首页   http://localhost:8080/
   后台管理   http://localhost:8080/admin?key=admin
   日志       …\runtime\logs\app.log
   停止服务   .\install.ps1 -Stop
============================================================
```

### 第 5 步：停止与重启
```powershell
.\install.ps1 -Stop          # 停止
.\install.ps1 -Start         # 重启（会重新构建）
.\install.ps1 -Start -SkipBuild   # 重启但不重新构建
```

---

## 方式二：Linux / macOS

### 第 1 步：取得源码
```bash
git clone https://github.com/zm51432/xumis.git
cd xumis
```

### 第 2 步：赋予执行权限并运行
```bash
chmod +x start.sh
./start.sh
```

> macOS 若提示「无法打开，因为来自身份不明的开发者」：
> 系统设置 → 隐私与安全性 → 仍要打开；或直接执行 `bash start.sh`。

### 第 3 步：可用参数

| 命令 | 说明 |
|------|------|
| `./start.sh` | 完整流程：装环境 → 构建 → 启动（默认 8080 端口、dev 配置） |
| `./start.sh --port=9090` | 指定端口 |
| `./start.sh --skip-build` | 跳过构建，直接启动已有 jar |
| `./start.sh --prod` | 使用 prod 配置（MySQL，见 DEPLOYMENT.md） |
| `./start.sh --h2` | 本机无 MySQL 时用内置文件库临时预览 |
| `./start.sh --stop` | 停止正在运行的实例（读取 `runtime/app.pid`） |
| `./start.sh --help` | 查看用法 |

### 第 4 步：内部步骤（与 Windows 完全对应）
1. **创建目录**：`.tools`、`data`、`uploads`、`runtime/logs`。
2. **检查 Java ≥ 17**：`JAVA_HOME` → 系统 `java` → `.tools/jdk17`；
   都没有则从 Adoptium 下载 Temurin 17（自动识别 `linux/mac` × `x64/aarch64`）解压到 `.tools/jdk17`。
3. **检查 Maven**：系统 `mvn` → `.tools/maven`；
   都没有则从 `archive.apache.org`（备用 `repo1.maven.org`）下载 3.9.9 解压到 `.tools/maven`。
4. **编译打包**：`mvn -B -q clean package -DskipTests`。
5. **启动**：`nohup java -jar target/shine-xumis.jar …`，日志写到 `runtime/logs/app.log`，
   写入 PID 到 `runtime/app.pid`，并轮询首页最长 2 分钟等待就绪。

### 第 5 步：验证
```bash
curl -I http://localhost:8080/          # 期望 HTTP/1.1 200
tail -f runtime/logs/app.log            # 实时看日志
./start.sh --stop                       # 停止
```

---

## 方式三：Docker（跨平台一致，含 MySQL）

### 第 1 步：安装 Docker Desktop（Windows / macOS）或 Docker Engine（Linux）
> 这是唯一需要你预先安装的软件。

### 第 2 步：构建并启动
```bash
docker compose up -d --build
```
内部步骤：
1. `builder` 阶段：`maven:3.9.9-eclipse-temurin-17` 容器内先 `dependency:go-offline` 缓存依赖，再 `clean package`。
2. `runtime` 阶段：`eclipse-temurin:17-jre-alpine` 只带 JRE，复制 jar 启动，镜像更小。
3. `mysql:8.0` 健康检查通过后，应用以 `prod` profile 连接 MySQL，数据落在 `mysql-data` 卷。
4. 应用暴露 8080，内置 `HEALTHCHECK` 每 30 秒探测首页。

### 第 3 步：常用命令
```bash
docker compose logs -f app        # 看应用日志
docker compose ps                 # 看状态
docker compose down               # 停止并移除容器（数据卷保留）
```

### 第 4 步：只跑单容器（不开 MySQL，用内置文件库预览）
```bash
docker build -t shine-xumis:1.0 .
docker run -d -p 8080:8080 -v shine-data:/app/data -v shine-uploads:/app/uploads shine-xumis:1.0
```

---

## 一键部署后：数据存在哪、怎么备份

| 内容 | 位置 | 备份方式 |
|------|------|----------|
| 商品 / 订单 / 评价 / 订阅 | MySQL `shine` 库 | `mysqldump shine > backup.sql` |
| 客户上传图 / 商品图 | `uploads/` | 复制 `uploads/` 目录 |
| 运行日志 | `runtime/logs/` | 可定期清理 |

> 默认数据源为 **MySQL 8**（`jdbc:mysql://127.0.0.1:3306/shine`），首次启动自动建库建表。
> 本机没有 MySQL 时可用 `./start.sh --h2` 走内置文件库临时预览，但不要用于生产。

---

## 常见问题（按步骤自查）

| 现象 | 原因 | 处理 |
|------|------|------|
| 卡在「下载 JDK / Maven」很久 | 网络慢或被墙 | 手工下载 JDK 17 解压到 `.tools/jdk17`，Maven 解压到 `.tools/maven`，脚本会自动识别 |
| `自动下载 JDK 失败` | 无网络 / 代理拦截 | 手动安装 JDK 17 并设置 `JAVA_HOME`，重跑脚本即可跳过下载 |
| `构建失败` | 依赖下载中断 | 重跑一次；或删掉 `~/.m2/repository` 下残缺目录后重试 |
| `端口被占用` | 8080 已被占用 | `./start.sh --port=9090` 或 `start.bat 9090` |
| `启动超时` | 应用启动异常 | 查看 `runtime/logs/app.log` 末尾的异常堆栈 |
| Windows 提示「禁止运行脚本」 | PowerShell 执行策略 | 已用 `-ExecutionPolicy Bypass`；仍报错时用「以管理员身份运行」执行一次 |
| 页面样式没生效 | 浏览器缓存 | 强制刷新 `Ctrl/Cmd + Shift + R` |
| 改了代码没生效 | 用了旧 jar | 重新执行完整流程（不要加 `--skip-build`） |

---

## 上线生产（简要）

一键脚本默认跑 `dev`（连接本机 MySQL，演示后台密钥）。正式上线请按 **DEPLOYMENT.md** 完成：
1. 准备 MySQL 8，设置 `DB_*` 环境变量与专用账号；
2. 设置强随机 `ADMIN_KEY`、`MOBILE_API_KEY`；
3. 用 `--prod` 启动（`./start.sh --prod` 或 `SPRING_PROFILES_ACTIVE=prod`）；
4. 配置 Nginx 反向代理 + HTTPS（443）；
5. `/admin` 接入正式鉴权（Spring Security / OAuth2）。
