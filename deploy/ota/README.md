# APK 版本推送服务（/apk/ota）

给「shine 星心」安卓客户端做**版本发布与一键推送**的小服务：站长在网页上传新安装包，
一键设为线上版本；所有已安装的客户端下次打开（或手动检查）就会收到升级提示，在 App 内
直接下载安装。

- 管理台：<https://shine.xumois.cn/apk/ota/>
- 客户端检查接口：`GET /apk/ota/api/check?vc=<versionCode>&ch=stable`
- 安装包下载：`GET /apk/ota/dl/<file>.apk`（支持 Range 断点续传）

## 一、结构

```
deploy/ota/
├── ota_server.py       服务本体（Python 3 标准库，零第三方依赖）
├── web/admin.html      发布管理台（单页）
├── shine-ota.service   systemd 单元
├── nginx-ota.conf      nginx 反代片段（写入 /etc/nginx/ota-location.conf）
├── install.sh          一键安装（服务器上执行）
└── README.md
```

服务器上的落点：

| 路径 | 用途 |
|---|---|
| `/opt/shine-ota/ota_server.py` | 服务程序 |
| `/opt/shine-ota/web/admin.html` | 管理台页面 |
| `/var/lib/shine-ota/state.json` | 版本清单、统计、审计日志 |
| `/var/lib/shine-ota/admin.json` | 口令哈希 + 会话密钥（600 权限） |
| `/var/lib/shine-ota/releases/*.apk` | 所有历史安装包 |
| `/var/www/download/shine-xumis.apk` | 下载页指向的安装包（推送时自动同步） |
| `/var/www/download/version.json` | 下载页读取的版本信息（推送时自动同步） |
| `/var/log/shine-ota.log` | 运行日志 |

进程只监听 `127.0.0.1:8095`，对外由 nginx 的 `/apk/ota` 反代，走站点同一张证书。

## 二、部署 / 升级

```bash
# 本地打包上传
scp deploy/ota/ota_server.py deploy/ota/shine-ota.service \
    deploy/ota/nginx-ota.conf deploy/ota/install.sh \
    deploy/ota/web/admin.html root@<server>:/tmp/shine-ota-pkg/

# 服务器上执行（幂等，可反复跑）
ssh root@<server> 'bash /tmp/shine-ota-pkg/install.sh'
```

脚本做的事：装文件 → 起 systemd → 幂等插入 nginx include → `nginx -t` 后 reload →
自测三个 URL → 打印初始口令。重复执行只更新文件与重启服务，不会丢数据。

只改了 `web/admin.html` 时，直接覆盖 `/opt/shine-ota/web/admin.html` 即可，不用重启
（静态文件按请求读取）。

## 三、日常发布流程

1. 打开 <https://shine.xumois.cn/apk/ota/>，输入管理口令登录。
2. 选择（或拖入）打好包的 `.apk`，服务端会立刻解析出**包名 / versionCode /
   versionName / minSdk / 签名指纹 / SHA-256** 并显示出来，防止传错文件。
3. 填「更新说明」（会原样显示在用户手机的升级页里），选渠道，决定是否强制更新。
4. 「上传并立即推送」→ 客户端下次检查即可收到；`/download/` 页同时自动更新。
   不想马上推送就点「仅上传，稍后推送」，之后在版本列表里点「推送上线」。

版本列表里可以随时：**推送上线**（含回滚到旧版本）、**编辑**（改说明 / 强制开关 /
最低支持版本 / 渠道）、**删除**（线上版本不允许删除，需先推别的）。

## 四、客户端行为

`com.shine.xumis.app` 1.5.0 起内置升级能力：

- 进入主界面时静默检查一次，6 小时内不重复请求；
- 服务端返回 `update=true` 时打开更新页，展示版本号、体积、更新说明；
- 「立即更新」→ 下载（进度可见）→ 校验大小与 SHA-256 → 调系统安装器；
- 用户点过「稍后再说」的同一版本不再自动弹窗（强制更新除外）；
- 设置 → 关于 →「检查更新」可随时手动查。

## 五、接口

### 检查更新（公开）

```bash
curl 'https://shine.xumois.cn/apk/ota/api/check?vc=5&ch=stable'
```

```json
{
  "ok": true,
  "update": true,
  "force": false,
  "channel": "stable",
  "installedVersionCode": 5,
  "latest": {
    "versionCode": 6,
    "versionName": "1.5.0",
    "size": 21308304,
    "sizeText": "20.3 MB",
    "sha256": "4692260f…",
    "notes": "· …",
    "force": false,
    "minSupported": 0,
    "publishedAtText": "2026-10-30 04:24",
    "url": "https://shine.xumois.cn/apk/ota/dl/shine-1.5.0-6-xxxx.apk"
  },
  "message": "发现新版本 1.5.0"
}
```

`force=true` 的条件：该版本勾了强制更新，**或** 客户端 versionCode 低于
`minSupported`。`/apk/ota/api/latest.json`、`/apk/ota/api/versions` 为公开只读信息。

### 管理与上传（需登录）

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/apk/ota/api/login` | `{"password":"…"}`，成功后下发 HttpOnly 会话 Cookie |
| POST | `/apk/ota/api/upload` | multipart：`apk`、`notes`、`channel`、`force`、`minSupported`、`publish` |
| POST | `/apk/ota/api/publish` | `{"id":"…","channel":"stable"}` 推送上线 |
| PATCH | `/apk/ota/api/release/<id>` | 改 `notes` / `force` / `minSupported` / `channel` |
| DELETE | `/apk/ota/api/release/<id>` | 删除版本（线上版本禁止） |
| GET | `/apk/ota/api/overview` | 管理台数据源 |
| POST | `/apk/ota/api/password` | `{"old":"…","new":"…"}`，改完踢掉所有会话 |

所有写操作必须带 `X-OTA-Request: 1` 请求头（配合 `SameSite=Strict` 防 CSRF）。

## 六、安全设计

- **口令**：PBKDF2-HMAC-SHA256（20 万次）+ 随机盐，不存明文；初始口令随机生成，
  写在 `admin.json`（600），登录后管理台顶部会一直提示修改。
- **会话**：HMAC-SHA256 签名令牌，HttpOnly + Secure + SameSite=Strict，默认 24 小时；
  改口令会轮换签名密钥，旧会话立即失效。
- **防爆破**：同一 IP 10 分钟内 6 次失败即锁定；登录失败也进审计日志。
- **上传校验**：包名必须等于 `com.shine.xumis.app`；必须能读出 versionCode；
  **签名指纹必须与当前线上版本一致**，换签的包会被拒绝（换签后老用户无法覆盖安装）。
- **客户端**：只接受 `https` 且与站点同域的安装包地址；下载后校验 SHA-256 与字节数；
  安装走系统安装器，不做静默安装。

## 七、运维

```bash
systemctl status shine-ota        # 状态
journalctl -u shine-ota -n 50     # 或 tail /var/log/shine-ota.log
systemctl restart shine-ota       # 重启
curl -s http://127.0.0.1:8095/apk/ota/healthz    # 健康检查
```

备份：`/var/lib/shine-ota`（`state.json` + `admin.json` + `releases/*.apk`）整个目录即可。

修改端口 / 域名 / 下载目录等参数见 `shine-ota.service` 里的 `Environment=`
（`OTA_PORT`、`OTA_PUBLIC_BASE`、`OTA_DOWNLOAD_DIR`、`OTA_MAX_UPLOAD_MB` 等）。

### 回滚

版本列表里对上一版本点「推送上线」即可。注意：**已经升级到新版本的客户端不会自动降级**
（Android 不允许同包名安装更低 versionCode），回滚只影响还没升级的用户和下载页。
真要收回某个版本，请发一个 versionCode 更大的修复版，勾上强制更新。

### 常见问题

| 现象 | 处理 |
|---|---|
| 上传报「包名不匹配」 | 传成了别的 App 的包，或包名被改过 |
| 上传报「签名不一致」 | 换了签名密钥打包。用同一 keystore 重打，或在客户端侧做迁移包 |
| 客户端不提示更新 | 确认 versionCode 大于客户端版本；确认推的是 stable 渠道；客户端 6 小时节流 |
| 用户装不上 | Android 8+ 需允许「安装未知应用」，App 内会引导；部分厂商需在系统设置里再开一次 |
| 管理台打不开（404） | 检查 `/etc/nginx/ota-location.conf` 是否被 include、`systemctl status shine-ota` |
