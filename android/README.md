# shine 星心 · 安卓测试客户端（ShineApp）

面向 **shine 星心独立站**（Spring Boot 后端）的移动测试 App，用于安卓手机上验证站点各项功能。
适配 **Android 13（API 33）**，minSdk 24，Kotlin 编写。

## 功能界面（全部完整实现）
- **启动页 Splash**：自动 `ping` 后端，连接成功进入主页，失败提示去「设置」改地址。
- **首页 Home**：原生商品网格（精选标记、图片、价格、分类），下拉刷新。
- **搜索 Search**：关键词 + 分类 + 排序（默认/价格升/降），原生结果列表。
- **商品详情 ProductDetail**：原生详情（大图、价格、材质、库存、定制标），「去网页加购/购买」「查看购物车」一键跳站内流程。
- **购物车 Cart**：内嵌 WebView 加载站点 `/cart`，完整加购 / 结算 / 下单流程与网页一致。
- **我的 Account**：内嵌 WebView 加载站点 `/account`（登录 / 注册 / 订单 / 个人中心）。
- **设置 Settings**：填写服务器地址（含 http:// 与端口）；模拟器默认 `http://10.0.2.2:8080`，真机/公网填实际可达地址。
- **自检 Diagnostics（50 遍）**：对后端连续执行 **50 次**「健康检查 + 商品/分类接口校验」，实时显示每遍通过与失败计数，用于上架前/现场演示时确认服务稳定。

## 对接的后端接口（/api/m 前缀）
| 接口 | 用途 |
|------|------|
| `GET /api/m/ping` | 健康检查探针 |
| `GET /api/m/products?page=&size=` | 商品分页列表 |
| `GET /api/m/products/{id}` | 商品详情 |
| `GET /api/m/categories` | 分类列表 |
| `GET /api/m/search?q=&cat=&sort=` | 搜索 / 分类 / 排序 |

> 后端接口默认不校验 `X-API-KEY`（开发期放行），部署到公网建议配置 `app.mobile-api-key` 并在 App 中带上。

## 构建（macOS / Linux）
工具链：JDK 17、Android SDK（platform-tools、platforms;android-33、build-tools;33.0.2）。

```bash
export JAVA_HOME=/path/to/jdk17
export ANDROID_HOME=$HOME/Android/sdk
cd android
./gradlew assembleDebug          # 产物：app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest      # 核心逻辑单测（JSON 解析 / 价格 / 库存 / 自检）
```

## 50 遍验证
1. App 内「自检」：右上角菜单 → 自检（50 遍），一键跑 50 次后端连通与接口校验。
2. 自动化单测：核心逻辑 JUnit 套件，可在 CI 中循环执行 50 次确认稳定。

## 真机使用
1. 在电脑/服务器启动后端（默认 8080 端口）。
2. 手机与后端在同一网络，或后端已公网部署。
3. App 内「设置」填写后端地址（如 `http://192.168.1.50:8080`）。
4. 返回首页即可浏览、搜索、下单、查看订单。
