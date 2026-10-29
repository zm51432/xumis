# 变更记录 CHANGELOG

## 2026-10-30 — 销售统计 / 支付渠道 / 首页定制区块改版 / 导航精简

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
