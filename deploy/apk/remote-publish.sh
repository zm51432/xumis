#!/usr/bin/env bash
# 在服务器上执行：把 APK 下载目录挂到 shine.xumois.cn/download/
# 前置：shine-xumis.apk、index.html 已上传到 /var/www/download/，
#       nginx-download.conf 已上传到 /etc/nginx/download-location.conf
set -euo pipefail

SITE=/etc/nginx/sites-available/xumis
SNIP=/etc/nginx/download-location.conf
DIR=/var/www/download

echo "==> [1/5] 检查文件"
ls -l "$DIR/shine-xumis.apk" "$DIR/index.html" "$SNIP"

echo "==> [2/5] 权限"
chmod 755 /var/www /var/www/download
chmod 644 "$DIR"/shine-xumis.apk "$DIR"/index.html "$SNIP"
if id nginx >/dev/null 2>&1; then NGINX_USER=nginx; elif id www-data >/dev/null 2>&1; then NGINX_USER=www-data; else NGINX_USER=root; fi
chown -R root:"$NGINX_USER" /var/www/download 2>/dev/null || true
echo "    nginx 运行用户：$NGINX_USER"

echo "==> [3/5] 挂载 nginx include（幂等）"
if grep -q "download-location.conf" "$SITE"; then
  echo "    已存在 include，跳过"
else
  BAK="$SITE.bak-$(date +%Y%m%d%H%M%S)"
  cp -a "$SITE" "$BAK"
  echo "    已备份：$BAK"
  LN=$(grep -n "honeypot-location.conf" "$SITE" | head -1 | cut -d: -f1 || true)
  if [ -z "${LN:-}" ]; then
    LN=$(grep -n "^server {" "$SITE" | head -1 | cut -d: -f1 || true)
  fi
  if [ -z "${LN:-}" ]; then
    echo "    ✗ 未能定位插入点，请手动在 server 块内加：include $SNIP;"
    exit 1
  fi
  sed -i "${LN}a\\    include /etc/nginx/download-location.conf;" "$SITE"
  echo "    已在第 ${LN} 行后插入 include"
fi

echo "==> [4/5] 校验并重载 nginx"
nginx -t
systemctl reload nginx
sleep 1

echo "==> [5/5] 本地自测"
for u in /download/ /download/shine-xumis.apk; do
  printf "    %-32s %s\n" "$u" \
    "$(curl -sk -o /dev/null -w '%{http_code} %{content_type} %{size_download}B' --resolve shine.xumois.cn:443:127.0.0.1 "https://shine.xumois.cn$u")"
done

echo
echo "✅ 完成：https://shine.xumois.cn/download/"
