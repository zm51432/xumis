#!/usr/bin/env bash
# ============================================================================
# shine 星心 · APK 推送服务 安装脚本（在服务器上执行）
# ----------------------------------------------------------------------------
# 前置：已把 deploy/ota 下的文件上传到 /tmp/shine-ota-pkg/
#   ota_server.py  admin.html  shine-ota.service  nginx-ota.conf
# 用法：bash /tmp/shine-ota-pkg/install.sh
# ============================================================================
set -euo pipefail

PKG="${PKG:-/tmp/shine-ota-pkg}"
APP=/opt/shine-ota
DATA=/var/lib/shine-ota
SNIP=/etc/nginx/ota-location.conf
SITE=/etc/nginx/sites-available/xumis
SVC=/etc/systemd/system/shine-ota.service

say() { printf '\033[36m==> %s\033[0m\n' "$*"; }
ok()  { printf '\033[32m    ✓ %s\033[0m\n' "$*"; }
die() { printf '\033[31m    ✗ %s\033[0m\n' "$*"; exit 1; }

[ "$(id -u)" -eq 0 ] || die "请用 root 执行"
[ -f "$PKG/ota_server.py" ] || die "找不到 $PKG/ota_server.py"

say "[1/6] 安装文件"
mkdir -p "$APP/web" "$DATA/releases" "$DATA/tmp"
install -m 0755 "$PKG/ota_server.py" "$APP/ota_server.py"
install -m 0644 "$PKG/admin.html"    "$APP/web/admin.html"
chmod 0700 "$DATA"
ok "$APP / $DATA"

say "[2/6] 系统服务"
install -m 0644 "$PKG/shine-ota.service" "$SVC"
systemctl daemon-reload
systemctl enable shine-ota >/dev/null 2>&1 || true
systemctl restart shine-ota
sleep 1
systemctl is-active --quiet shine-ota || { tail -30 /var/log/shine-ota.log 2>/dev/null || true; die "服务启动失败"; }
ok "shine-ota.service 运行中（127.0.0.1:8095）"

say "[3/6] nginx 片段"
install -m 0644 "$PKG/nginx-ota.conf" "$SNIP"
if grep -q "ota-location.conf" "$SITE"; then
  ok "已存在 include，跳过"
else
  BAK="$SITE.bak-$(date +%Y%m%d%H%M%S)"
  cp -a "$SITE" "$BAK"
  LN=$(grep -n "download-location.conf" "$SITE" | head -1 | cut -d: -f1 || true)
  [ -n "${LN:-}" ] || LN=$(grep -n "^server {" "$SITE" | head -1 | cut -d: -f1 || true)
  [ -n "${LN:-}" ] || die "未能定位 server 块"
  sed -i "${LN}a\\    include /etc/nginx/ota-location.conf;" "$SITE"
  ok "已插入 include（备份 $BAK）"
fi

say "[4/6] 校验并重载 nginx"
nginx -t || die "nginx 配置校验失败"
systemctl reload nginx
ok "已重载"

say "[5/6] 自测"
printf "    %-46s %s\n" "/apk/ota/healthz" \
  "$(curl -s -o /dev/null -w '%{http_code}' --resolve shine.xumois.cn:443:127.0.0.1 https://shine.xumois.cn/apk/ota/healthz)"
printf "    %-46s %s\n" "/apk/ota/（管理台）" \
  "$(curl -s -o /dev/null -w '%{http_code} %{size_download}B' --resolve shine.xumois.cn:443:127.0.0.1 https://shine.xumois.cn/apk/ota/)"
printf "    %-46s %s\n" "/apk/ota/api/check?vc=1" \
  "$(curl -s --resolve shine.xumois.cn:443:127.0.0.1 'https://shine.xumois.cn/apk/ota/api/check?vc=1' | head -c 160)"

say "[6/6] 初始口令"
python3 - <<'PY'
import json
p = "/var/lib/shine-ota/admin.json"
try:
    a = json.load(open(p))
    if a.get("initialPassword"):
        print("    初始管理口令：\033[1;33m%s\033[0m" % a["initialPassword"])
        print("    登录 https://shine.xumois.cn/apk/ota/ 后请立即修改")
    else:
        print("    口令已修改过，不再显示")
except Exception as e:
    print("    读取失败：%s" % e)
PY

echo
echo "✅ 完成：https://shine.xumois.cn/apk/ota/"
