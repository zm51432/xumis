# APK 正式签名密钥档案

> **这是整个 App 最重要的一份凭证。**
> 丢失或忘记口令 = 已安装的用户永远无法覆盖升级，只能换包名重新上架，老用户、评价、下载量全部作废。

自 **1.5.1（versionCode 7）** 起，所有对外发布的安装包都固定使用本节这把密钥。

---

## 1. 证书信息

| 项目 | 值 |
|---|---|
| 密钥库文件 | `shine-release.jks`（PKCS12） |
| 别名 keyAlias | `shine` |
| 密钥算法 | RSA 2048 / 签名 SHA384withRSA |
| 证书 DN | `CN=shine Star-Heart, OU=Mobile, O=shine, L=Beijing, ST=Beijing, C=CN` |
| 序列号 | `310b20c252011dd2` |
| 有效期 | 2026-11-02 → 2054-03-20（10000 天） |
| 证书 SHA-1 | `BC:11:47:3B:92:6B:9B:FA:DE:23:A9:ED:0F:3D:54:B3:64:D0:5C:CE` |
| **证书 SHA-256** | `E5:AC:66:AC:BF:A9:81:4D:46:C3:ED:71:42:48:90:E3:A1:C6:9D:03:E1:08:32:B8:52:29:8E:12:44:60:F0:72` |
| 密钥库文件 SHA-256 | `fddf2a5827be38be8e3d16ebdf70e925c0c991ef8622c0fe4ed222a8705c3bf5` |

管理台「签名基线」显示的指纹，应始终是上面这条 **E5:AC:66:AC…:F0:72**。
如果哪天它变成了别的值，说明有人推送了换签的包——立刻查操作日志。

---

## 2. 三个存放位置

| 位置 | 用途 | 是否入库 |
|---|---|---|
| `android/keys/shine-release.jks` | 构建时 Gradle 直接读取 | ❌ 已 `.gitignore` |
| `android/keystore.properties` | 口令与别名（Gradle 读取） | ❌ 已 `.gitignore` |
| `~/android-keys/shine-release.jks` | 本机独立备份，脱离项目目录防误删 | — |
| `~/.shine/keystore-vault/` | 本机保险库：jks + cert-info + README + properties（目录 700 / 文件 600） | — |
| 服务器 `/root/.shine-release/` | 异地备份：jks + cert-info + README（目录 700 / 文件 600，**不放口令**） | — |

四处 jks 的 SHA-256 应完全一致，2026-11-02 实测均为
`fddf2a5827be38be8e3d16ebdf70e925c0c991ef8622c0fe4ed222a8705c3bf5`。

服务器备份**刻意不放 `keystore.properties`**：口令不落在业务服务器上。
口令本机保存于 `android/keystore.properties` 与 `~/.shine/keystore-vault/keystore.properties`。
服务端、网站后台、数据库等口令记在 `~/.shine/server-credentials.md`（600，不入库）。

口令**只写在 `keystore.properties`**，不写进任何 markdown、commit message 或聊天记录。
`keystore.properties` 的格式：

```properties
storeFile=keys/shine-release.jks
storePassword=******
keyAlias=shine
keyPassword=******
```

路径相对 `android/` 工程根。换机器构建时，只要带上 `keys/shine-release.jks` 与
`keystore.properties` 两个文件，就能出同签名的包。

**另外请自行离线保存一份**（加密 U 盘 / 加密云盘），与服务器、本机形成三地冗余。
`~/Downloads/shine-release-keystore_20261102/` 只是交付目录，含明文口令，不要长期留在下载区，
更不要放进任何网盘同步目录或聊天记录。

---

## 3. 构建与发布

`android/app/build.gradle` 已接好签名：`release` 与 `debug` **都用这把正式密钥**，
这样真机测试包能直接覆盖安装正式包，升级链路（OTA 校验 + 系统安装器）在测试阶段就与线上一致。

```bash
cd android
export JAVA_HOME=$PWD/../.tools/jdk17/jdk-17.0.2.jdk/Contents/Home

# 发布前：改 app/build.gradle 里的 versionCode / versionName（versionCode 必须递增）
./gradlew --offline assembleRelease
# 产物：app/build/outputs/apk/release/app-release.apk

# 核对签名（必须先看这一步）
$HOME/Android/sdk/build-tools/<版本>/apksigner verify --print-certs \
  app/build/outputs/apk/release/app-release.apk
# 期望：certificate SHA-256 digest: e5ac66acbfa9814d46c3ed71424890e3a1c69d03e10832b852298e124460f072
```

然后打开 <https://shine.xumois.cn/apk/ota/> → 拖入 APK → 填更新说明 →
「上传并立即推送」。管理台会自动做签名校验，**不是这把密钥签的包会被拦下**。

若 `keystore.properties` 缺失，`assembleRelease` 仍能编译，但产物是 unsigned 包——
那种包不可发布，管理台也不会识别到签名证书。

---

## 4. 签名基线机制（管理台）

- 首次推送成功后，服务端会把该版本的证书指纹锁定为**签名基线**，记入 `state.json`
  的 `signer.trusted`，并写一条「建立签名基线」审计日志。
- 之后每个包推送前都会比对基线，不一致直接拒绝，并提示两个指纹的前 23 位。
- 确认要换签时，有两处入口：
  1. 版本列表里点「设为签名基线」（推荐，先把基线切过去）；
  2. 推送被拒时点确认，勾选「换签后继续推送」（`allowResign`，会记「换签推送」日志）。
- 同版本号重传且换了密钥时，服务端会先把**旧包的指纹**锁成基线，
  避免「自己跟自己比」直接放行。

### 当前基线状态（2026-11-02 21:17 CST）

| 项 | 值 |
|---|---|
| 基线指纹 | `E5:AC:66:AC:BF:A9:81:4D:46:C3:ED:71:42:48:90:E3:A1:C6:9D:03:E1:08:32:B8:52:29:8E:12:44:60:F0:72` |
| 来源版本 | v1.5.3（versionCode 9） |
| 设置方式 | 管理台「设为签名基线」（`POST /apk/ota/api/trust-signer`） |
| 线上各包比对 | 1.5.1 ✓ / 1.5.2 ✓ / 1.5.3 ✓ 均与本密钥一致；1.5.0 为旧 debug 签名（`01:B8:B0:9C…`），仅存档不可再推 |

实测拦截：对 1.5.0 发起推送被拒，返回
`签名不一致：线上基线证书指纹 E5:AC:66:AC:BF:A9:81:4D，本包为 01:B8:B0:9C:E4:84:30:3A`，
且线上版本未被改动（`check?vc=9` 仍返回 `update:false`）。

---

## 5. 换签的代价

Android 只认「同包名 + 同签名」为同一个应用。换密钥后：

- 老用户**装不上**新包（系统报「应用未安装 / 签名不一致」），必须卸载重装；
- 卸载会丢失本地数据（登录状态、购物车、离线缓存）；
- 服务端会拒绝推送换签包，这是保护，不是故障。

本项目从 debug 签名切换到这把正式密钥时已经历过一次：1.5.0 及更早的用户需要
卸载后重新安装 1.5.1，从 1.5.1 起才能正常覆盖升级。**此后不要再换密钥。**

---

## 6. 校验与找回

```bash
# 看密钥库里的证书
keytool -list -v -keystore android/keys/shine-release.jks -alias shine

# 看已发布 APK 的签名
apksigner verify --print-certs app-release.apk

# 看密钥库文件本身是否被改动（应与备份一致）
shasum -a 256 android/keys/shine-release.jks
```

多份密钥库文件的 SHA-256 应当完全相同。若不一致，说明有一份损坏或被替换，
以服务器备份 + 本机备份中一致的那份为准，并重新核对证书指纹。

---

## 7. 检查清单（每次发版）

- [ ] `versionCode` 比上一版大
- [ ] `./gradlew assembleRelease` 成功，无 lint 阻断
- [ ] `apksigner verify --print-certs` 输出 `e5ac66ac…60f072`
- [ ] 上传管理台后，包指纹与「签名基线」一致
- [ ] 更新说明写清楚用户能感知的变化
- [ ] 发布后用一台已装旧版的手机，走一遍「检查更新 → 下载 → 安装」
