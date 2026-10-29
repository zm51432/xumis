#!/usr/bin/env python3
"""
生成 App 内置离线商品册（assets/offline/catalog.html）与离线商品图。

用途：APK 装上后即使断网/弱网，也能浏览最近一次快照的商品（图、价、材质、描述），
不依赖服务器。发布新版本前跑一次即可刷新快照：

    python3 android/tools/gen_offline_catalog.py

可选参数：--base https://shine.xumois.cn
"""

import argparse
import html
import json
import os
import shutil
import urllib.request

HERE = os.path.dirname(os.path.abspath(__file__))
ASSETS = os.path.join(HERE, "..", "app", "src", "main", "assets", "offline")
IMG_DIR = os.path.join(ASSETS, "img")

TEMPLATE = """<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
<title>shine 星心 · 离线商品册</title>
<link rel="stylesheet" href="app.css">
</head>
<body>

<div class="appbar">
  <div class="logo">✦</div>
  <div class="brand">离线商品册<small>Offline Catalog</small></div>
</div>

<div class="wrap">

  <div class="note">
    这是随 App 打包的商品快照（共 {count} 件，生成于 {date}）。联网后回到首页可获取最新价格与库存。
  </div>

  <div class="grid" id="grid">
{cards}
  </div>

  <div class="footer">
    <div>数据来源：shine.xumois.cn · 快照 {date}</div>
    <div><a href="index.html">返回离线首页</a> · <a href="policies.html">配送与售后条款</a></div>
  </div>
</div>

<div class="sheet" id="sheet">
  <div class="panel">
    <img class="hero" id="sImg" alt="">
    <div class="info">
      <h3 id="sName"></h3>
      <div class="price" id="sPrice"></div>
      <dl>
        <dt>分类</dt><dd id="sCat"></dd>
        <dt>材质</dt><dd id="sMat"></dd>
        <dt>库存</dt><dd id="sStock"></dd>
      </dl>
      <div class="desc" id="sDesc"></div>
      <div class="btn-row">
        <a class="btn" id="sOnline" href="#">联网查看最新</a>
        <button class="btn ghost" id="sClose">关闭</button>
      </div>
    </div>
  </div>
</div>

<script>
(function () {{
  var data = {payload};

  var sheet = document.getElementById('sheet');
  var el = {{
    img: document.getElementById('sImg'),
    name: document.getElementById('sName'),
    price: document.getElementById('sPrice'),
    cat: document.getElementById('sCat'),
    mat: document.getElementById('sMat'),
    stock: document.getElementById('sStock'),
    desc: document.getElementById('sDesc'),
    online: document.getElementById('sOnline')
  }};

  document.getElementById('grid').addEventListener('click', function (e) {{
    var card = e.target.closest('.item');
    if (!card) return;
    var item = data[+card.dataset.idx];
    el.img.src = item.img;
    el.name.textContent = item.name;
    el.price.textContent = '$' + item.price;
    el.cat.textContent = item.category || '—';
    el.mat.textContent = item.material || '—';
    el.stock.textContent = item.stock > 0 ? ('现货 ' + item.stock + ' 件') : '暂时缺货';
    el.desc.textContent = item.description || '（暂无描述）';
    el.online.href = item.url;
    sheet.classList.add('open');
  }});

  document.getElementById('sClose').addEventListener('click', function () {{
    sheet.classList.remove('open');
  }});
  sheet.addEventListener('click', function (e) {{
    if (e.target === sheet) sheet.classList.remove('open');
  }});
}})();
</script>
</body>
</html>
"""

CARD = """    <div class="item" data-idx="{idx}">
      <img src="img/{file}" alt="{name}" loading="lazy">
      <div class="body">
        <div class="name">{name}</div>
        <div class="price">${price}</div>
        <div class="tag">{category}</div>
      </div>
    </div>"""


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--base", default="https://shine.xumois.cn")
    args = ap.parse_args()

    os.makedirs(IMG_DIR, exist_ok=True)
    with urllib.request.urlopen(args.base + "/api/m/products?page=0&size=50", timeout=30) as r:
        page = json.load(r)
    products = page.get("content", [])
    if not products:
        raise SystemExit("接口没有返回商品，取消生成")

    items, cards = [], []
    for i, p in enumerate(products):
        src = p.get("imageUrl") or ""
        fname = os.path.basename(src) or "p%02d.png" % (i + 1)
        local = os.path.join(IMG_DIR, fname)
        if not os.path.exists(local) and src:
            with urllib.request.urlopen(args.base + src, timeout=60) as r, open(local, "wb") as fh:
                shutil.copyfileobj(r, fh)
        items.append({
            "img": "img/" + fname,
            "name": p.get("displayName") or p.get("name") or "商品",
            "price": "%.2f" % (p.get("price") or 0),
            "category": p.get("category") or "",
            "material": p.get("material") or "",
            "stock": p.get("stock") or 0,
            "description": p.get("description") or "",
            "url": args.base + "/product/" + str(p.get("id")),
        })
        cards.append(CARD.format(
            idx=i, file=fname,
            name=html.escape(items[-1]["name"]),
            price=items[-1]["price"],
            category=html.escape(items[-1]["category"]),
        ))

    date = __import__("datetime").date.today().isoformat()
    out = TEMPLATE.format(
        count=len(items), date=date, cards="\n".join(cards),
        payload=json.dumps(items, ensure_ascii=False),
    )
    dest = os.path.join(ASSETS, "catalog.html")
    with open(dest, "w", encoding="utf-8") as f:
        f.write(out)
    size = sum(os.path.getsize(os.path.join(IMG_DIR, f)) for f in os.listdir(IMG_DIR))
    print("已生成 %s（%d 件商品，图片 %d 个 / %.1f MB）" % (
        dest, len(items), len(os.listdir(IMG_DIR)), size / 1048576))


if __name__ == "__main__":
    main()
