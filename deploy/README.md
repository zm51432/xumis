# shine星心 独立站 · 生产部署指南

目标：把 `xumis` Spring Boot 应用通过 **Nginx** 或 **Caddy** 反代到公网，启用 HTTPS，让别人能访问并正常分享（OG 卡片不裂图）。

---

## 1. 构建产物

```bash
cd xumis
export JAVA_HOME=$PWD/.tools/jdk17/jdk-17.0.2.jdk/Contents/Home
export PATH=$JAVA_HOME/bin:$PWD/.tools/maven/bin:$PATH

mvn -o package -DskipTests
# 产物：target/shine-xumis.jar
```

把 `target/shine-xumis.jar` 传到服务器（如 `/opt/xumis/`）。

---

## 2. 数据库

- **演示 / 快速预览**：用内置 H2 文件库，无需装 MySQL。运行时加 `--spring.profiles.active=h2`。
- **生产**：装 MySQL，建库后通过环境变量覆盖（无需改代码）：

```bash
DB_HOST=127.0.0.1 DB_NAME=shine DB_USER=shine DB_PASSWORD='xxxx' \
  java -jar /opt/xumis/shine-xumis.jar
```

> 默认 `ddl-auto: update`，首次启动自动建表；首次启动会在商品表为空时自动播种 12 个本地图商品，并创建站长账号 `admin / Admin@12345`（**登录后立即改密码**）。

---

## 3. 以服务方式运行（推荐）

```bash
sudo cp deploy/systemd/xumis.service /etc/systemd/system/xumis.service
sudo systemctl daemon-reload
sudo systemctl enable --now xumis
sudo journalctl -u xumis -f
```

---

## 4. 域名与防火墙

- 域名 A 记录指向服务器公网 IP。
- 放行 80 / 443（以及你的 SSH 22）。

---

## 5. 选一个反向代理

### 方案 A：Nginx（证书用 certbot 申请）

```bash
sudo cp deploy/nginx.conf /etc/nginx/sites-available/xumis
sudo ln -s /etc/nginx/sites-available/xumis /etc/nginx/sites-enabled/
# 改 example.com 为你的域名后：
sudo nginx -t
sudo certbot --nginx -d example.com -d www.example.com
sudo systemctl reload nginx
```

### 方案 B：Caddy（自动 HTTPS，最省事）

```bash
# 安装：https://caddyserver.com/docs/install
# 改 Caddyfile 里的 example.com 为你的域名后：
caddy run --config deploy/Caddyfile
# 或常驻： caddy start --config deploy/Caddyfile
```

---

## 6. ⚠️ 关键：绝对地址必须指向公网域名

社交分享（微信 / Facebook / X）的 OG 图与链接由后端 `GlobalModelAdvice.siteBaseUrl()` 拼**绝对地址**。

- 若后端拿不到真实协议/域名（看到的是 `http://127.0.0.1:8080/...`），分享卡片会裂图。
- 修复：`application.yml` 已加 `server.forward-headers-strategy: native`，后端会读取反代传来的 `X-Forwarded-Proto/Host`，自动生成 `https://你的域名/...`。

部署后用 curl 自测：

```bash
curl -s https://你的域名/product/1 | grep -o 'og:image" content="[^"]*"'
# 期望输出：og:image" content="https://你的域名/img/products/p01_...png"
```

---

## 7. 上线前检查清单

- [ ] HTTPS 可达，HTTP 自动跳 HTTPS
- [ ] `og:image` / `og:url` 为绝对 https 地址（见第 6 步）
- [ ] 支付网关已接入（Stripe / 支付宝 / 微信 / PayPal）—— 否则只能展示不能成交
- [ ] 后台 `/admin` 密码已修改；非演示环境关闭 `app.admin-allow-key`
- [ ] `/h2-console` 在生产关闭或加 IP 白名单
- [ ] 补齐内容页：关于我们 / 联系方式 / 隐私政策 / 退换货 / 物流时效
- [ ] 真实商品多视角图、尺寸表、批发询价入口
