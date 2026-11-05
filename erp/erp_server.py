#!/usr/bin/env python3
"""
shine 星心 · 轻量 ERP（本机部署）

职责：
  · 商品主数据、库存台账、销售单、退货单、询盘、定制需求的统一台账
  · 接收网站推送（X-ERP-API-KEY）：/erp/api/products|orders|inquiries|custom-ideas|inventory/sync
  · 退货审批后回写网站：POST http://127.0.0.1:8080/api/erp/callback/return
    → 网站生成退货记录、回滚库存、把这部分数量从「售出量」里扣掉
  · 库存调整可回写网站：POST /api/erp/callback/stock

只监听 127.0.0.1:8096；外网入口由 nginx /erp/ 反向代理，UI 需登录。
所有口令/密钥均为 24 位随机串，放在 /opt/shine-erp/config.env（600）。
"""
import os
import json
import hmac
import base64
import hashlib
import secrets
import time
import datetime
import html
import urllib.request
import urllib.error
from urllib.parse import quote
from functools import wraps
from flask import Flask, request, session, redirect, jsonify, render_template_string

import pymysql

# ---------------- 配置 ----------------
DB_HOST = os.environ.get("ERP_DB_HOST", "127.0.0.1")
DB_USER = os.environ.get("ERP_DB_USER", "erp")
DB_PASS = os.environ.get("ERP_DB_PASSWORD", "")
DB_NAME = os.environ.get("ERP_DB_NAME", "shine_erp")
API_KEY = os.environ.get("ERP_API_KEY", "")
ADMIN_PW = os.environ.get("ERP_ADMIN_PASSWORD", "")
SECRET = os.environ.get("ERP_SECRET_KEY", "fallback-secret-change-me")
WEB_BASE = os.environ.get("ERP_WEB_BASE", "http://127.0.0.1:8080")

app = Flask(__name__)
app.secret_key = SECRET
app.config.update(SESSION_COOKIE_HTTPONLY=True, SESSION_COOKIE_SAMESITE="Lax",
                  PERMANENT_SESSION_LIFETIME=datetime.timedelta(hours=12))

# ---------------- 库存业务类型（入库 / 出库剔除） ----------------
# 之前 UI 只有「采购入库 / 盘点调整」，只能往上涨；这里补齐出库与剔除方向。
IN_TYPES = {"PURCHASE", "RETURN_IN", "PRODUCE_IN", "RETURN"}
OUT_TYPES = {"SALE_OUT", "DAMAGE", "SCRAP", "STOCK_OUT", "SALE"}
TYPE_LABELS = {
    "PURCHASE": "采购入库", "RETURN_IN": "退货入库", "PRODUCE_IN": "生产入库",
    "RETURN": "退货入库", "SALE": "网站销售出库", "SALE_OUT": "销售/领用出库",
    "DAMAGE": "报损剔除", "SCRAP": "报废剔除", "STOCK_OUT": "其他出库",
    "ADJUST": "盘点调整", "SYNC": "网站同步", "MANUAL": "手动调整",
}


def type_label(t):
    return TYPE_LABELS.get(t, t or "—")


def direction_of(t, change_qty):
    """台账方向：优先按业务类型，其次按变动量正负"""
    if t in IN_TYPES:
        return "IN"
    if t in OUT_TYPES:
        return "OUT"
    try:
        return "OUT" if int(change_qty or 0) < 0 else "IN"
    except (TypeError, ValueError):
        return "IN"


EXTRA_TABLES = [
    """CREATE TABLE IF NOT EXISTS erp_product_sku (
        sku_id BIGINT PRIMARY KEY, product_id BIGINT NOT NULL,
        sku_code VARCHAR(80), option_name VARCHAR(60), option_value VARCHAR(120),
        option_value_en VARCHAR(120), price_delta DECIMAL(10,2) DEFAULT 0,
        stock INT DEFAULT 0, enabled TINYINT(1) DEFAULT 1,
        synced_at DATETIME NULL, INDEX idx_eps_product (product_id)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci""",
    """CREATE TABLE IF NOT EXISTS erp_stock_reservation (
        id BIGINT AUTO_INCREMENT PRIMARY KEY, order_no VARCHAR(64) NOT NULL,
        product_id BIGINT NOT NULL, sku_id BIGINT NULL, qty INT NOT NULL DEFAULT 0,
        status VARCHAR(20) NOT NULL DEFAULT 'HELD', expires_at DATETIME NULL,
        updated_at DATETIME NULL, INDEX idx_esr_order (order_no),
        INDEX idx_esr_status (status)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci""",
]


EXTRA_COLUMNS = {
    "erp_product": {
        "active": "TINYINT(1) NOT NULL DEFAULT 1",
        "category_code": "VARCHAR(64)",
    },
    "erp_sales_order": {
        "subtotal_amount": "DECIMAL(10,2) DEFAULT 0",
        "discount_amount": "DECIMAL(10,2) DEFAULT 0",
        "coupon_code": "VARCHAR(60)",
        "tracking_no": "VARCHAR(100)",
        "carrier": "VARCHAR(80)",
        "tracking_url": "VARCHAR(300)",
        "note": "TEXT",
        "shipped_at": "DATETIME NULL",
        "shipping_fee": "DECIMAL(10,2) DEFAULT 0",
        "tax_amount": "DECIMAL(10,2) DEFAULT 0",
    },
    "erp_user": {
        "role": "VARCHAR(20) NOT NULL DEFAULT 'admin'",
    },
    "erp_return": {
        "source": "VARCHAR(32) DEFAULT 'ERP'",
        "synced_at": "DATETIME NULL",
    },
    "erp_inventory_log": {
        "sku_id": "BIGINT NULL",
    },
    "erp_sales_item": {
        "sku_id": "BIGINT NULL",
        "sku_label": "VARCHAR(200)",
        "product_name_en": "VARCHAR(255)",
        "image_url": "VARCHAR(500)",
    },
}


def ensure_schema():
    """启动时自动补齐新增列与表（阶段2：SKU 库存 / 预占台账）"""
    for ddl in EXTRA_TABLES:
        try:
            run(ddl)
        except Exception as e:
            print("[schema] create table: %s" % e)
    for table, cols in EXTRA_COLUMNS.items():
        for col, ddl in cols.items():
            try:
                found = one("SELECT COLUMN_NAME FROM information_schema.COLUMNS "
                            "WHERE TABLE_SCHEMA=%s AND TABLE_NAME=%s AND COLUMN_NAME=%s",
                            (DB_NAME, table, col))
                if not found:
                    run("ALTER TABLE %s ADD COLUMN %s %s" % (table, col, ddl))
            except Exception as e:
                print("[schema] %s.%s: %s" % (table, col, e))


def restock_cancelled(no):
    """订单取消：幂等回补 ERP 库存并写台账。网站取消 / 预占超时都会触发。"""
    if one("SELECT id FROM erp_inventory_log WHERE type='CANCEL' AND ref_no=%s LIMIT 1", (no,)):
        return False
    items = query("SELECT product_id,quantity FROM erp_sales_item WHERE order_no=%s", (no,))
    for i in items:
        pid, qty = i["product_id"], int(i["quantity"] or 0)
        if not pid or qty <= 0:
            continue
        p = one("SELECT stock FROM erp_product WHERE product_id=%s", (pid,))
        if not p:
            continue
        before = int(p["stock"] or 0)
        after = before + qty
        run("UPDATE erp_product SET stock=%s WHERE product_id=%s", (after, pid))
        run("INSERT INTO erp_inventory_log(product_id,change_qty,before_qty,after_qty,type,ref_no,note) "
            "VALUES(%s,%s,%s,%s,'CANCEL',%s,%s)",
            (pid, qty, before, after, no, "订单取消回补"))
    # 无明细也留一条幂等标记
    if not items:
        run("INSERT INTO erp_inventory_log(product_id,change_qty,before_qty,after_qty,type,ref_no,note) "
            "VALUES(0,0,0,0,'CANCEL',%s,%s)", (no, "订单取消（无明细）"))
    run("UPDATE erp_sales_order SET status='CANCELLED' WHERE order_no=%s", (no,))
    return True


# ---------------- 数据库工具 ----------------
def conn():
    # 阶段4：固定会话时区为 UTC，与网站 Hibernate jdbc.time_zone=UTC 对齐，
    # 避免 NOW() 与 LocalDateTime 混用造成 8 小时偏移（历史多次踩坑）。
    return pymysql.connect(host=DB_HOST, user=DB_USER, password=DB_PASS,
                           database=DB_NAME, charset="utf8mb4", autocommit=True,
                           init_command="SET time_zone = '+00:00'",
                           cursorclass=pymysql.cursors.DictCursor)


def query(sql, args=None):
    c = conn()
    try:
        with c.cursor() as cur:
            cur.execute(sql, args or ())
            return cur.fetchall()
    finally:
        c.close()


def one(sql, args=None):
    rows = query(sql, args)
    return rows[0] if rows else None


def run(sql, args=None):
    c = conn()
    try:
        with c.cursor() as cur:
            cur.execute(sql, args or ())
            return cur.lastrowid
    finally:
        c.close()


def log(direction, endpoint, status, message):
    try:
        run("INSERT INTO erp_sync_log(direction,endpoint,status,message) VALUES(%s,%s,%s,%s)",
            (direction, endpoint, status, str(message)[:480]))
    except Exception:
        pass


def esc(v):
    return html.escape("" if v is None else str(v))


# ---------------- 口令 ----------------
def hash_pw(raw, iterations=200000):
    salt = os.urandom(16)
    dk = hashlib.pbkdf2_hmac("sha256", raw.encode(), salt, iterations, dklen=32)
    return "pbkdf2$%d$%s$%s" % (iterations,
                                base64.b64encode(salt).decode(),
                                base64.b64encode(dk).decode())


def check_pw(raw, stored):
    try:
        _, iters, salt, digest = stored.split("$")
        dk = hashlib.pbkdf2_hmac("sha256", raw.encode(), base64.b64decode(salt), int(iters), dklen=32)
        return hmac.compare_digest(base64.b64encode(dk).decode(), digest)
    except Exception:
        return False


def init_user():
    row = one("SELECT id FROM erp_user WHERE username='admin'")
    if row is None and ADMIN_PW:
        run("INSERT INTO erp_user(username,password_hash) VALUES('admin',%s)", (hash_pw(ADMIN_PW),))


def api_ok(req):
    key = req.headers.get("X-ERP-API-KEY", "")
    return bool(API_KEY) and hmac.compare_digest(key, API_KEY)


LOGIN_FAILS = {}          # key -> [失败时间戳]
LOGIN_LOCK = {}           # key -> 解锁时间戳


def login_key():
    return (request.remote_addr or "?") + "|" + request.form.get("username", "").strip().lower()


def login_locked(key):
    until = LOGIN_LOCK.get(key, 0)
    if until and until > time.time():
        return True
    if until:
        LOGIN_LOCK.pop(key, None)
        LOGIN_FAILS.pop(key, None)
    return False


def login_failed(key):
    now = time.time()
    arr = [t for t in LOGIN_FAILS.get(key, []) if now - t < 600]
    arr.append(now)
    LOGIN_FAILS[key] = arr
    if len(arr) >= 5:
        LOGIN_LOCK[key] = now + 900      # 10 分钟内 5 次失败 → 锁 15 分钟
        LOGIN_FAILS.pop(key, None)


def login_required(fn):
    """阶段4：登录 + 只读角色禁止写操作（POST/PUT/DELETE 一律拦截）。"""
    @wraps(fn)
    def wrapper(*a, **kw):
        if not session.get("user"):
            return redirect("/erp/login")
        if request.method not in ("GET", "HEAD", "OPTIONS"):
            u = session.get("user")
            if isinstance(u, dict) and u.get("role") == "viewer":
                return redirect("/erp/?msg=!%E5%8F%AA%E8%AF%BB%E8%B4%A6%E5%8F%B7%E6%97%A0%E6%9D%83%E4%BF%AE%E6%94%B9%E6%95%B0%E6%8D%AE")
        return fn(*a, **kw)
    return wrapper


def post_json(url, payload):
    """回写网站（同一台服务器，走内网）"""
    data = json.dumps(payload, ensure_ascii=False).encode("utf-8")
    req = urllib.request.Request(url, data=data, method="POST", headers={
        "Content-Type": "application/json; charset=utf-8",
        "X-ERP-API-KEY": API_KEY,
    })
    with urllib.request.urlopen(req, timeout=10) as resp:
        return resp.status, json.loads(resp.read().decode("utf-8") or "{}")


# ---------------- 页面外壳 ----------------
CSS = """
:root{--bg:#f6f7f9;--card:#fff;--line:#e5e9ee;--ink:#24303f;--sub:#7b8794;--red:#d32f2f;--green:#2f9e44;--amber:#f59f00}
*{box-sizing:border-box}body{margin:0;background:var(--bg);color:var(--ink);font:14px/1.6 -apple-system,BlinkMacSystemFont,'Segoe UI','Microsoft YaHei',sans-serif}
header{background:#1f2a37;color:#fff;padding:14px 22px;display:flex;align-items:center;gap:18px;flex-wrap:wrap}
header b{font-size:17px;letter-spacing:.5px}header nav{display:flex;gap:14px;flex-wrap:wrap}
header nav a{color:#cbd5e1;text-decoration:none;font-size:13px}header nav a:hover{color:#fff}
header .right{margin-left:auto;font-size:12px;color:#94a3b8}
main{max-width:1180px;margin:22px auto;padding:0 18px}
h1{font-size:22px;margin:0 0 16px}h2{font-size:17px;margin:22px 0 10px}
.card{background:var(--card);border:1px solid var(--line);border-radius:12px;padding:16px;margin-bottom:16px}
.grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(180px,1fr));gap:12px;margin-bottom:16px}
.stat{background:#fff;border:1px solid var(--line);border-radius:12px;padding:14px}
.stat .n{font-size:24px;font-weight:800;display:block}.stat .t{font-size:12px;color:var(--sub)}
table{width:100%;border-collapse:collapse;font-size:13px}th,td{padding:9px 10px;border-bottom:1px solid var(--line);text-align:left}
th{background:#f8fafc;color:var(--sub);font-weight:700}tr:hover td{background:#fafbfc}
.btn{display:inline-block;background:var(--red);color:#fff;border:0;border-radius:8px;padding:8px 14px;font-size:13px;cursor:pointer;text-decoration:none}
.btn.sec{background:#fff;color:var(--ink);border:1px solid var(--line)}
.btn.sm{padding:5px 10px;font-size:12px}
input,select,textarea{border:1px solid var(--line);border-radius:8px;padding:8px 10px;font:inherit;background:#fff}
input[type=number]{width:90px}label{display:inline-block;margin-right:6px;font-size:13px;color:var(--sub)}
.tag{display:inline-block;padding:2px 8px;border-radius:999px;font-size:11px;background:#eef2f6;color:#475569}
.tag.ok{background:#e6f7ea;color:#1a7f37}.tag.bad{background:#fdeaea;color:#b42318}.tag.warn{background:#fff3d6;color:#96620a}
.alert{padding:10px 14px;border-radius:10px;margin-bottom:14px;background:#e6f7ea;color:#1a7f37}
.alert.err{background:#fdeaea;color:#b42318}
.muted{color:var(--sub);font-size:12px}
form.inline{display:inline}
@media(max-width:700px){header{padding:12px}main{padding:0 10px}table{font-size:12px}}
/* 出入库 / 剔除 */
.op-card{border-left:4px solid var(--red);background:#fff8f8}
.op-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(150px,1fr));gap:10px;align-items:end}
.op-grid label{display:block;margin:0 0 4px;font-size:12px;color:var(--sub)}
.op-grid input,.op-grid select{width:100%}
.btn.danger{background:var(--red)}.btn.ghost{background:#fff;color:var(--red);border:1px solid #f3c6c6}
.qty-in{color:#1a7f37;font-weight:700}.qty-out{color:#b42318;font-weight:700}
.tip{background:#fff8e6;border:1px solid #f6dfa8;color:#8a6100;border-radius:10px;padding:10px 14px;font-size:12.5px;margin-bottom:14px}
.chip{display:inline-block;padding:2px 8px;border-radius:999px;font-size:11px;background:#e6f7ea;color:#1a7f37}
.chip.out{background:#fdeaea;color:#b42318}
"""


def page(title, body, msg=""):
    u = session.get("user") or {}
    uname = u.get("name", "admin") if isinstance(u, dict) else "admin"
    urole = u.get("role", "admin") if isinstance(u, dict) else "admin"
    nav = """
    <header><b>shine 星心 · ERP</b>
      <nav>
        <a href="/erp/">总览</a><a href="/erp/products">商品与库存</a>
        <a href="/erp/orders">销售单</a><a href="/erp/returns">退货单</a>
        <a href="/erp/inventory">库存台账</a><a href="/erp/messages">询盘/定制</a>
        <a href="/erp/logs">同步日志</a>
      </nav><span class="right">%s (%s) · <a href="/erp/logout" style="color:#cbd5e1">退出</a></span>
    </header><main>
    """ % (esc(uname), esc(urole))
    alert = ('<div class="alert %s">%s</div>' % ("err" if msg.startswith("!") else "", esc(msg.lstrip("!")))) if msg else ""
    return render_template_string("""<!doctype html><html lang="zh"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>{{t}} · shine ERP</title><style>""" + CSS + """</style></head><body>
""" + nav + """<h1>{{t}}</h1>{{alert}}{{body|safe}}</main></body></html>""",
                                  t=title, alert=alert, body=body)


# ---------------- 登录 ----------------
@app.route("/erp/login", methods=["GET", "POST"])
def login():
    if request.method == "POST":
        u = request.form.get("username", "").strip()
        p = request.form.get("password", "")
        key = login_key()
        if login_locked(key):
            return page("登录", '<div class="card">尝试过于频繁，请 15 分钟后再试。</div>', "!登录已被临时锁定")
        row = one("SELECT * FROM erp_user WHERE username=%s", (u,))
        if row and check_pw(p, row["password_hash"]):
            LOGIN_FAILS.pop(key, None)
            LOGIN_LOCK.pop(key, None)
            session["user"] = {"name": u, "role": row.get("role") or "admin"}
            return redirect("/erp/")
        return page("登录", '<div class="card"><form method="post">账号 <input name="username" value="admin"><br><br>'
                    '口令 <input type="password" name="password"><br><br><button class="btn">登录</button></form></div>',
                    "!账号或口令不正确")
    return page("登录", '<div class="card"><form method="post">账号 <input name="username" value="admin"><br><br>'
                '口令 <input type="password" name="password" autofocus><br><br><button class="btn">登录</button></form></div>')


@app.route("/erp/logout")
def logout():
    session.clear()
    return redirect("/erp/login")


# ---------------- 总览 ----------------
@app.route("/erp/")
@login_required
def dashboard():
    products = one("SELECT COUNT(*) c, IFNULL(SUM(stock),0) s FROM erp_product") or {}
    orders = one("SELECT COUNT(*) c, IFNULL(SUM(total_amount),0) amt FROM erp_sales_order WHERE status<>'CANCELLED'") or {}
    returns = one("SELECT COUNT(*) c, IFNULL(SUM(refund_amount),0) amt FROM erp_return WHERE status='APPROVED'") or {}
    sold = one("SELECT IFNULL(SUM(quantity),0) q FROM erp_sales_item si JOIN erp_sales_order so ON so.order_no=si.order_no WHERE so.status<>'CANCELLED'") or {}
    returned_qty = one("SELECT IFNULL(SUM(quantity),0) q FROM erp_return_item ri JOIN erp_return r ON r.id=ri.return_id WHERE r.status='APPROVED'") or {}
    net_qty = int(sold.get("q", 0)) - int(returned_qty.get("q", 0))
    gross = float(orders.get("amt") or 0)
    refund = float(returns.get("amt") or 0)

    stats = [
        ("商品 SKU", products.get("c", 0)), ("库存总量", products.get("s", 0)),
        ("销售单", orders.get("c", 0)), ("净售出件数", net_qty),
        ("销售额", "$%.2f" % gross), ("已退货款", "$%.2f" % refund),
        ("净销售额", "$%.2f" % (gross - refund)), ("退货单", returns.get("c", 0)),
    ]
    cards = "".join('<div class="stat"><span class="n">%s</span><span class="t">%s</span></div>' % (esc(v), esc(k))
                    for k, v in stats)
    recent = query("SELECT * FROM erp_sync_log ORDER BY id DESC LIMIT 8")
    rows = "".join("<tr><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td></tr>" % (
        esc(r["created_at"]), esc(r["direction"]), esc(r["endpoint"]),
        esc(r["status"]), esc(r["message"])) for r in recent)
    body = ('<div class="grid">' + cards + '</div>'
            '<div class="card"><h2>闭环链路</h2><p class="muted">网站下单 → 推送销售单/扣 ERP 库存 → '
            'ERP 审批退货 → 回写网站（生成退货记录 + 回滚库存 + 扣减销量）→ 首页热销榜与商品「已售 N 件」随之下降。</p></div>'
            '<div class="card"><h2>最近同步</h2><table><tr><th>时间</th><th>方向</th><th>端点</th><th>状态</th><th>说明</th></tr>'
            + rows + '</table></div>')
    return page("总览", body, request.args.get("msg", ""))


# ---------------- 商品与库存 ----------------
@app.route("/erp/products")
@login_required
def products_page():
    show_removed = request.args.get("show") == "removed"
    rows = query("SELECT * FROM erp_product WHERE active=%s ORDER BY product_id LIMIT 500",
                 (0 if show_removed else 1,))
    cnt_active = int((one("SELECT COUNT(*) c FROM erp_product WHERE active=1") or {}).get("c", 0))
    cnt_removed = int((one("SELECT COUNT(*) c FROM erp_product WHERE active=0") or {}).get("c", 0))
    sum_stock = int((one("SELECT IFNULL(SUM(stock),0) s FROM erp_product WHERE active=1") or {}).get("s", 0))
    out_30 = int((one("SELECT IFNULL(SUM(-change_qty),0) q FROM erp_inventory_log "
                      "WHERE change_qty<0 AND created_at>=DATE_SUB(NOW(),INTERVAL 30 DAY)") or {}).get("q", 0))

    all_active = query("SELECT product_id,name,stock FROM erp_product WHERE active=1 ORDER BY product_id LIMIT 500")
    opts = "".join("<option value='%s'>#%s %s（现存 %s）</option>" % (
        esc(r["product_id"]), esc(r["product_id"]), esc(r["name"]), esc(r["stock"]))
        for r in all_active)
    type_opts = (
        "<optgroup label='出库 / 剔除（减少库存）'>"
        "<option value='DAMAGE'>报损剔除</option><option value='SCRAP'>报废剔除</option>"
        "<option value='SALE_OUT'>销售/领用出库</option><option value='STOCK_OUT'>其他出库</option>"
        "<option value='ADJUST'>盘点差异（减少）</option></optgroup>"
        "<optgroup label='入库（增加库存）'>"
        "<option value='PURCHASE'>采购入库</option><option value='RETURN_IN'>退货入库</option>"
        "<option value='PRODUCE_IN'>生产入库</option><option value='ADJUST'>盘点差异（增加）</option></optgroup>")
    quick_types = ("<option value='DAMAGE'>报损剔除</option><option value='SALE_OUT'>销售/领用出库</option>"
                   "<option value='PURCHASE'>采购入库</option><option value='ADJUST'>盘点调整</option>")

    op_card = (
        '<div class="card op-card"><h2>库存出入库 / 报损剔除</h2>'
        '<div class="tip">「出库 / 剔除」会减少 ERP 库存；勾选「回写网站」时网站库存同步为调整后的数值。'
        '不勾选则只改 ERP，网站后续同步商品时可能覆盖本次调整。出库数量不能超过当前库存，'
        '系统不会允许把库存减成负数。</div>'
        '<form method="post" action="/erp/products/adjust"><div class="op-grid">'
        '<div><label>商品</label><select name="product_id" required>' + opts + '</select></div>'
        '<div><label>数量</label><input type="number" name="qty" min="1" value="1" required></div>'
        '<div><label>方向</label><select name="direction">'
        '<option value="OUT">− 出库 / 剔除</option><option value="IN">+ 入库</option></select></div>'
        '<div><label>业务类型</label><select name="type">' + type_opts + '</select></div>'
        '<div><label>备注 / 原因</label><input name="note" placeholder="如：破损、盘亏、样品领用" size="22"></div>'
        '<div><label>&nbsp;</label><label style="display:flex;align-items:center;gap:6px">'
        '<input type="checkbox" name="push" value="1" checked style="width:auto"> 回写网站</label></div>'
        '<div><label>&nbsp;</label><button class="btn danger">执行调整</button></div>'
        '</div></form></div>')

    stats = ('<div class="grid">'
             '<div class="stat"><span class="n">%s</span><span class="t">在库商品</span></div>'
             '<div class="stat"><span class="n">%s</span><span class="t">已移除</span></div>'
             '<div class="stat"><span class="n">%s</span><span class="t">库存总量</span></div>'
             '<div class="stat"><span class="n">%s</span><span class="t">近 30 天出库件数</span></div>'
             '</div>' % (cnt_active, cnt_removed, sum_stock, out_30))
    switch = ('<p class="muted"><a href="/erp/products">在库商品</a> · '
              '<a href="/erp/products?show=removed">已移除商品 (%s)</a></p>' % cnt_removed)

    def row_html(r):
        pid = esc(r["product_id"])
        if show_removed:
            return ("<tr><td>%s</td><td>%s</td><td>%s</td><td>$%s</td><td><b>%s</b></td><td>%s</td>"
                    "<td><form class='inline' method='post' action='/erp/products/restore'>"
                    "<input type='hidden' name='product_id' value='%s'>"
                    "<button class='btn sm'>恢复</button></form></td></tr>" % (
                        pid, esc(r["name"]), esc(r["category"]), esc(r["price"]),
                        esc(r["stock"]), esc(r["synced_at"]), pid))
        return (
            "<tr><td>%s</td><td>%s</td><td>%s</td><td>$%s</td><td><b>%s</b></td><td>%s</td>"
            "<td><form class='inline' method='post' action='/erp/products/adjust'>"
            "<input type='hidden' name='product_id' value='%s'>"
            "<input type='number' name='qty' min='1' value='1' required style='width:62px' title='数量'> "
            "<select name='direction'><option value='IN'>+ 入库</option><option value='OUT'>− 出库</option></select> "
            "<select name='type'>%s</select> "
            "<input name='note' placeholder='备注' size='8'> "
            "<label style='font-size:12px'><input type='checkbox' name='push' value='1' checked> 回写网站</label> "
            "<button class='btn sm'>提交</button></form> "
            "<form class='inline' method='post' action='/erp/products/remove' "
            "onsubmit=\"return confirm('确认从 ERP 台账移除 #%s？网站商品不受影响')\">"
            "<input type='hidden' name='product_id' value='%s'>"
            "<button class='btn sec sm'>移除</button></form></td></tr>" % (
                pid, esc(r["name"]), esc(r["category"]), esc(r["price"]), esc(r["stock"]),
                esc(r["synced_at"]), pid, quick_types, pid, pid))

    # 阶段2：SKU 与预占 —— 可售库存 = 实物库存 − HELD 预占
    sku_rows = query("SELECT s.*, (SELECT IFNULL(SUM(r.qty),0) FROM erp_stock_reservation r "
                     "WHERE r.sku_id=s.sku_id AND r.status='HELD') held_qty "
                     "FROM erp_product_sku s ORDER BY s.product_id,s.sku_id LIMIT 200")
    held_total = int((one("SELECT IFNULL(SUM(qty),0) q FROM erp_stock_reservation WHERE status='HELD'") or {}).get("q") or 0)
    sku_tr = "".join("<tr><td>#%s</td><td>%s</td><td>%s</td><td>%s</td><td><b>%s</b></td><td>%s</td>"
                     "<td><form class='inline' method='post' action='/erp/skus/adjust'>"
                     "<input type='hidden' name='sku_id' value='%s'>"
                     "<input type='number' name='qty' min='1' value='1' required style='width:56px'> "
                     "<select name='direction'><option value='IN'>+ 入库</option><option value='OUT'>− 出库</option></select> "
                     "<button class='btn sm'>调整并回写网站</button></form></td></tr>" % (
                         esc(s["product_id"]), esc(s["sku_code"] or s["sku_id"]),
                         esc(s["option_name"] or ""), esc(s["option_value"] or ""),
                         esc(s["stock"]), esc(s["held_qty"] or 0), esc(s["sku_id"])) for s in sku_rows)
    sku_card = ('<div class="card"><h2>SKU 库存 / 预占口径</h2>'
                '<div class="tip">可售库存 = 实物库存 − 未支付订单预占；网站与 ERP 使用同一口径。'
                '当前 HELD 预占合计 <b>%s</b> 件，SKU 共 %s 行。'
                'SKU 调整会直接回写网站 product_sku.stock。</div>'
                '<table><tr><th>商品ID</th><th>SKU编码</th><th>规格</th><th>规格值</th>'
                '<th>实物库存</th><th>预占</th><th>调整</th></tr>%s</table></div>' % (
                    held_total, len(sku_rows), sku_tr or '<tr><td colspan="7">暂无 SKU</td></tr>'))

    body = (stats + op_card + (switch if not show_removed else '<p class="muted">当前显示已移除商品，可恢复。</p>')
            + sku_card
            + '<div class="card"><h2>商品主数据 / 库存调整</h2><table>'
              '<tr><th>ID</th><th>名称</th><th>分类</th><th>价格</th><th>库存</th><th>同步时间</th>'
              '<th>出入库 / 移除</th></tr>'
            + "".join(row_html(r) for r in rows) + '</table></div>')
    return page("商品与库存", body, request.args.get("msg", ""))


@app.route("/erp/skus/adjust", methods=["POST"])
@login_required
def sku_adjust():
    """ERP 调整 SKU 库存并回写网站（阶段2 双向同步）。"""
    try:
        sid = int(request.form.get("sku_id", "0"))
        qty = int(request.form.get("qty", "0"))
    except ValueError:
        return redirect("/erp/products?msg=!SKU%E7%BC%96%E5%8F%B7%E6%88%96%E6%95%B0%E9%87%8F%E4%B8%8D%E6%AD%A3%E7%A1%AE")
    direction = request.form.get("direction", "IN")
    s = one("SELECT * FROM erp_product_sku WHERE sku_id=%s", (sid,))
    if not s or qty <= 0:
        return redirect("/erp/products?msg=!SKU%E4%B8%8D%E5%AD%98%E5%9C%A8%E6%88%96%E6%95%B0%E9%87%8F%E4%B8%8D%E6%AD%A3%E7%A1%AE")
    before = int(s["stock"] or 0)
    delta = qty if direction == "IN" else -qty
    after = max(0, before + delta)
    if direction == "OUT" and qty > before:
        return redirect("/erp/products?msg=!SKU%E5%BA%93%E5%AD%98%E4%B8%8D%E8%B6%B3%EF%BC%8C%E6%9C%80%E5%A4%9A%E5%8F%AF%E5%89%94%E9%99%A4%%20%s%E4%BB%B6" % before)
    run("UPDATE erp_product_sku SET stock=%s,synced_at=NOW() WHERE sku_id=%s", (after, sid))
    run("INSERT INTO erp_inventory_log(product_id,sku_id,change_qty,before_qty,after_qty,type,ref_no,note) "
        "VALUES(%s,%s,%s,%s,%s,'SKU_ADJUST',%s,%s)",
        (s["product_id"], sid, after - before, before, after, str(sid), "ERP SKU 调整"))
    try:
        post_json(WEB_BASE + "/api/erp/callback/sku-stock", {"skuId": sid, "stock": after})
    except Exception as e:
        return redirect("/erp/products?msg=!ERP%20SKU%20%E5%BA%93%E5%AD%98%E5%B7%B2%E6%94%B9%EF%BC%8C%E5%9B%9E%E5%86%99%E7%BD%91%E7%AB%99%E5%A4%B1%E8%B4%A5%EF%BC%9A" + str(e)[:60])
    log("OUT", "/callback/sku-stock", "OK", "sku %s -> %s" % (sid, after))
    return redirect("/erp/products?msg=SKU%20%E5%BA%93%E5%AD%98%E5%B7%B2%E8%B0%83%E6%95%B4%E5%B9%B6%E5%9B%9E%E5%86%99%E7%BD%91%E7%AB%99")


@app.route("/erp/products/adjust", methods=["POST"])
@login_required
def adjust_stock():
    def back(msg, err=False):
        return redirect("/erp/products?msg=" + quote(("!" if err else "") + msg))

    try:
        pid = int(request.form.get("product_id", "0"))
    except ValueError:
        return back("商品编号不正确", True)
    direction = request.form.get("direction", "").upper()
    typ = request.form.get("type", "ADJUST").upper()
    note = request.form.get("note", "")[:200].strip()
    push = request.form.get("push") == "1"

    # 新表单：qty 为正整数 + direction 决定正负；兼容旧表单的可带符号 delta
    raw_qty = request.form.get("qty")
    try:
        if raw_qty not in (None, ""):
            qty = abs(int(raw_qty))
            if qty <= 0:
                raise ValueError
            if direction == "OUT":
                sign = -1
            elif direction == "IN":
                sign = 1
            else:
                sign = -1 if typ in OUT_TYPES else 1
            delta = sign * qty
        else:
            delta = int(request.form.get("delta", "0"))
    except ValueError:
        return back("数量必须是整数", True)
    if delta == 0:
        return back("数量不能为 0", True)

    # 类型与方向冲突时以方向为准，保证台账方向不歧义
    if delta < 0 and typ in IN_TYPES:
        typ = "STOCK_OUT"
    if delta > 0 and typ in OUT_TYPES:
        typ = "PURCHASE"

    row = one("SELECT stock,name,active FROM erp_product WHERE product_id=%s", (pid,))
    if not row:
        return back("商品不存在", True)
    if int(row.get("active", 1) or 0) == 0:
        return back("商品已移除，请先恢复", True)
    before = int(row["stock"] or 0)
    if delta < 0 and -delta > before:
        return back("库存不足：当前 %s 件，最多可剔除 %s 件" % (before, before), True)
    after = before + delta
    if after < 0:
        after = 0
    real_delta = after - before

    run("UPDATE erp_product SET stock=%s WHERE product_id=%s", (after, pid))
    run("INSERT INTO erp_inventory_log(product_id,change_qty,before_qty,after_qty,type,ref_no,note) "
        "VALUES(%s,%s,%s,%s,%s,%s,%s)",
        (pid, real_delta, before, after, typ, "MANUAL",
         (note + (" [出库/剔除]" if real_delta < 0 else " [入库]")).strip()))

    pushed = False
    if push and real_delta != 0:
        try:
            status, resp = post_json(WEB_BASE + "/api/erp/callback/stock",
                                     {"productId": pid, "stock": after, "delta": real_delta,
                                      "direction": "OUT" if real_delta < 0 else "IN",
                                      "type": typ,
                                      "note": "ERP %s %s" % (type_label(typ), note)})
            pushed = status == 200 and bool(resp.get("ok"))
            log("OUT", "/callback/stock", "OK" if pushed else "FAIL", resp)
        except Exception as e:
            log("OUT", "/callback/stock", "FAIL", e)
            return back("ERP 库存已改，但回写网站失败：%s" % str(e)[:60], True)

    verb = "剔除/出库" if real_delta < 0 else "入库"
    msg = "已%s %s 件：#%s 库存 %s → %s" % (verb, abs(real_delta), pid, before, after)
    if push and real_delta != 0:
        msg += "（网站已同步）" if pushed else "（网站未同步）"
    elif not push:
        msg += "（仅 ERP，未回写）"
    return back(msg)


@app.route("/erp/products/remove", methods=["POST"])
@login_required
def remove_product():
    try:
        pid = int(request.form.get("product_id", "0"))
    except ValueError:
        return redirect("/erp/products?msg=" + quote("!商品编号不正确"))
    run("UPDATE erp_product SET active=0 WHERE product_id=%s", (pid,))
    log("OUT", "/products/remove", "OK", "移除 #%s" % pid)
    return redirect("/erp/products?msg=" + quote("已从 ERP 台账移除商品 #%s（网站商品不受影响）" % pid))


@app.route("/erp/products/restore", methods=["POST"])
@login_required
def restore_product():
    try:
        pid = int(request.form.get("product_id", "0"))
    except ValueError:
        return redirect("/erp/products?show=removed&msg=" + quote("!商品编号不正确"))
    run("UPDATE erp_product SET active=1 WHERE product_id=%s", (pid,))
    log("OUT", "/products/restore", "OK", "恢复 #%s" % pid)
    return redirect("/erp/products?show=removed&msg=" + quote("已恢复商品 #%s" % pid))


# ---------------- 销售单 ----------------
@app.route("/erp/orders")
@login_required
def orders_page():
    rows = query("SELECT * FROM erp_sales_order ORDER BY id DESC LIMIT 200")
    tr = "".join(
        "<tr><td><a href='/erp/returns/new?order_no=%s'>%s</a></td><td>%s</td><td>%s</td><td>$%s</td>"
        "<td><span class='tag'>%s</span></td><td>%s</td>"
        "<td><a class='btn sec sm' href='/erp/returns/new?order_no=%s'>发起退货</a></td></tr>" % (
            esc(r["order_no"]), esc(r["order_no"]), esc(r["customer_name"]), esc(r["email"]),
            esc(r["total_amount"]), esc(r["status"]), esc(r["placed_at"]), esc(r["order_no"]))
        for r in rows)
    body = ('<div class="card"><table><tr><th>订单号</th><th>客户</th><th>邮箱</th><th>金额</th>'
            '<th>状态</th><th>下单时间</th><th>操作</th></tr>' + tr + '</table></div>')
    return page("销售单", body)


# ---------------- 退货单 ----------------
@app.route("/erp/returns")
@login_required
def returns_page():
    rows = query("SELECT r.*, (SELECT SUM(quantity) FROM erp_return_item i WHERE i.return_id=r.id) qty "
                 "FROM erp_return r ORDER BY r.id DESC LIMIT 200")
    tr = ""
    for r in rows:
        badge = {"APPROVED": "ok", "REJECTED": "bad", "DRAFT": "warn"}.get(r["status"], "")
        act = ""
        if r["status"] == "DRAFT":
            act = ("<form class='inline' method='post' action='/erp/returns/%s/approve'>"
                   "<button class='btn sm'>审批并回写网站</button></form> " % r["id"] +
                   "<form class='inline' method='post' action='/erp/returns/%s/reject'>"
                   "<button class='btn sec sm'>驳回</button></form>" % r["id"])
        tr += ("<tr><td>%s</td><td>%s</td><td>%s 件</td><td>$%s</td>"
               "<td><span class='tag %s'>%s</span></td><td>%s</td><td>%s</td><td>%s</td></tr>" % (
                   esc(r["return_no"]), esc(r["order_no"]), esc(r["qty"] or 0),
                   esc(r["refund_amount"]), badge, esc(r["status"]),
                   esc(r["created_at"]), esc(r["synced_at"]), act))
    body = ('<div class="card"><a class="btn" href="/erp/returns/new">+ 新建退货单</a></div>'
            '<div class="card"><table><tr><th>退货单号</th><th>原订单</th><th>数量</th><th>退款</th>'
            '<th>状态</th><th>创建</th><th>回写时间</th><th>操作</th></tr>' + tr + '</table></div>')
    return page("退货单", body, request.args.get("msg", ""))


@app.route("/erp/returns/new", methods=["GET"])
@login_required
def return_new():
    order_no = request.args.get("order_no", "")
    orders = query("SELECT order_no,customer_name,total_amount FROM erp_sales_order ORDER BY id DESC LIMIT 200")
    opts = "".join("<option value='%s' %s>%s · %s · $%s</option>" % (
        esc(r["order_no"]), "selected" if r["order_no"] == order_no else "",
        esc(r["order_no"]), esc(r["customer_name"]), esc(r["total_amount"])) for r in orders)
    items_html = ""
    if order_no:
        items = query("SELECT * FROM erp_sales_item WHERE order_no=%s", (order_no,))
        # 已退货数量（含草稿，避免重复开单）
        done = query("SELECT ri.product_id, IFNULL(SUM(ri.quantity),0) q FROM erp_return_item ri "
                     "JOIN erp_return r ON r.id=ri.return_id WHERE r.order_no=%s AND r.status<>'REJECTED' "
                     "GROUP BY ri.product_id", (order_no,))
        done_map = {int(d["product_id"]): int(d["q"]) for d in done}
        rows = "".join(
            "<tr><td>%s</td><td>%s</td><td>$%s</td><td>%s</td><td>%s</td>"
            "<td><input type='number' name='qty_%s' min='0' max='%s' value='0'></td></tr>" % (
                esc(i["product_id"]), esc(i["product_name"]), esc(i["price"]),
                esc(i["quantity"]), done_map.get(int(i["product_id"]), 0),
                esc(i["product_id"]), max(0, int(i["quantity"] or 0) - done_map.get(int(i["product_id"]), 0)))
            for i in items)
        items_html = ('<h2>%s · 可退明细</h2><table><tr><th>商品ID</th><th>名称</th><th>单价</th>'
                      '<th>购买数</th><th>已退</th><th>本次退货数</th></tr>' % esc(order_no) + rows + '</table>'
                      '<p><label>退货原因</label><input name="reason" size="60" placeholder="如：尺寸不合适"></p>'
                      '<button class="btn">保存退货单（草稿）</button>')
    body = ('<div class="card"><form method="get">选择订单 <select name="order_no" onchange="this.form.submit()">'
            '<option value="">— 请选择 —</option>' + opts + '</select></form></div>'
            '<div class="card"><form method="post" action="/erp/returns/create">'
            '<input type="hidden" name="order_no" value="%s">' % esc(order_no) + items_html + '</form></div>')
    return page("新建退货单", body)


@app.route("/erp/returns/create", methods=["POST"])
@login_required
def return_create():
    order_no = request.form.get("order_no", "").strip()
    if not order_no:
        return redirect("/erp/returns/new?msg=!%E8%AF%B7%E9%80%89%E6%8B%A9%E8%AE%A2%E5%8D%95")
    items = query("SELECT * FROM erp_sales_item WHERE order_no=%s", (order_no,))
    picked = []
    for i in items:
        try:
            qty = int(request.form.get("qty_%s" % i["product_id"], "0"))
        except ValueError:
            qty = 0
        if qty > 0:
            picked.append((i, qty))
    if not picked:
        return redirect("/erp/returns/new?order_no=" + order_no + "&msg=!%E8%87%B3%E5%B0%91%E9%80%80%E4%B8%80%E4%BB%B6%E5%95%86%E5%93%81")
    return_no = "RT" + datetime.datetime.now().strftime("%Y%m%d%H%M%S") + secrets.token_hex(2).upper()
    refund = sum(float(i["price"] or 0) * q for i, q in picked)
    rid = run("INSERT INTO erp_return(return_no,order_no,status,reason,refund_amount,created_at) "
              "VALUES(%s,%s,'DRAFT',%s,%s,NOW())",
              (return_no, order_no, request.form.get("reason", "")[:400], refund))
    for i, q in picked:
        run("INSERT INTO erp_return_item(return_id,product_id,product_name,price,quantity,refund_amount) "
            "VALUES(%s,%s,%s,%s,%s,%s)",
            (rid, i["product_id"], i["product_name"], i["price"], q, float(i["price"] or 0) * q))
    log("IN", "return/create", "OK", "%s %s 件" % (return_no, sum(q for _, q in picked)))
    return redirect("/erp/returns?msg=%E9%80%80%E8%B4%A7%E5%8D%95%E5%B7%B2%E5%88%9B%E5%BB%BA%EF%BC%8C%E8%AF%B7%E5%AE%A1%E6%89%B9")


@app.route("/erp/returns/<int:rid>/approve", methods=["POST"])
@login_required
def return_approve(rid):
    r = one("SELECT * FROM erp_return WHERE id=%s", (rid,))
    if not r or r["status"] != "DRAFT":
        return redirect("/erp/returns?msg=!%E8%AF%A5%E9%80%80%E8%B4%A7%E5%8D%95%E4%B8%8D%E5%8F%AF%E5%AE%A1%E6%89%B9")
    items = query("SELECT * FROM erp_return_item WHERE return_id=%s", (rid,))
    payload = {
        "returnNo": r["return_no"], "orderNo": r["order_no"],
        "reason": r["reason"], "refundAmount": float(r["refund_amount"] or 0),
        "items": [{"productId": int(i["product_id"]), "quantity": int(i["quantity"])} for i in items],
    }
    try:
        status, resp = post_json(WEB_BASE + "/api/erp/callback/return", payload)
        if status != 200 or not resp.get("ok"):
            raise RuntimeError("网站未接受: %s" % str(resp)[:200])
        run("UPDATE erp_return SET status='APPROVED', approved_at=NOW(), synced_at=NOW(), website_response=%s WHERE id=%s",
            (json.dumps(resp, ensure_ascii=False)[:1000], rid))
        # ERP 侧库存回补
        for i in items:
            p = one("SELECT stock FROM erp_product WHERE product_id=%s", (i["product_id"],))
            if p:
                before = int(p["stock"] or 0)
                after = before + int(i["quantity"])
                run("UPDATE erp_product SET stock=%s WHERE product_id=%s", (after, i["product_id"]))
                run("INSERT INTO erp_inventory_log(product_id,change_qty,before_qty,after_qty,type,ref_no,note) "
                    "VALUES(%s,%s,%s,%s,'RETURN',%s,%s)",
                    (i["product_id"], int(i["quantity"]), before, after, r["return_no"], "退货入库"))
        log("OUT", "/callback/return", "OK", "%s %s" % (r["return_no"], resp.get("message", "")))
        return redirect("/erp/returns?msg=%E5%B7%B2%E5%9B%9E%E5%86%99%E7%BD%91%E7%AB%99%EF%BC%9A%E5%BA%93%E5%AD%98%E5%B7%B2%E5%9B%9E%E6%BB%9A%EF%BC%8C%E9%94%80%E9%87%8F%E5%B7%B2%E6%89%A3%E5%87%8F")
    except Exception as e:
        log("OUT", "/callback/return", "FAIL", e)
        return redirect("/erp/returns?msg=!%E5%9B%9E%E5%86%99%E5%A4%B1%E8%B4%A5%EF%BC%9A" + str(e)[:80])


@app.route("/erp/returns/<int:rid>/reject", methods=["POST"])
@login_required
def return_reject(rid):
    run("UPDATE erp_return SET status='REJECTED' WHERE id=%s AND status='DRAFT'", (rid,))
    return redirect("/erp/returns?msg=%E5%B7%B2%E9%A9%B3%E5%9B%9E")


# ---------------- 台账 / 询盘 / 日志 ----------------
@app.route("/erp/inventory")
@login_required
def inventory_page():
    flt = request.args.get("filter", "all")
    pid_filter = request.args.get("product_id", "").strip()
    where, args = ["1=1"], []
    if flt == "in":
        where.append("l.change_qty>0")
    elif flt == "out":
        where.append("l.change_qty<0")
    if pid_filter.isdigit():
        where.append("l.product_id=%s")
        args.append(int(pid_filter))
    rows = query("SELECT l.*, p.name FROM erp_inventory_log l "
                 "LEFT JOIN erp_product p ON p.product_id=l.product_id WHERE "
                 + " AND ".join(where) + " ORDER BY l.id DESC LIMIT 300", args)

    def fmt(r):
        q = int(r["change_qty"] or 0)
        cls = "qty-out" if q < 0 else "qty-in"
        d = direction_of(r["type"], q)
        chip = "<span class='chip %s'>%s</span>" % (
            "out" if d == "OUT" else "", "出库" if d == "OUT" else "入库")
        return ("<tr><td>%s</td><td>%s · %s</td><td class='%s'>%+d</td><td>%s → %s</td>"
                "<td>%s %s</td><td>%s</td><td>%s</td></tr>" % (
                    esc(r["created_at"]), esc(r["product_id"]), esc(r["name"]), cls, q,
                    esc(r["before_qty"]), esc(r["after_qty"]), chip, esc(type_label(r["type"])),
                    esc(r["ref_no"]), esc(r["note"])))

    total_in = sum(max(0, int(r["change_qty"] or 0)) for r in rows)
    total_out = sum(max(0, -int(r["change_qty"] or 0)) for r in rows)
    stats = ('<div class="grid">'
             '<div class="stat"><span class="n qty-in">+%s</span><span class="t">入库合计（本页）</span></div>'
             '<div class="stat"><span class="n qty-out">-%s</span><span class="t">出库/剔除合计（本页）</span></div>'
             '<div class="stat"><span class="n">%+d</span><span class="t">净变动</span></div>'
             '<div class="stat"><span class="n">%s</span><span class="t">台账记录</span></div>'
             '</div>' % (total_in, total_out, total_in - total_out, len(rows)))
    links = ('<p class="muted"><a href="/erp/inventory?filter=all">全部</a> · '
             '<a href="/erp/inventory?filter=in">仅入库</a> · '
             '<a href="/erp/inventory?filter=out">仅出库/剔除</a> &nbsp; '
             '<form class="inline" method="get" action="/erp/inventory">'
             '<input type="hidden" name="filter" value="%s">'
             '<input name="product_id" value="%s" placeholder="商品 ID" size="8"> '
             '<button class="btn sec sm">筛选</button></form></p>' % (esc(flt), esc(pid_filter)))
    table = ('<div class="card"><table><tr><th>时间</th><th>商品</th><th>变动</th>'
             '<th>库存</th><th>方向 / 类型</th><th>单号</th><th>备注</th></tr>'
             + "".join(fmt(r) for r in rows) + '</table></div>')
    return page("库存台账", stats + links + table)


@app.route("/erp/messages")
@login_required
def messages_page():
    inq = query("SELECT * FROM erp_inquiry ORDER BY id DESC LIMIT 100")
    ideas = query("SELECT * FROM erp_custom_idea ORDER BY id DESC LIMIT 100")
    a = "".join("<tr><td>%s</td><td>%s</td><td>%s</td><td>%s</td></tr>" % (
        esc(r["created_at"]), esc(r["name"]), esc(r["email"]), esc(r["message"])) for r in inq)
    b = "".join("<tr><td>%s</td><td>%s</td><td>%s</td><td>%s</td></tr>" % (
        esc(r["created_at"]), esc(r["customer_name"]), esc(r["email"]), esc(r["idea_description"])) for r in ideas)
    return page("询盘 / 定制", '<div class="card"><h2>询盘</h2><table><tr><th>时间</th><th>姓名</th><th>邮箱</th>'
                '<th>内容</th></tr>' + a + '</table></div><div class="card"><h2>定制需求</h2>'
                '<table><tr><th>时间</th><th>客户</th><th>邮箱</th><th>描述</th></tr>' + b + '</table></div>')


@app.route("/erp/logs")
@login_required
def logs_page():
    rows = query("SELECT * FROM erp_sync_log ORDER BY id DESC LIMIT 200")
    tr = "".join("<tr><td>%s</td><td>%s</td><td>%s</td><td><span class='tag %s'>%s</span></td><td>%s</td></tr>" % (
        esc(r["created_at"]), esc(r["direction"]), esc(r["endpoint"]),
        "ok" if r["status"] == "OK" else "bad", esc(r["status"]), esc(r["message"])) for r in rows)
    return page("同步日志", '<div class="card"><table><tr><th>时间</th><th>方向</th><th>端点</th>'
                '<th>状态</th><th>说明</th></tr>' + tr + '</table></div>')


# ---------------- 对外 API（网站 → ERP） ----------------
@app.route("/erp/api/health")
def api_health():
    return jsonify({"ok": True, "service": "shine-erp",
                    "time": datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S")})


@app.route("/erp/api/products", methods=["POST"])
def api_products():
    if not api_ok(request):
        return jsonify({"ok": False, "error": "unauthorized"}), 401
    d = request.get_json(silent=True) or {}
    pid = d.get("id")
    if pid is None:
        return jsonify({"ok": False, "error": "missing id"}), 400
    old = one("SELECT stock FROM erp_product WHERE product_id=%s", (pid,))
    stock = int(d.get("stock") or 0)
    force = bool(d.get("forceStock"))   # 后台明确改库存才允许覆盖 ERP 手工调整
    cat_code = d.get("categoryCode")
    if force:
        run("""INSERT INTO erp_product(product_id,name,name_en,category,category_code,price,material,stock,customizable,image_url)
               VALUES(%s,%s,%s,%s,%s,%s,%s,%s,%s,%s)
               ON DUPLICATE KEY UPDATE name=VALUES(name),name_en=VALUES(name_en),category=VALUES(category),
               category_code=VALUES(category_code),price=VALUES(price),material=VALUES(material),stock=VALUES(stock),
               customizable=VALUES(customizable),image_url=VALUES(image_url)""",
            (pid, d.get("name"), d.get("nameEn"), d.get("category"), cat_code, d.get("price"),
             d.get("material"), stock, 1 if d.get("customizable") else 0, d.get("imageUrl")))
    else:
        run("""INSERT INTO erp_product(product_id,name,name_en,category,category_code,price,material,stock,customizable,image_url)
               VALUES(%s,%s,%s,%s,%s,%s,%s,%s,%s,%s)
               ON DUPLICATE KEY UPDATE name=VALUES(name),name_en=VALUES(name_en),category=VALUES(category),
               category_code=VALUES(category_code),price=VALUES(price),material=VALUES(material),stock=stock,
               customizable=VALUES(customizable),image_url=VALUES(image_url)""",
            (pid, d.get("name"), d.get("nameEn"), d.get("category"), cat_code, d.get("price"),
             d.get("material"), stock, 1 if d.get("customizable") else 0, d.get("imageUrl")))
    if force and (old is None or int(old["stock"] or 0) != stock):
        before = int(old["stock"] or 0) if old else 0
        run("INSERT INTO erp_inventory_log(product_id,change_qty,before_qty,after_qty,type,ref_no,note) "
            "VALUES(%s,%s,%s,%s,'SYNC',%s,%s)", (pid, stock - before, before, stock, "WEBSITE", "网站商品同步"))
    # 阶段2：SKU 主数据 + 独立库存随商品主数据一起同步
    for s in d.get("skus") or []:
        sid = s.get("skuId")
        if sid is None:
            continue
        run("""INSERT INTO erp_product_sku(sku_id,product_id,sku_code,option_name,option_value,
               option_value_en,price_delta,stock,enabled,synced_at)
               VALUES(%s,%s,%s,%s,%s,%s,%s,%s,%s,NOW())
               ON DUPLICATE KEY UPDATE product_id=VALUES(product_id),sku_code=VALUES(sku_code),
               option_name=VALUES(option_name),option_value=VALUES(option_value),
               option_value_en=VALUES(option_value_en),price_delta=VALUES(price_delta),
               stock=VALUES(stock),enabled=VALUES(enabled),synced_at=NOW()""",
            (sid, pid, s.get("skuCode"), s.get("optionName"), s.get("optionValue"),
             s.get("optionValueEn"), s.get("priceDelta") or 0, int(s.get("stock") or 0),
             1 if s.get("enabled", True) else 0))
    log("IN", "/products", "OK", "product %s forceStock=%s skus=%s" % (pid, force, len(d.get("skus") or [])))
    return jsonify({"ok": True})


@app.route("/erp/api/sku-stock", methods=["POST"])
def api_sku_stock():
    """网站 SKU 库存变动（下单/退货）→ ERP 绝对值同步（阶段2）。"""
    if not api_ok(request):
        return jsonify({"ok": False, "error": "unauthorized"}), 401
    d = request.get_json(silent=True) or {}
    sid, stock = d.get("skuId"), int(d.get("stock") or 0)
    if sid is None:
        return jsonify({"ok": False, "error": "missing skuId"}), 400
    old = one("SELECT stock,product_id FROM erp_product_sku WHERE sku_id=%s", (sid,)) or {}
    run("UPDATE erp_product_sku SET stock=%s,synced_at=NOW() WHERE sku_id=%s", (max(0, stock), sid))
    if old:
        delta = max(0, stock) - int(old.get("stock") or 0)
        if delta:
            run("INSERT INTO erp_inventory_log(product_id,sku_id,change_qty,before_qty,after_qty,type,ref_no,note) "
                "VALUES(%s,%s,%s,%s,%s,'SKU_SYNC',%s,%s)",
                (old.get("product_id"), sid, delta, int(old.get("stock") or 0), max(0, stock),
                 "WEBSITE", "网站 SKU 库存同步"))
    log("IN", "/sku-stock", "OK", "sku %s -> %s" % (sid, stock))
    return jsonify({"ok": True})


@app.route("/erp/api/reservations", methods=["POST"])
def api_reservations():
    """网站库存预占事件 → ERP 台账。ERP 可售库存 = 实物库存 − HELD 预占（阶段2）。"""
    if not api_ok(request):
        return jsonify({"ok": False, "error": "unauthorized"}), 401
    d = request.get_json(silent=True) or {}
    no, status = d.get("orderNo"), (d.get("status") or "HELD").upper()
    if not no:
        return jsonify({"ok": False, "error": "missing orderNo"}), 400
    run("DELETE FROM erp_stock_reservation WHERE order_no=%s", (no,))
    if status == "HELD":
        for it in d.get("items") or []:
            run("INSERT INTO erp_stock_reservation(order_no,product_id,sku_id,qty,status,expires_at,updated_at) "
                "VALUES(%s,%s,%s,%s,'HELD',%s,NOW())",
                (no, int(it.get("productId") or 0), it.get("skuId"),
                 int(it.get("qty") or 0), d.get("expiresAt")))
    log("IN", "/reservations", "OK", "%s %s" % (no, status))
    return jsonify({"ok": True, "status": status})


@app.route("/erp/api/orders", methods=["POST"])
def api_orders():
    if not api_ok(request):
        return jsonify({"ok": False, "error": "unauthorized"}), 401
    d = request.get_json(silent=True) or {}
    no = d.get("orderNo")
    if not no:
        return jsonify({"ok": False, "error": "missing orderNo"}), 400
    exists = one("SELECT id FROM erp_sales_order WHERE order_no=%s", (no,))
    status = d.get("status") or "PENDING"
    run("""INSERT INTO erp_sales_order(order_no,customer_name,email,phone,country,address,status,currency,
           payment_method,total_amount,subtotal_amount,discount_amount,shipping_fee,tax_amount,coupon_code,tracking_no,carrier,
           tracking_url,note,placed_at,shipped_at)
           VALUES(%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s)
           ON DUPLICATE KEY UPDATE status=VALUES(status),total_amount=VALUES(total_amount),
           subtotal_amount=VALUES(subtotal_amount),discount_amount=VALUES(discount_amount),
           shipping_fee=VALUES(shipping_fee),tax_amount=VALUES(tax_amount),
           coupon_code=VALUES(coupon_code),tracking_no=VALUES(tracking_no),carrier=VALUES(carrier),
           tracking_url=VALUES(tracking_url),note=VALUES(note),shipped_at=VALUES(shipped_at),
           customer_name=VALUES(customer_name),email=VALUES(email),phone=VALUES(phone),
           country=VALUES(country),address=VALUES(address),payment_method=VALUES(payment_method)""",
        (no, d.get("customerName"), d.get("email"), d.get("phone"), d.get("country"),
         d.get("address"), status, d.get("currency") or "USD", d.get("paymentMethod"),
         d.get("totalAmount") or 0, d.get("subtotalAmount") or 0, d.get("discountAmount") or 0,
         d.get("shippingFee") or 0, d.get("taxAmount") or 0, d.get("couponCode"), d.get("trackingNo"), d.get("carrier"), d.get("trackingUrl"),
         d.get("note"), d.get("placedAt"), d.get("shippedAt")))
    for it in d.get("items") or []:
        pid = it.get("productId")
        qty = int(it.get("quantity") or 1)
        price = it.get("price") or 0
        run("""INSERT INTO erp_sales_item(order_no,product_id,product_name,product_name_en,image_url,
               price,quantity,line_total,sku_id,sku_label)
               VALUES(%s,%s,%s,%s,%s,%s,%s,%s,%s,%s)
               ON DUPLICATE KEY UPDATE product_name=VALUES(product_name),product_name_en=VALUES(product_name_en),
               image_url=VALUES(image_url),price=VALUES(price),quantity=VALUES(quantity),
               line_total=VALUES(line_total),sku_id=VALUES(sku_id),sku_label=VALUES(sku_label)""",
            (no, pid, it.get("productName"), it.get("productNameEn"), it.get("imageUrl"),
             price, qty, float(price) * qty, it.get("skuId"), it.get("skuLabel")))
        # 新订单才扣 ERP 库存，避免重复推送导致重复扣减
        if exists is None and pid:
            p = one("SELECT stock FROM erp_product WHERE product_id=%s", (pid,))
            if p:
                before = int(p["stock"] or 0)
                after = max(0, before - qty)
                run("UPDATE erp_product SET stock=%s WHERE product_id=%s", (after, pid))
                run("INSERT INTO erp_inventory_log(product_id,change_qty,before_qty,after_qty,type,ref_no,note) "
                    "VALUES(%s,%s,%s,%s,'SALE',%s,%s)", (pid, after - before, before, after, no, "网站销售出库"))
    if status.upper() == "CANCELLED":
        restock_cancelled(no)   # 幂等：网站取消 / 后台改状态都走这里
    log("IN", "/orders", "OK", "%s %s" % (no, "更新" if exists else "新增"))
    return jsonify({"ok": True, "created": exists is None})


@app.route("/erp/api/returns", methods=["POST"])
def api_returns():
    """网站侧退货审批通过 → ERP 生成已审批退货单、回补库存、同步订单状态（阶段1）。"""
    if not api_ok(request):
        return jsonify({"ok": False, "error": "unauthorized"}), 401
    d = request.get_json(silent=True) or {}
    rn, no = d.get("returnNo"), d.get("orderNo")
    if not rn or not no:
        return jsonify({"ok": False, "error": "missing returnNo/orderNo"}), 400
    if one("SELECT id FROM erp_return WHERE return_no=%s", (rn,)):
        return jsonify({"ok": True, "duplicate": True})
    items = d.get("items") or []
    refund = float(d.get("refundAmount") or 0)
    rid = run("INSERT INTO erp_return(return_no,order_no,status,reason,refund_amount,source,created_at,synced_at) "
              "VALUES(%s,%s,'APPROVED',%s,%s,%s,NOW(),NOW())",
              (rn, no, (d.get("reason") or "")[:400], refund, d.get("source") or "WEB"))
    for it in items:
        try:
            pid, qty = int(it.get("productId")), int(it.get("quantity") or 0)
        except (TypeError, ValueError):
            continue
        if qty <= 0:
            continue
        oi = one("SELECT price,product_name FROM erp_sales_item WHERE order_no=%s AND product_id=%s LIMIT 1",
                 (no, pid)) or {}
        price = float(oi.get("price") or 0)
        name = oi.get("product_name") or ("#%s" % pid)
        run("INSERT INTO erp_return_item(return_id,product_id,product_name,price,quantity,refund_amount) "
            "VALUES(%s,%s,%s,%s,%s,%s)", (rid, pid, name, price, qty, price * qty))
        # ERP 实物库存回补（与网站库存回滚保持同一口径）
        p = one("SELECT stock FROM erp_product WHERE product_id=%s", (pid,))
        if p:
            before = int(p["stock"] or 0)
            after = before + qty
            run("UPDATE erp_product SET stock=%s WHERE product_id=%s", (after, pid))
            run("INSERT INTO erp_inventory_log(product_id,change_qty,before_qty,after_qty,type,ref_no,note) "
                "VALUES(%s,%s,%s,%s,'RETURN',%s,%s)", (pid, qty, before, after, rn, "网站退货回补"))
    bought = int((one("SELECT IFNULL(SUM(quantity),0) q FROM erp_sales_item WHERE order_no=%s", (no,)) or {}).get("q") or 0)
    ret = int((one("SELECT IFNULL(SUM(ri.quantity),0) q FROM erp_return_item ri "
                   "JOIN erp_return r ON r.id=ri.return_id WHERE r.order_no=%s AND r.status='APPROVED'", (no,))
               or {}).get("q") or 0)
    run("UPDATE erp_sales_order SET status=%s WHERE order_no=%s",
        ("REFUNDED" if bought > 0 and ret >= bought else "PARTIAL_RETURN", no))
    log("IN", "/returns", "OK", "%s %s 件" % (rn, sum(int(i.get("quantity") or 0) for i in items)))
    return jsonify({"ok": True, "returnId": rid})


@app.route("/erp/api/orders/delete", methods=["POST"])
def api_order_delete():
    """网站后台删除订单时同步删除，避免 ERP 里留下幽灵销售单"""
    if not api_ok(request):
        return jsonify({"ok": False, "error": "unauthorized"}), 401
    no = (request.get_json(silent=True) or {}).get("orderNo")
    if not no:
        return jsonify({"ok": False, "error": "missing orderNo"}), 400
    restock_cancelled(no)   # 幂等回补：删单不应让 ERP 库存永久少算
    run("DELETE FROM erp_sales_item WHERE order_no=%s", (no,))
    run("DELETE FROM erp_sales_order WHERE order_no=%s", (no,))
    log("IN", "/orders/delete", "OK", no)
    return jsonify({"ok": True})


@app.route("/erp/api/products/delete", methods=["POST"])
def api_product_delete():
    """网站删除商品时 ERP 软删除，保留历史台账"""
    if not api_ok(request):
        return jsonify({"ok": False, "error": "unauthorized"}), 401
    pid = (request.get_json(silent=True) or {}).get("id")
    if pid is None:
        return jsonify({"ok": False, "error": "missing id"}), 400
    run("UPDATE erp_product SET active=0 WHERE product_id=%s", (pid,))
    log("IN", "/products/delete", "OK", "product %s" % pid)
    return jsonify({"ok": True})


@app.route("/erp/api/orders/cancel", methods=["POST"])
def api_order_cancel():
    """订单取消 / 预占超时：幂等回补库存"""
    if not api_ok(request):
        return jsonify({"ok": False, "error": "unauthorized"}), 401
    no = (request.get_json(silent=True) or {}).get("orderNo")
    if not no:
        return jsonify({"ok": False, "error": "missing orderNo"}), 400
    changed = restock_cancelled(no)
    log("IN", "/orders/cancel", "OK", "%s %s" % (no, "回补" if changed else "已处理"))
    return jsonify({"ok": True, "restocked": changed})


@app.route("/erp/api/reconcile", methods=["POST"])
def api_reconcile():
    """网站 ↔ ERP 两库差异快照（只读，不自动改数据）"""
    if not api_ok(request):
        return jsonify({"ok": False, "error": "unauthorized"}), 401
    c = conn()
    try:
        with c.cursor() as cur:
            cur.execute("SELECT id,stock,name FROM shine.product")
            web_p = {int(r["id"]): (int(r["stock"] or 0), r["name"]) for r in cur.fetchall()}
            cur.execute("SELECT product_id,stock,active FROM shine_erp.erp_product")
            erp_p = {int(r["product_id"]): (int(r["stock"] or 0), int(r["active"] or 0)) for r in cur.fetchall()}
            cur.execute("SELECT order_no,status FROM shine.shop_order")
            web_o = {str(r["order_no"]): str(r["status"]) for r in cur.fetchall()}
            cur.execute("SELECT order_no,status FROM shine_erp.erp_sales_order")
            erp_o = {str(r["order_no"]): str(r["status"]) for r in cur.fetchall()}
            cur.execute("SELECT COUNT(*) c FROM shine.inquiry"); web_inq = cur.fetchone()["c"]
            cur.execute("SELECT COUNT(*) c FROM shine_erp.erp_inquiry"); erp_inq = cur.fetchone()["c"]
            cur.execute("SELECT COUNT(*) c FROM shine.custom_idea"); web_idea = cur.fetchone()["c"]
            cur.execute("SELECT COUNT(*) c FROM shine_erp.erp_custom_idea"); erp_idea = cur.fetchone()["c"]
            cur.execute("SELECT COUNT(*) c FROM shine.order_return"); web_ret = cur.fetchone()["c"]
            cur.execute("SELECT COUNT(*) c FROM shine_erp.erp_return"); erp_ret = cur.fetchone()["c"]
    finally:
        c.close()

    stock_diff = [{"id": pid, "web": web_p[pid][0], "erp": erp_p.get(pid, (None, 1))[0],
                   "name": web_p[pid][1]}
                  for pid in sorted(web_p) if pid in erp_p and web_p[pid][0] != erp_p[pid][0]]
    web_only_p = [pid for pid in sorted(web_p) if pid not in erp_p]
    erp_only_p = [pid for pid in sorted(erp_p) if pid not in web_p]
    web_only_o = [{"orderNo": k, "status": v} for k, v in web_o.items() if k not in erp_o]
    erp_only_o = [{"orderNo": k, "status": v} for k, v in erp_o.items() if k not in web_o]
    mismatch = [{"orderNo": k, "web": v, "erp": erp_o[k]}
                for k, v in web_o.items() if k in erp_o and v != erp_o[k]]
    return jsonify({
        "ok": True,
        "time": datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
        "products": {"web": len(web_p), "erp": len(erp_p),
                     "erpRemoved": sum(1 for v in erp_p.values() if v[1] == 0),
                     "stockDiff": stock_diff[:50], "stockDiffCount": len(stock_diff),
                     "webOnly": web_only_p[:50], "erpOnly": erp_only_p[:50]},
        "orders": {"web": len(web_o), "erp": len(erp_o),
                   "webOnly": web_only_o[:50], "webOnlyCount": len(web_only_o),
                   "erpOnly": erp_only_o[:50], "erpOnlyCount": len(erp_only_o),
                   "statusMismatch": mismatch[:50]},
        "inquiries": {"web": web_inq, "erp": erp_inq},
        "customIdeas": {"web": web_idea, "erp": erp_idea},
        "returns": {"web": web_ret, "erp": erp_ret},
    })


@app.route("/erp/api/inquiries", methods=["POST"])
def api_inquiries():
    if not api_ok(request):
        return jsonify({"ok": False, "error": "unauthorized"}), 401
    d = request.get_json(silent=True) or {}
    run("INSERT INTO erp_inquiry(source_id,name,email,subject,message,created_at) VALUES(%s,%s,%s,%s,%s,NOW())",
        (d.get("id"), d.get("name"), d.get("email"), d.get("subject"), d.get("message")))
    log("IN", "/inquiries", "OK", "inquiry %s" % d.get("id"))
    return jsonify({"ok": True})


@app.route("/erp/api/custom-ideas", methods=["POST"])
def api_ideas():
    if not api_ok(request):
        return jsonify({"ok": False, "error": "unauthorized"}), 401
    d = request.get_json(silent=True) or {}
    run("""INSERT INTO erp_custom_idea(source_id,customer_name,email,country,idea_description,
           expect_category,reference_image_url,created_at) VALUES(%s,%s,%s,%s,%s,%s,%s,NOW())""",
        (d.get("id"), d.get("customerName"), d.get("email"), d.get("country"),
         d.get("ideaDescription"), d.get("expectCategory"), d.get("referenceImageUrl")))
    log("IN", "/custom-ideas", "OK", "idea %s" % d.get("id"))
    return jsonify({"ok": True})


@app.route("/erp/api/inventory/sync", methods=["POST"])
def api_inventory_sync():
    """以网站 product 表为准，拉齐 ERP 库存（全量对账）"""
    if not api_ok(request):
        return jsonify({"ok": False, "error": "unauthorized"}), 401
    c = conn()
    n = 0
    try:
        with c.cursor() as cur:
            cur.execute("SELECT id,stock,name FROM shine.product")
            site_rows = cur.fetchall()
        for r in site_rows:
            pid, stock = r["id"], int(r["stock"] or 0)
            old = one("SELECT stock,active FROM erp_product WHERE product_id=%s", (pid,))
            if old is None:
                run("INSERT INTO erp_product(product_id,name,stock) VALUES(%s,%s,%s)", (pid, r["name"], stock))
            elif int(old.get("active", 1) or 0) == 0:
                continue   # 已从 ERP 台账移除，不参与对账
            else:
                before = int(old["stock"] or 0)
                if before != stock:
                    run("UPDATE erp_product SET stock=%s WHERE product_id=%s", (stock, pid))
                    run("INSERT INTO erp_inventory_log(product_id,change_qty,before_qty,after_qty,type,ref_no,note) "
                        "VALUES(%s,%s,%s,%s,'ADJUST',%s,%s)", (pid, stock - before, before, stock, "SYNC", "全量对账"))
                    n += 1
    finally:
        c.close()
    log("IN", "/inventory/sync", "OK", "对齐 %s 个商品库存" % n)
    return jsonify({"ok": True, "adjusted": n})


if __name__ == "__main__":
    ensure_schema()
    init_user()
    app.run(host="127.0.0.1", port=8096, threaded=True)
