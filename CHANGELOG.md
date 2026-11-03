# 变更记录 CHANGELOG

## 2026-11-04（第十八次）— 客户端 2.3.0：单品详情页改版（对标京东/淘宝/拼多多）

- **首页重构为「单品详情页」**：本程序只销售单一商品，首页不再是一堆商品网格，而是对标京东/淘宝/拼多多的商品详情排版——全幅主图 + 醒目红色价格 + 包邮/限时特惠角标 + 标题与英文名 + 正品保证/七天无理由/闪电发货服务承诺 + 规格参数卡 + 宝贝详情卡 + 吸底「加入购物车 / 立即购买」操作栏。
- **单品由后台配置**：首页取后台商品列表的第一件作为唯一在售单品（名称/价格/图/描述/材质均由后台控制，可随时改）。不新增后端接口，零部署风险；原 `products` 接口直接复用。
- **保留全部原有功能**：搜索、分类、购物车、账户、来图定制、订单等入口与能力均未删除，仅首页视觉收敛为单品形态（按「只改版式不删功能」原则）。
- **离线兜底不变**：无网络时回退到上次缓存的单品快照并提示，下拉即可刷新。

---

## 2026-11-04（第十七次）— 客户端 2.2.0：来图定制支持上传参考图 + 体验打磨

- **来图定制支持上传参考图（核心）**：此前「来图定制」只能填文字，工匠拿不到用户想要的图样。现新增参考图能力——支持「从相册选择」与「拍照」两种来源，选图后即时预览、可一键移除；提交时连同文字与图片一并上传（`POST /api/m/custom-ideas`，multipart `referenceImage`），服务端存盘后回写参考图链接。服务端此前已具备该能力，本次补齐客户端。
- **修复品类字段导致的提交 400（潜伏 bug）**：旧版把「期望品类」当自由文本发往服务端，而服务端该字段是枚举（饰品/手工品/其他），非枚举值会触发 400、导致只要填了品类就提交失败。现改为下拉选择，发送合法枚举名，提交 100% 可靠。
- **账户信息预填**：已登录用户进入来图定制页，姓名/邮箱自动带入，减少重复输入。
- **提交反馈升级**：成功后用 Material 对话框明确告知「已收到定制需求，将尽快通过邮箱联系」，替代原来一闪而过的 Toast 直接关闭。
- **权限体验**：无相机机型自动隐藏「拍照」项；拒绝相机权限时引导改用相册，不阻塞流程；兼容小米/OPPO/vivo 的「部分照片」授权。
- 附带产出《真机回归测试清单.md》（23 项用例），供真机逐项验证。

---

## 2026-11-04（第十六次）— 客户端 2.1.0：订单可回看 + 关键操作二次确认

- **游客订单可回看**：此前未登录下单后，订单号只在成功页一闪而过、「我的订单」里再也找不到。现记忆下单邮箱，在「我的订单」里按邮箱回查，并允许从结算成功页一键「查看订单」详情（无需登录也能看）。
- **结算成功页增强**：下单成功后新增「查看订单」按钮直达订单详情；「完成」改为「返回首页」，避免层层返回停留在详情/结算页。
- **删除购物车商品二次确认**：移除前弹窗确认，防止误触丢失商品。
- **退出登录二次确认 + 角标归零**：退出前弹窗确认；退出后购物车角标立即清零，避免残留数字误导。
- 上述改动均为原生界面，不涉及网页跳转；后端无需改动（订单查询已支持邮箱参数）。

---

## 2026-11-03（第十五次）— 客户端 2.0.2：Logo 改为「shine 星心」文字标

- 品牌 Logo 改为「shine 星心」文字标：淡黄底 + 红色文字（shine 小字 + 星心大字）+ 金色星光点缀，图标各密度与启动页同步。

---

## 2026-11-03（第十四次）— 客户端 2.0.1：原生结算页商品摘要

- 原生结算页新增「商品摘要」区块，逐条列出商品名 × 数量与小计（此前仅显示「共 N 件 · 合计」）。
- 收货信息、支付方式选择、提交订单（立即付款）保持不变，全程不跳网页。

---

## 2026-11-03（第十三次）— 客户端 2.0.0：全原生版 + 淡黄红主题

- 订单详情原生页（`GET /api/m/orders/{orderNo}` + OrderDetailActivity，订单列表点卡片进详情）。
- 搜索页首点即弹输入法（`isFocusableInTouchMode` + `postDelayed` 优化）。
- 移除全部网页入口，均改原生：
  - 找回密码（ForgotPasswordActivity + ResetPasswordActivity，后端 `POST /api/m/auth/forgot-password|reset-password`，重置邮件附带令牌）。
  - 离线商品册（OfflineCatalogActivity，展示缓存商品原生网格）。
  - 配送与售后条款（PoliciesActivity，内置静态文本）。
  - 来图定制（CustomIdeaActivity → `/api/m/custom-ideas`）与询盘（InquiryActivity → `/api/m/inquiries`）。
  - 注册本已原生。
- 全新主题统一淡黄底 + 红；App 图标改为淡黄底 + 红星心（各密度 PNG），启动页渐变同步改淡黄。

---

## 2026-11-03（第十二次）— 客户端 1.10.1：首页浏览型

- 首页默认按「已售」从多到少排序（沿用 /download/hot.json 热销榜映射）。
- 去掉商品卡片上的快速「加入购物车」按钮：点击卡片（图片）进详情页，再在详情页做「加入购物车 / 立即购买」。

---

## 2026-11-03（第十一次）— 客户端 1.10.0：「我的」页堆叠账户菜单

- 「我的」页改为堆叠账户菜单：收货资料 / 修改密码 / 我的订单 / 支付方式 + 退出登录。
- 新增原生页：收货资料（编辑姓名/电话/国家/地址）、修改密码（原+新密码）、我的订单（独立订单列表）、支付方式（选默认支付渠道，结算页自动预选）。
- 后端新增 `POST /api/m/auth/password`（改密码），已部署并重启服务。

---

## 2026-11-03（第十次）— 客户端 1.9.1：结算付款界面 + 首页口号

- 结算页更名「结算付款」、提交按钮改为「立即付款」，下单成功页显示「订单号 + 付款方式」，明确「立即购买 → 付款」的路径。
- 首页滚动公告改为店铺口号「只看销量，不看评论。」（重复以形成跑马灯滚动）。

---

## 2026-11-02（第九次）— 客户端 1.9.0：全原生购物闭环 + 全新图标

**背景**：用户要求「去除 50 用户痛点文档 / 所有界面都是原生 App 界面 / 重新设计 logo / 购买等操作只在 App 内完成、不再跳转 Web」。本轮把购物车、结算、我的账户从 WebView 全部改成原生界面，并重做品牌图标。

### 全原生购物闭环（不再跳 Web）
- 后端新增 `MobileCommerceController`（`/api/m` 前缀、基于 HttpSession、复用 CartService/OrderService/UserAccountService）：
  - `GET /cart`、`POST /cart/add|update|remove`（购物车）
  - `POST /orders`（下单，返回订单号）、`GET /orders`（我的订单）
  - `GET /me`、`POST /auth/login|register|logout|profile`（账户）
  - 走 `/api/**` 已由 CsrfInterceptor 放行，App 侧共享 `android.webkit.CookieManager` 作为会话 Cookie 存储，与站点同会话。
- App 新增：
  - 原生购物车（列表 + 数量步进 + 删除 + 合计 + 去结算），替换原 WebView 购物车。
  - 原生结算页（收货信息 + 支付方式 + 提交订单 → 显示订单号），替换原「跳 Web 落地页」。
  - 原生「我的」（登录/注册 + 资料 + 我的订单 + 退出），替换原 WebView 账户页。
  - 商品详情「立即购买」改为原生加购后直进结算页。
  - 新增 `NativeSession`（共享 OkHttp + 持久化 CookieJar）与 `CommerceApi`，`CartApi` 改为委托原生接口。

### 品牌图标
- 用 AI 重新设计「星心火花」图标（珊瑚红爱心 + 金色四角星光，暖橙渐变底），去除水印后生成各密度 `ic_launcher`/`ic_launcher_round` PNG，移除旧自适应矢量图标，启动页同步展示圆角图标。

### 其它
- 移除 `docs/独立站用户痛点50问自解.md` 及其 README / CHANGELOG 引用。

### 验证
- 后端 jar 离线重打包并部署到 `/opt/xumis`，`systemctl` 重启后 curl 实测购物车增删改查与会话一致性通过。
- App `assembleRelease` 编译通过（仅无害警告），正式密钥签名，OTA stable 发布（1.8.0 → 1.9.0）。

---

## 2026-11-02（第八次）— 客户端 1.8.0：星心焕新 · 体验与质感升级

**背景**：用户要求「不断自我优化，从 logo 到 UI 再到客户使用、以及产品美观」。本轮聚焦启动页质感、导航层次、站内页（WebView 外壳）观感、首页与商品卡的视觉打磨，以及一处常用手势体验。

### 启动页质感
- 启动页改为品牌渐变背景（暖黄→浅红晕），星心 Logo 放进白色圆角卡片并带柔光投影，更跳、更「品牌」。
- 新增进场动画：Logo 白卡缩放淡入、标题与副标依次轻移浮现，弱化「加载感」。

### 导航层次
- 底部导航选中态文字加粗放大（`itemTextAppearanceActive`），强化当前位置；图标/文字仍保持品牌红选中、浅棕未选。

### 站内页（WebView 外壳）观感 v2
- 去掉点击高亮（`-webkit-tap-highlight-color`）、开启平滑滚动与惯性滚动。
- 输入框/下拉框统一圆角、聚焦时红边 + 红晕，字号 16sp 规避 iOS 自动放大。
- 卡片类容器圆角 + 柔红阴影，产品缩略图圆角。
- 隐藏站点残留的在线客服 / 浮层（避免遮挡内容）。
- 保留 1.7.0 的购物车卡片化与隐藏站点 nav/footer。

### 首页与商品卡
- 滚动公告条换成喇叭图标 + 渐变底，分区标题改为带火苗图标的「热销好物」。
- 商品卡新增销量「热销」红底角标（销量 ≥ 20）；「已售 N 件」改为柔和奶油药丸，弱化生硬感。

### 体验手势
- 双击首页底部 Tab 自动回滚到顶部（常见 App 手势，便于长列表快速回到开头）。

---

## 2026-11-02（第七次）— 客户端 1.7.0：星心品牌升级 + 加购闭环 + 首页改版

**背景**：按用户反馈集中打磨「品牌识别 / 购物车可用性 / 商品操作意图 / 信息完整性 / 首页导购效率」五件事，
以「shine 星心（Star-Heart）」为主题重做视觉，并补齐原生加购与销量排序两类此前缺失的能力。

### 品牌与视觉
- 全新「星心」Logo：红色爱心 + 金星自适应图标（foreground/background 矢量 + 各密度 mipmap），启动页同步展示星心前景图。
- 图标与启动页统一到暖黄光晕 + 红心金星，替换旧版 `ic_launcher`。

### 购物车可用性
- 购物车页改卡片式 flex 布局，注入 CSS 把站点 `.cart-table`（原 `min-width:560px`）拆成卡片，解决小屏横向溢出 / 无法完整查看的问题。

### 商品操作意图（加购闭环）
- 商品卡片右下角新增圆形「加入购物车」按钮（原生调用 `CartApi`，带 loading 态，避免手快点两次）。
- 卡片点击进入商品详情（不再误跳购物车）。
- 详情页底部明确两个动作：「立即购买」（打开商品落地页）/「加入购物车」（原生加购，Toast 显示件数）。
- 加购成功后通过 `CartApi.changes` 广播，底栏购物车角标实时 +1，`WebFragment` 切回时若购物车有变动自动重载。
- 原生加购复用 WebView `CookieManager` 会话 Cookie + 商品页 CSRF 令牌，保证与站内购物车是同一个购物车。

### 信息完整性
- 注入 CSS 隐藏站点自带顶部导航 / 底部 `mobile-nav` / `footer` / cookie 条，修复「我的」页被站点元素叠层遮挡、信息看不全的问题。

### 首页导购效率
- 首页顶部新增滚动公告条（跑马灯，对齐网站公告内容）。
- 首页首屏按销量优先排序：拉取服务端热销榜 `/download/hot.json`，把销量补进商品并降序；翻页页保持服务端顺序。
- 商品卡片展示「已售 N 件」。

### 配套后端
- 服务端 `gen_hot.py`（crontab 每 10 分钟）扫描订单汇总热销榜，落盘 `/download/hot.json`，App 拉取做销量排序与「已售」展示；拉不到降级为默认顺序，不影响主流程。

版本 `versionCode 11 / versionName 1.7.0`，正式密钥签名（SHA-256 `E5:AC:66:AC…F0:72`），
已推送 OTA stable：`https://shine.xumois.cn/apk/ota/dl/shine-1.7.0-11-3603857ce8.apk`。

### 验证
- 离线 `assembleRelease` 编译通过（仅警告），APK 19.0 MB，证书指纹与签名基线一致（`signerMatched=true`）。
- OTA：1.6.0 客户端 `check?vc=10` 返回 `update:true → 1.7.0`；1.7.0 客户端 `check?vc=11` 返回 `已是最新`；下载页 `version.json` 已同步 1.7.0。
- 注意：本环境无 Android 模拟器，真机逐项回归（启动页 Logo、公告条、销量排序、加购角标联动、购物车卡片、我的页完整）需在一台已装旧版的手机上走一遍「检查更新 → 下载 → 安装」确认。

## 2026-11-02（第六次）— 客户端 1.6.0：统一淡黄+红视觉，修交互与稳定性

**背景**：1.5.2 / 1.5.3 是在另一台设备上编译的，源码没有回到本机；这次以本仓库 1.5.1 源码为基线，
按线上 1.5.3 APK 里提取的配色与结构重建视觉，并把使用中暴露的交互问题一并修掉。

### 视觉

- 配色取自线上 APK 资源表：底 `#FFF9E6`、主色 `#D32F2F`、辅助黄 `#FFD54F`、文字 `#3B2A1E`，
  补齐 `shine_surface / yellow_soft / yellow_line / red_soft / amber_text / on_red` 六个层次色。
- 全部子页顶栏由「红底白字」改为「淡黄底深色字」，返回箭头同步改深色；状态栏改浅色底 + 深色图标。
- 商品卡改为 MaterialCardView 圆角卡（16dp + 淡黄描边），图片 12dp 圆角，价格红色加粗，
  库存标在价格右侧，标题固定两行高度保证同一行卡片对齐。
- 商品详情改为底部固定操作栏（不再跟着内容滚动到底才见按钮），图片圆角、库存做成胶囊标签。
- 深色模式沿用同一套浅色配色，避免夜间把卡片和导航刷成深色与淡黄背景拼接。

### 使用习惯与稳定性

| 问题 | 处理 |
|---|---|
| 切 Tab 每次都 new Fragment，购物车/我的每次重新加载 | 四个 Tab 懒加载 + `show/hide` 复用实例 |
| 转屏或进程重建后底部高亮与内容不一致 | `onSaveInstanceState` 记住选中 Tab 并恢复 |
| 搜索进页面不弹键盘、结果为空只弹 Toast、无分页 | 进页面自动聚焦并弹键盘、输入 400ms 防抖即搜、空态页 + 清空条件、滚动到底自动翻页 |
| 首页只有 30 条、无空态 | 20 条一页 + 预加载下一页、空态/失败态分开、显示总件数 |
| 更新检查失败也占掉 6 小时窗口 | 时间戳改为**请求成功后**才写 |
| 升级包下载中断要重下 | 改 `.part` + Range 断点续传，校验失败才删 |
| 崩溃后静默重启，用户不知道发生了什么 | 下次启动弹说明，可直达「运行状态」看日志（提示一次后清除） |
| 购物车/我的页里站点底栏与原生底栏叠加成两层 | WebView 注入 CSS 隐藏站点 `.nav/.mobile-nav/.footer`，并把站点 CSS 变量映射成 App 配色 |
| 无隐私政策入口 | 新增 DocActivity（离线可看）+ 设置「隐私政策/服务条款」两行 |
| 站内其它入口（来图定制、询盘）被隐藏后无处可达 | 设置新增「更多服务」，用完整网页打开站点 |

版本 `versionCode 10 / versionName 1.6.0`，正式密钥签名（SHA-256 `E5:AC:66:AC…F0:72`），
已推送 OTA stable：`https://shine.xumois.cn/apk/ota/dl/shine-1.6.0-10-f58acf1111.apk`。

### 验证

- Android 14 模拟器：首装、切 Tab、搜索默认列表、商品详情、购物车/我的外壳、设置、隐私政策逐页截图确认。
- 升级链路：装 1.5.3 → 检测到 1.6.0 → 下载 19.0 MB → SHA-256 校验 → 系统安装器 → 实装 `versionCode=10`。
- OTA 侧：上传时签名与基线一致（`signerMatched=true`），下载页 `version.json` 已同步 1.6.0。

## 2026-10-30（第五次）— 仓库按职责重组为五类目录

**目标**：把「网站服务端 / 安卓客户端 / 部署运维 / 文档 / 归档」分清，仓库一眼能看出结构；
同时清掉入库的编译产物（`archive/*.class`）与仓库根残留的 21MB 安装包。

### 目录调整

| 调整前 | 调整后 | 说明 |
|---|---|---|
| `pom.xml`、`src/`、`sql/`、`start.sh`、`start.bat`、`install.ps1`、`Dockerfile`、`.dockerignore` | `server/` 下同名文件 | 网站服务端整体归入 `server/`，Maven 工程根随之迁移 |
| `DEPLOYMENT.md`、`ONECLICK.md` | `docs/` 下同名文件 | 文档集中 |
| `archive/personal-site/*.class` | 已删除 | 编译产物不入库（源码与 `run.bat` 保留） |
| 仓库根 `shine.apk`（21MB） | 已移除 | 安装包由推送服务托管，不入库 |

保持不变：`android/`（客户端）、`deploy/`（nginx / systemd / 下载页 / OTA 服务）、`archive/`（归档）。
`docker-compose.yml` 保留在仓库根（`build.context` 指向 `./server`），`docker compose up -d --build` 用法不变。

### 随之更新的引用

- `server/start.sh`、`server/install.ps1`：`.tools` 查找增加回退——`server/.tools` 不存在时
  自动使用仓库根的 `.tools`，避免老环境的 JDK / Maven 缓存失效重下。
- `docker-compose.yml`：构建上下文改为 `./server`。
- 根 `README.md`：重写为项目总览（五类目录导航 + 服务端 / 客户端 / 推送服务 / 文档四段）。
- `docs/DEPLOYMENT.md`、`docs/ONECLICK.md`、`deploy/README.md`：命令路径同步为 `server/...`，
  ONECLICK 顶部加「路径说明」提示先 `cd server`。
- 新增 `server/README.md`：服务端三条启动路径与目录说明。

### 验证

- `cd server && mvn -o -DskipTests compile` 通过（91 个 class，离线）。
- `bash -n server/start.sh` 语法通过；`.tools` 回退逻辑实测解析到仓库根 `.tools`。
- `android/`、`deploy/` 内无跨目录相对路径依赖，重组不影响 Gradle 与部署脚本。


## 2026-10-30（第四次）— APK 版本推送服务 + 客户端应用内升级（1.5.0）

**目标**：在服务器上部署一套发布管理台，站长上传新安装包并**一键推送**，已安装的客户端
自动发现、下载、安装；同时把「下载页永远是最新版」变成自动同步，而不是每次手改 HTML。

### 1. 新增服务端：`deploy/ota/`（shine-ota.service）

独立小服务（Python 3 标准库，零第三方依赖），只监听 `127.0.0.1:8095`，
由 nginx 以 `/apk/ota` 反代到同一张证书下：

| 入口 | 说明 |
|---|---|
| `/apk/ota/` | 发布管理台（单页，口令登录） |
| `/apk/ota/api/check?vc=6&ch=stable` | 客户端检查更新（公开只读） |
| `/apk/ota/dl/<file>.apk` | 安装包下载，支持 Range 断点续传并计数 |

- **上传即识别**：内置 AXML 解析器直接读 APK 里的 `AndroidManifest.xml`，取出包名、
  versionCode、versionName、minSdk；不需要在服务器装 aapt。遇到 0 基/1 基底址差异时
  按「属性名字符串 + 根元素名反推」兼容，不依赖资源 ID。
- **签名一致性校验**：自带 APK Signing Block（v2/v3）解析，取签名证书 SHA-256 指纹，
  与当前线上版本比对；换签的包会被拒绝推送，避免「老用户装不上」的静默故障。
- **一键推送**：设为线上版本的同时，自动把安装包与 `version.json` 同步到
  `/var/www/download/`，下载页读取后自动显示新版本，无需人工替换文件。
- **版本管理**：推送 / 回滚（把旧版本重新设为线上）/ 编辑说明与强制开关 / 删除；
  线上版本禁止删除，避免把下载页打成 404。
- **统计与审计**：累计下载、近 7 天检查与下载曲线、客户端版本分布、30 条操作日志。
- **安全**：口令 PBKDF2（20 万次迭代）+ 随机盐；会话为 HMAC 签名令牌，
  HttpOnly + Secure + SameSite=Strict，24 小时；同 IP 10 分钟 6 次失败即锁定；
  写操作强制 `X-OTA-Request: 1` 头防 CSRF；上传包名必须等于 `com.shine.xumis.app`。
- 部署脚本 `install.sh` 幂等：装文件 → systemd → 幂等插入 nginx include →
  `nginx -t` 后 reload → 三个 URL 自测 → 打印初始口令。

### 2. 新增客户端：应用内升级（1.5.0 / versionCode 6）

- `util/UpdateChecker.kt`：检查、下载、校验三合一。
  - 只接受 `https` 且与站点同域的安装包地址；
  - 下载按字节比对大小、按 SHA-256 比对指纹，不一致立即删包并报错；
  - 并发下载用 64KB 缓冲 + 进度回调，不占内存。
- `ui/UpdateActivity.kt` + `layout/activity_update.xml`：更新页
  - 展示版本号、安装包体积、当前版本、更新说明（按行排版）；
  - 「立即更新」→ 进度条 → 下载完成自动调起系统安装器；
  - 强制更新时隐藏「稍后再说」，返回键不放行；
  - Android 8+ 未授予「安装未知应用」时，弹窗引导跳系统设置，返回后自动继续安装。
- `MainActivity`：进入前台静默检查，6 小时内不重复请求；用户点过「稍后」的同一版本
  不再弹（强制更新除外）。
- `SettingsActivity`：关于分组新增「检查更新」行，右侧显示当前版本，点击即查。
- Manifest 增加 `REQUEST_INSTALL_PACKAGES`、查看安装器的 `<queries>` 与 `UpdateActivity`；
  `file_paths.xml` 增加 `updates/` 路径给 FileProvider 授权安装器读取。
- 全流程不出现任何服务器地址，与 1.3.0 起的「界面无 URL」保持一致。

### 3. 下载页改为动态版本

`deploy/apk/index.html` 打开时读取 `/download/version.json`，覆盖版本号、体积、
按钮链接与「最新版本 · 发布于」一行，并新增 1.5.0 更新说明。推送新版本后下载页
自动是最新的，不再需要手工改 HTML。

### 4. 验证

- 服务端：`check` / 上传 / 推送 / 回滚 / 删除 / Range 下载 / `version.json` 同步全部实测；
  管理台用无头 Chrome 真实登录截图校验（登录态、版本列表、日志、7 天曲线均正常）。
- 客户端：模拟器 Android 14 上 1.5.0 → 推送 1.5.1（强制）→ 更新页显示「重要更新」
  且无「稍后」→ 下载 20.4 MB → SHA-256 通过 → 系统安装器 → 升级成功（versionCode 7）。
  随后回滚线上到 1.5.0 并删除测试版，客户端「检查更新」显示「已是最新」。
- 线上与本地 SHA-256 一致：`/var/www/download/shine-xumis.apk` 与
  `/var/lib/shine-ota/releases/shine-1.5.0-6-*.apk` 同哈希。

### 5. 注意

- **1.4.0 及更早的客户端没有升级能力**，这批用户需要从 `/download/` 手动装一次
  1.5.0，之后才能享受应用内一键升级。
- 应用内升级依赖**同一签名密钥**：debug 包与 release 包签名不同，正式发版前需要用
  同一个 keystore 出包，否则老用户无法覆盖安装（服务端会直接拒绝推送换签包）。


## 2026-10-30（第三次）— APK 直连线上服务器，服务端到端打通

**目标**：Android 客户端装到手机上即可直接使用线上服务，不需要任何手工配置；原生接口与内嵌站点页面全部指向 `https://shine.xumois.cn`。

### 1. 默认服务器地址改为线上（关键修复）

- `util/ServerConfig.kt`：`DEFAULT_URL` 从 `http://10.0.2.2:8080`（模拟器回环）改为 `https://shine.xumois.cn`。
  - 之前真机安装后必然连接失败，因为 `10.0.2.2` 只在模拟器里指向宿主机。
- 新增**升级迁移**：老版本把 `10.0.2.2:8080` 存进 SharedPreferences，升级后仍是旧值。现在启动时若发现「恰好等于旧默认值」就清掉回落新默认值，用户自己改过的地址不受影响。
- 地址归一化抽为纯函数 `UrlUtils.normalizeBase()`：
  - 空值 → 线上地址；
  - 不写协议时，`xumois.cn` 域名补 `https`，IP/局域网补 `http`；
  - 统一补结尾斜杠。

### 2. 设置页升级

- 新增「测试连接」按钮：真实调用 `ping` + `products`，回显服务名、数据库状态、接口地址与耗时，不再只是"保存一个地址"。
- 「恢复默认」改为恢复**线上服务器**；保存后自动跑一次连接测试。
- 文案改为线上口径，不再提示"模拟器地址"。

### 3. 内嵌站点（购物车/我的/来图定制/下单）补全能力

新增 `util/WebViewSetup.kt` 统一配置，`WebFragment` 与 `WebViewActivity` 共用：

- 开启 JS、DOM Storage、Database、混合内容兼容模式，保证站点脚本与登录态正常。
- 开启 Cookie（含第三方）并在退出时 flush，购物车/登录态不会丢。
- **实现文件选择** `WebChromeClient.onShowFileChooser`：这是之前最影响体验的缺口——站点「来图定制」上传参考图在 WebView 里点了没反应，现在会正常拉起系统文件选择器。
- 站内返回优先回退 WebView 历史，到栈底才交回 Activity；回调绑定 `viewLifecycleOwner`，切换 Tab 自动注销，避免返回被处理两遍。
- 非 http(s) 协议（`tel:` / `mailto:` / 支付宝唤端等）交给系统处理，不再在 WebView 里报错。

### 4. 数据模型与测试

- `PingResponse` 补齐后端实际返回的 `dbOk` / `dbMs` / `ms` 字段（设置页与自检页可展示数据库状态）。
- 单测新增 4 组：默认地址必须是线上、协议与斜杠归一化、由基址拼出的接口地址、ping 含数据库字段。
- **单测抓到一个真实 bug**：`normalizeBase("")` 原先直接返回常量 `PROD_URL`（不带结尾斜杠），会让 `apiBase()` 拼成 `https://shine.xumois.cnapi/m/`。已改为走统一的补斜杠逻辑，13 项测试全部通过。

### 5. 版本与发布

- `versionCode 1 → 2`，`versionName 1.0.0 → 1.1.0`（沿用同一 debug 签名，可直接覆盖安装旧版本，无需卸载）。
- 新增下载页 `https://shine.xumois.cn/download/`，安装包 `https://shine.xumois.cn/download/shine-xumis.apk`。
  - Nginx 片段 `deploy/apk/nginx-download.conf`（`.apk` 指定 `application/vnd.android.package-archive`，带 `Content-Disposition`）。
  - `deploy/apk/remote-publish.sh`：幂等挂载 include、备份站点配置、`nginx -t` 校验后 reload。
- 仓库根目录 `shine.apk` 更新为本次构建产物。

### 验证结果

| 项目 | 结果 |
|---|---|
| 单元测试 | 13 项通过 / 0 失败 |
| APK 元信息 | `com.shine.xumis.app` 1.1.0 (code 2)，minSdk 24，targetSdk 33 |
| APK 内默认地址 | 存在 `https://shine.xumois.cn`，`10.0.2.2` 仅作调试常量 |
| `/api/m/ping` | 200，`{"ok":true,"dbOk":true}` |
| `/api/m/products`、`/products/1`、`/categories`、`/search` | 全部 200，返回 12 件商品，价格排序生效 |
| WebView 页面 `/cart`、`/product/1`、`/custom`、`/login` | 200（`/account` 未登录 302 → `/login`，符合预期） |
| 下载地址 | 200，`Content-Type` 正确，本地与线上 SHA-256 一致 |

## 2026-10-30（第二次）— 登录与注册合并为一个页面

**目标**：把原来分开的 `/login` 与 `/register` 两个页面合并成一个带「登录 / 注册」标签切换的页面，减少一次跳转。

### 新增

- `templates/auth.html`：合并页。顶部胶囊式标签（登录 / 注册）+ 两个面板，登录面板含邮箱、密码、忘记密码、订单查询；注册面板含邮箱、称呼、密码（≥8 位提示）、确认密码、条款说明。
- 样式（`static/css/style.css`）：`.auth-wrap` / `.auth-tabs` / `.auth-tab(.is-active)` / `.auth-panel.is-hidden` / `.auth-foot`，沿用项目原有的米绿配色与圆角。

### 路由与行为

| 地址 | 行为 |
|---|---|
| `GET /login` | 渲染合并页，默认停在**登录**标签 |
| `GET /login?tab=register` | 渲染合并页，直接停在**注册**标签 |
| `GET /register` | 渲染合并页，默认停在**注册**标签（旧链接继续可用，不做 302，避免多一跳） |
| `POST /login` | 逻辑不变；失败/锁定后回到**登录**标签并提示 |
| `POST /register` | 逻辑不变；校验失败后回到**注册**标签，保留已填邮箱与称呼 |

- 标签切换用前端 JS（无刷新），同时把 `?tab=` 写回地址栏，刷新或分享链接后仍停在同一个面板。
- 无 JS 时由服务端决定显示哪个面板，两个表单都能正常提交。
- 两个表单都保留 `redirect` 隐藏域，`/login?redirect=/account` 这类回跳逻辑不受影响。

### 后端改动（`controller/AccountController.java`）

- `registerPage` / `loginPage` 返回视图改为 `auth`，并注入 `tab`（login / register）；`loginPage` 新增可选参数 `tab`。
- 错误属性改名，避免两个面板互相干扰：
  - 登录失败 → `loginError`（布尔）
  - 注册失败 → `regError`（`badEmail` / `weakPassword` / `pwdMismatch` / `dupEmail`）
- 注册失败分支补上 `redirectTo` 回填（此前会丢失，回跳会退化成 `/account`）。

### 删除

- `templates/login.html`、`templates/register.html`（已被 `auth.html` 取代）。

### i18n

- 新增 `account.authTitle`（登录 / 注册）、`account.authDesc`（一个账号即可保存收货信息…），三个语言文件同步。

### 缓存版本

- 样式版本号 `?v=20261030b` → `?v=20261030c`（21 个模板同步更新）。

### 验证

- `GET /login`、`GET /register`、`GET /login?tab=register` 均 200，标签选中态正确。
- 错误密码提交：200，回到登录标签，提示「邮箱或密码不正确」，邮箱已回填。
- 注册两次密码不一致提交：200，回到注册标签，提示「两次输入的密码不一致」，称呼已回填。
- 已登录用户访问 `/login`、`/register` 仍 302 跳 `/account`。

---

## 2026-10-30（第一次）— 销售统计 / 支付渠道 / 首页定制区块改版 / 导航精简

本次改动已上线：<https://shine.xumois.cn/>。构建方式：`mvn -o clean package -DskipTests`（JDK 17），产物 `target/shine-xumis.jar`。

### 1. 后台新增「累计销售额」等销售统计

**后端**

| 文件 | 改动 |
|---|---|
| `dto/SalesSummary.java`（新增） | 销售汇总记录：累计销售额、已确认收入、待确认金额、订单数、售出件数、客单价 |
| `dto/ProductSales.java`（新增） | 单商品售出汇总：累计件数 + 累计金额 |
| `service/OrderService.java` | 新增 `salesSummary()` 与 `soldByProduct()`；口径：**已取消（CANCELLED）订单不计入**，已确认收入 = PAID + SHIPPED + DONE，金额以订单基准币种 USD 计，客单价 = 销售额 / 订单数（四舍五入 2 位） |
| `repository/OrderRepository.java` | `findAllByOrderByCreatedAtDesc()` 加 `@EntityGraph(attributePaths = {"items"})`，避免统计时 N+1 查询 |
| `controller/AdminController.java` | 仪表盘注入 `sales`；商品管理页注入 `soldStats`（商品 ID → 件数/金额） |

**页面**

- `templates/admin.html`：统计卡新增「累计销售额(USD)」「售出件数」；下方新增汇总行：累计销售额 · 已确认收入 · 待确认 · 客单价（附「N 笔订单，M 件，不含已取消订单」）。统计卡网格由 6 列改为 **4 列两行**（8 张卡不再出现 6+2 参差）。
- `templates/admin-products.html`：商品表新增「已售」列，展示 **件数 + 累计金额**；空行 `colspan` 8 → 9。
- `static/css/style.css`：新增 `.stat-grid-4`、`.stat-num.stat-money`（金额字号缩至 20px 防溢出）、`.sales-line`；≤900px 时 `.stat-grid-4` 降为 2 列。

### 2. 结算新增支付渠道（PayPal / 微信 / 支付宝 / 万事达卡）

- `model/Order.java`：新增 `paymentMethod` 字段（`VARCHAR(20)`，可为空，兼容历史订单）。
- `controller/CartController.java`：
  - 下单接口接收可选参数 `paymentMethod`；
  - 白名单 `PAYPAL / WECHAT / ALIPAY / MASTERCARD`，其余值一律落 null（防止前端伪造任意字符串入库）。
- `templates/checkout.html`：结算页新增「支付方式」2×2 单选（🌐 PayPal / 💚 微信支付 / 🔵 支付宝 / 💳 万事达卡），默认选中 PayPal。
- `controller/StorefrontController.java`：`/order-success` 按订单号回填订单对象，供成功页展示渠道（此前只传订单号）。
- 展示位：`order-success.html`（下单成功页）、`order-track.html`（订单查询）、`admin.html`（后台订单表新增「支付渠道」列，空行 `colspan` 7 → 8）。
- `static/css/style.css`：新增 `.pay-methods` / `.pay-opt`（含 `:has(input:checked)` 选中态），≤480px 单列。

### 3. 首页「把你的想法交给我们」按设计稿改版

- `templates/home.html`：左「标题 + 副文案 + 立即定制胶囊按钮」（`.story-text/.story-title/.story-sub/.story-cta`），右 **2×2 白色卡片**（`.feature-grid` / `.feature-card` / `.fc-ico`），四张卡：🌸 温柔手作、🎨 来图定制、🌍 跨境直发、❤️ 贴心服务。
- `style.css`：外层改为浅米绿大圆角卡 + 左下柔和绿色光晕（`.story::before`），卡片 hover 轻微上浮；替换旧 `.features/.feature/.ico` 规则，并同步响应式（≤720px 单列）与降级规则。

### 4. 顶部导航删除搜索框

- `templates/fragments.html`：移除导航中间 `.nav-search` 表单。
- `style.css`：`.nav` 由「品牌 | 搜索 | 入口」三栏改为 **两栏**（品牌左、入口右），≤1040px 栅格同步调整。
- 搜索能力未丢失：首页 hero 搜索框、`/products` 页搜索 + 价格筛选均保留。

### 5. 静态资源缓存刷新

- nginx 对 `/css/` 设了 7 天 `immutable`，改样式后老访客会继续用旧 CSS。已将 **23 个模板**的样式引用统一加版本号 `?v=20261030b`。

### 6. i18n

`messages.properties`、`messages_zh.properties`、`messages_en.properties` 同步新增：

- 销售：`admin.totalSales` / `admin.paidSales` / `admin.pendingSales` / `admin.avgOrder` / `admin.salesOrders` / `admin.soldUnit` / `admin.salesNote` / `admin.sold` / `admin.soldItems` / `admin.pendingReviews`
- 支付：`checkout.paymentMethod` / `pay.PAYPAL` / `pay.WECHAT` / `pay.ALIPAY` / `pay.MASTERCARD` / `pay.label`

### 兼容性说明

- `paymentMethod` 为可空新增列。若线上为 `ddl-auto=update` 会自动加列；历史订单该字段为空，页面显示「-」，统计口径不受影响。
- 销售统计当前为**实时全表遍历**（订单量在千级以内无压力）。若后续订单量上万，建议改为数据库聚合查询或加日汇总表。

---

## 2026-10-30 · Android 1.2.0：从「WebView 壳」变成离线可用的客户端（6.99 MB → 20.2 MB）

> 诉求：APK 不能只是个网页展示，还要能证明「可靠、能用」。体积增长全部来自真实内容与能力，不是塞无意义文件。

### 体积构成（6.99 MB → 20.2 MB，+13.2 MB）

| 增量 | 大小 | 用途 |
|---|---|---|
| `assets/offline/img/*.png`（12 张商品原图） | 13.1 MB | 离线商品册 + 弱网首屏本地解码，不再等网络 |
| `assets/fonts/*.ttf`（Nunito 4 字重 + Quicksand 2 字重） | 0.63 MB | 离线排版与站点一致，不依赖 Google Fonts |
| `assets/offline/*.html + app.css`（兜底页 / 商品册 / 条款） | <0.02 MB | 断网时的可用界面 |
| 新增 Kotlin（状态页 / 帮助页 / 缓存 / 崩溃保护） | — | 见下 |

### 新增能力

- **离线商品册** `assets/offline/catalog.html`：内置 12 件商品的图、价、材质、描述，点卡片看详情，底部「联网查看最新」跳站点。
- **离线兜底页** `assets/offline/index.html`：WebView 主文档加载失败（断网/超时/5xx）自动切过去，带 `?target=` 回跳，点「重新连接」直接回到原页面；`WebViewSetup.client(context, onOffline)` 新增回调参数，`WebFragment` / `WebViewActivity` 均已接入。
- **配送与售后条款** `assets/offline/policies.html`：发货、跨境运费、7 天无理由、定制款例外、四种支付渠道说明，离线可读。
- **商品列表本地缓存** `util/CatalogCache`：在线成功后落盘，断网时 `HomeFragment` 回退展示并显示「离线数据 · N 件 · 缓存时间」提示条。
- **启动重试与离线进入** `SplashActivity`：ping 失败自动重试 3 次（0.8s/1.6s 退避）；仍失败给三个出口——重试 / 改地址 / 离线进入（有缓存时）。
- **运行状态页** `StatusActivity`：5 轮采样（ping+商品+分类算一轮），展示通过率、平均/最大延迟、已用天数、缓存商品数、离线资源、上次检测时间、崩溃记录；结果由 `util/HealthLog` 持久化；可跳 50 次压力自检。
- **帮助与关于** `AboutActivity`：版本、服务器、实时连接状态、离线资源清单、5 条 FAQ、客服邮箱、分享 App。
- **崩溃保护** `util/CrashGuard`：异常堆栈落盘 `filesDir/last_crash.txt`，60 秒内非连环崩溃则拉回启动页；日志在运行状态页可查。
- **分享 / 浏览器打开** `ProductDetailActivity` 新增 `product_menu.xml`（分享走系统分享面板，浏览器打开走 ACTION_VIEW）。
- 主菜单扩为：运行状态 / 自检(50遍) / 帮助与关于 / 设置。

### 文件

- 新增：`util/OfflineAssets.kt`、`util/CatalogCache.kt`、`util/HealthLog.kt`、`util/CrashGuard.kt`、`ui/StatusActivity.kt`、`ui/AboutActivity.kt`、`layout/activity_status.xml`、`layout/activity_about.xml`、`drawable/{bg_card,ic_status,ic_help,ic_share,ic_browser}.xml`、`menu/product_menu.xml`、`tools/gen_offline_catalog.py`、`assets/offline/**`、`assets/fonts/**`、`res/font/{nunito,quicksand_bold}.ttf`
- 改动：`WebViewSetup.kt`（onOffline 回调 + shareIntent）、`HomeFragment.kt`、`WebFragment.kt`、`WebViewActivity.kt`、`SplashActivity.kt`、`ProductDetailActivity.kt`、`MainActivity.kt`、`DiagnosticsActivity.kt`（结果写入 HealthLog）、`ShineApp.kt`、`AndroidManifest.xml`、`app/build.gradle`（versionCode 3 / versionName 1.2.0）、`deploy/apk/index.html`

### 真机（Android 14 模拟器）验证

- 运行状态页：5/5 通过，平均 227 ms、最大 418 ms，通过率 100%，明细显示「商品图 12 张 / 13.1 MB」「缓存 12 件」。
- 帮助与关于：连接状态「正常 · 286 ms · 数据库正常」。
- 离线商品册：App 内 `file://` 打开，图片、字体、JS 详情弹层全部正常。
- **断网实测**（飞行模式）：首页回退缓存并显示提示条；购物车 WebView 显示离线兜底页（含「目标地址 shine.xumois.cn/cart」）；恢复网络点「重新连接」回到真实购物车且登录态/购物车内容仍在；冷启动显示「离线进入（缓存 12 件商品）」。
- APK `versionCode=3 / versionName=1.2.0`，线上与本地 SHA-256 一致（`4328a3e6…f0228cf9`），已发布到 https://shine.xumois.cn/download/。

### 维护提示

- 商品数据更新后，跑 `python3 android/tools/gen_offline_catalog.py` 重新生成离线快照再打包，否则离线册会停留在旧价格。

---

## 2026-10-30 · Android 1.3.0：地址收进后台，设置改成分组目录

> 诉求：手机端不要显示连接的服务器地址，后台直连即可；设置页按常规 App 的样子做成分组目录。

### 一、服务器地址不再出现在任何界面

- `util/ServerConfig` 重写：地址改为内部常量 `UrlUtils.PROD_URL`，删除可写属性 `baseUrl` / `isUsingDefault()` / 运行时切换逻辑，只保留 `siteBase()`、`apiBase()` 给内部拼接用。
- 升级时自动清理旧版本写进 `shine_prefs` 的 `server_url`（`ServerConfig.purgeLegacy`），旧的自定义地址不会残留生效。
- 界面清理：
  - 启动页副标题「连接线上商店 shine.xumois.cn」→「饰品与手工品 · 官方客户端」；删除「设置服务器地址」按钮。
  - 设置页彻底移除地址输入框、保存/测试/恢复默认按钮与接口地址回显。
  - 帮助与关于：「服务器 https://…」整行删除；FAQ 第 1 条改为「安装后需要做配置吗？不需要，后台已直连」。
  - 运行状态「运行明细」：删除「服务器 …」一行。
  - 断网兜底页：删除页脚「目标地址 …」；提示「设置 → 测试连接」改为「设置 → 检查网络」。
  - 离线商品册页脚：「数据来源：shine.xumois.cn」→「商品快照 · 生成于 日期」（同步改 `tools/gen_offline_catalog.py` 模板）。
  - 售后条款：删除「站点：https://…」一行（客服邮箱保留）。
- 分享文案不再附带链接，避免把域名带到分享面板。

### 二、设置页改为常规分组目录

新增 `layout/item_settings_row.xml`（图标 + 标题 + 说明 + 当前值 + 箭头）、`drawable/ic_{chevron_right,text_size,cache,network,storage,image,doc,info}.xml`，`themes.xml` 增加 `SettingsSection` / `SettingsCard` 两个样式。

四组结构：

| 分组 | 设置项 |
|---|---|
| 通用 | 网页字号（标准 / 大 115% / 特大 130%，经 `WebSettings.textZoom` 生效）、检查网络（ping + 商品接口，回显耗时/数据库/商品数）、清除网页缓存（只清 WebView 缓存，保留 Cookie 登录态） |
| 内容与存储 | 离线商品缓存（件数 + 缓存时间，点开离线商品册）、离线资源（图片张数/体积，弹层列明内置资源） |
| 帮助与支持 | 运行状态、接口自检（50 次）、帮助与关于、配送与售后条款 |
| 关于 | 版本（v1.3.0（4），不可点）、分享给朋友 |

配套改动：

- 新增 `util/AppPrefs`（网页字号偏好，`shine_settings`）。
- `WebViewSetup.apply` 读取字号写入 `textZoom`；新增 `clearWebCache()`。
- 主界面右上角菜单收敛为单个「设置」，原「运行状态 / 自检 / 帮助」全部并入设置目录。
- `ShareIntent` 支持空链接。

### 三、验证（Android 14 模拟器，真机级走查）

- 设置页四组十行全部渲染正常，`网页字号` 值随选择变化并持久化（重启后仍为「大」）。
- 检查网络：`已连接 · 454 ms · 数据库正常 · 商品 12 件`（不显示地址）。
- 网页字号调大后，购物车 WebView 文字同步放大，登录态与购物车（贝珠耳环 $12.50）保持。
- 帮助与关于：`连接状态 正常 · 471 ms · 数据库正常`，无地址。
- 运行状态：`5/5 全部通过 · 100% · 平均 227 ms · 最大 418 ms`，明细无「服务器」行。
- 飞行模式：兜底页正常，页脚无「目标地址」；提示为「设置 → 检查网络」。
- 离线商品册页脚为「商品快照 · 生成于 2026-10-30」；售后条款仅保留客服邮箱。
- 全量 grep `xumois` 后，界面中仅剩客服邮箱 `service@shine.xumois.cn`，代码中仅剩 `UrlUtils.PROD_URL`。
- 13 项单元测试全绿；APK 20.2 MB，versionCode 4 / versionName 1.3.0。

---

## 2026-10-30 · Android 1.4.0：只保留四项系统权限（定位 / 相册 / 相机 / 蓝牙），适配小米 · OPPO · vivo

> 诉求：APK 需要定位、手机相册、手机相机、蓝牙这四项系统权限，除此外都不要；并且要适配小米、OPPO、vivo 三款手机。

### 一、权限清单（安装时可见，仅此五项）

| 分组 | 声明 | 说明 |
|---|---|---|
| 联网 | `INTERNET` | 与商店服务通信，普通权限不弹窗 |
| 定位 | `ACCESS_FINE_LOCATION`、`ACCESS_COARSE_LOCATION` | 运费估算、就近自提点、站点地图定位 |
| 相册 | `READ_MEDIA_IMAGES`（13+）、`READ_MEDIA_VISUAL_USER_SELECTED`（14+「仅选中的照片」）、`READ_EXTERNAL_STORAGE`（≤12，maxSdk 32） | 来图定制上传参考图、换头像 |
| 相机 | `CAMERA` | 下单现场拍图、扫溯源码 |
| 蓝牙 | `BLUETOOTH_SCAN`（neverForLocation）、`BLUETOOTH_CONNECT`（12+）、`BLUETOOTH`/`BLUETOOTH_ADMIN`（≤11，maxSdk 30） | 蓝牙标签打印机、门店近场设备 |

没有申请：通讯录、短信、通话记录、通知、后台定位、写外部存储、麦克风、读取应用列表等，一条都没有。
AndroidX 默认会注入的 `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` 也用 `tools:node="remove"` 显式移除了（本应用不注册任何广播）。

### 二、兼容性处理（针对国产 ROM）

- `targetSdk` 33 → **34**：HyperOS / ColorOS 14 / OriginOS 4 底层是 Android 14，只有到 34 才能按新规则申请「仅选中的照片」。
- 硬件声明全部 `android:required="false"`（camera / location / bluetooth / bluetooth_le 等）：没有蓝牙或摄像头的入门机型照样能装，App 内对应入口自动变为「本机不支持」。
- `Permissions.Group` 按 `Build.VERSION.SDK_INT` 动态计算权限集合：Android 12 拆分的蓝牙扫描/连接、13 的图片权限、14 的部分授权分别处理。
- 相册区分 **已允许 / 部分照片**：ColorOS、HyperOS 上用户常只勾几张，不能当成未授权反复弹窗。
- 定位区分 **精确 / 大致位置**：只给粗略定位时按「部分授权」展示，功能照常。
- 一键跳转厂商页：小米 `com.miui.securitycenter/…AutoStartManagementActivity`、OPPO/realme `com.coloros.safecenter/…StartupAppListActivity`、vivo/iQOO `com.vivo.permissionmanager/…BgStartUpManagerActivity`、华为/荣耀 `com.huawei.systemmanager/…StartupNormalAppListActivity`，逐个尝试，失败退回本应用详情页；并在清单里加了对应的 `<queries>` 以便 Android 11+ 可见。
- 首次启动弹一次权限说明，之后只从「设置 → 权限管理」进入，不反复打扰。

### 三、权限是真在用（不是只声明）

- **权限中心** `ui/PermissionActivity`：四项各显示状态，支持一键开启，点每一项可**就地验证**——取一次坐标（GPS/网络，三级兜底）、统计可读相册图片数、调起相机试拍并显示尺寸与体积、扫 6 秒 BLE 列出周边设备。
- **拍照上传**：网页 `onShowFileChooser` 改为 `ACTION_CHOOSER` + `EXTRA_INITIAL_INTENTS`，把「相机」与「厂商相册」并列，保留各家原生相册选择器；拍照走 `FileProvider`（`cacheDir/captures`），结果回填网页预览。
- **WebView 定位**：`setGeolocationEnabled(true)` + `onGeolocationPermissionsShowPrompt` 按权限状态放行/申请/拒绝。
- **网页摄像头**：`onPermissionRequest` 只放行 `RESOURCE_VIDEO_CAPTURE`，麦克风一律拒绝。
- 拒绝后弹「去设置」引导，文案按品牌写真实菜单路径。

### 四、验证（Android 14 模拟器，逐项实测）

- 一键开启依次弹出 4 个系统弹窗：拍照录像 → 位置 → 附近设备 → 照片和视频，全部允许后状态变 **4/4**。
- 定位：`纬度 39.90930 / 经度 116.39740 / 精度 ±5m / 来源 GPS`（`adb emu geo fix` 注入）。
- 相册：推入 1 张测试照片后显示「可读取相册图片 1 张」。
- 相机：调起 `com.android.camera2` 试拍成功，`1392 × 1856 · 0.1 MB`，落在 `/cache/captures/`。
- 蓝牙：`已配对 0 台 · 支持 BLE true · 扫描完成，周边暂无蓝牙设备`（模拟器无周边设备，流程正常）。
- 来图定制上传：选「相机」拍完回填 `upload_….jpg` 并显示预览；选「Media」从相册选图回填 `1000000025.jpg` 并显示预览。
- 撤销相机权限后点该项 → 系统弹窗选「拒绝」→ 弹出「手机相机权限未开启 / …去设置」，点「去设置」跳到应用详情页（`Permissions: Location, Nearby devices, and Photos and videos`，只有这四项）。
- 非小米/OPPO/vivo 机型点「后台运行白名单」正确退回本应用设置页。
- 13 项单元测试全绿；APK 21,308,304 B（20.3 MB），versionCode 5 / versionName 1.4.0。

### 五、文件

- 新增：`util/Permissions.kt`、`util/DeviceProbe.kt`、`ui/PermissionActivity.kt`、`layout/activity_permission.xml`、`res/xml/file_paths.xml`、`drawable/ic_{location,gallery,camera,bluetooth,shield}.xml`
- 改动：`AndroidManifest.xml`（权限/uses-feature/queries/FileProvider）、`app/build.gradle`（targetSdk 34、versionCode 5 / 1.4.0）、`util/WebViewSetup.kt`（geoAllowDirectly / handlePermissionRequest / chooserIntent / chooserResult / newCaptureFile）、`util/AppPrefs.kt`（首次引导标记）、`ui/{WebFragment,WebViewActivity,MainActivity,SettingsActivity}.kt`、`layout/activity_settings.xml`、`values/colors.xml`（shine_mint_dark）、`deploy/apk/index.html`
