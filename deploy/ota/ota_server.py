#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
shine 星心 · APK 版本推送服务 (OTA)

职责
----
1. 对外提供客户端升级检查接口  GET  /apk/ota/api/check?vc=<versionCode>&ch=stable
2. 提供安装包下载（支持 Range 断点续传）GET /apk/ota/dl/<file>.apk
3. 提供站长使用的发布管理台         GET  /apk/ota/
4. 一键推送：上传 APK → 解析版本信息 → 设为线上版本 → 同步下载页文件

依赖：仅 Python 3.6+ 标准库；签名证书解析优先用 JDK 的 keytool，缺失时退回内置解析。

目录约定（默认 /var/lib/shine-ota）
    state.json          版本清单、统计、审计日志
    admin.json          登录口令哈希 + 会话密钥（首次启动生成）
    releases/*.apk      所有已上传的安装包
    tmp/                上传临时文件
"""

import argparse
import base64
import hashlib
import hmac
import json
import os
import re
import secrets
import shutil
import struct
import subprocess
import sys
import threading
import time
import zipfile
from datetime import datetime, timedelta, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlparse, parse_qs, unquote

VERSION = "1.0.0"
PREFIX = "/apk/ota"          # nginx 反代保留前缀
CST = timezone(timedelta(hours=8))

# ---------------------------------------------------------------- 工具函数


def now_ts():
    return int(time.time())


def iso(ts):
    if not ts:
        return ""
    return datetime.fromtimestamp(ts, CST).strftime("%Y-%m-%d %H:%M")


def day_key(ts=None):
    return datetime.fromtimestamp(ts or now_ts(), CST).strftime("%Y-%m-%d")


def human_size(n):
    n = float(n or 0)
    for unit in ("B", "KB", "MB", "GB"):
        if n < 1024 or unit == "GB":
            return ("%d %s" % (n, unit)) if unit == "B" else ("%.1f %s" % (n, unit))
        n /= 1024.0
    return "%.1f GB" % n


def safe_name(s):
    return re.sub(r"[^A-Za-z0-9._-]", "_", s or "")[:96]


# ------------------------------------------------------------ APK 元信息解析

AXML_MAGIC = 0x00080003


def _axml_string_pool(buf, off):
    ctype, hsize, csize = struct.unpack_from("<HHI", buf, off)
    if ctype != 0x0001:
        return []
    count, style_count, flags, strings_start, styles_start = struct.unpack_from(
        "<IIIII", buf, off + 8
    )
    offsets = struct.unpack_from("<%dI" % count, buf, off + hsize) if count else ()
    base = off + strings_start
    utf8 = bool(flags & (1 << 8))
    out = []
    for o in offsets:
        p = base + o
        try:
            if utf8:
                n = buf[p]
                p += 1
                if n & 0x80:
                    n = ((n & 0x7F) << 8) | buf[p]
                    p += 1
                m = buf[p]
                p += 1
                if m & 0x80:
                    m = ((m & 0x7F) << 8) | buf[p]
                    p += 1
                out.append(buf[p:p + m].decode("utf-8", "replace"))
            else:
                n = struct.unpack_from("<H", buf, p)[0]
                p += 2
                if n & 0x8000:
                    n = ((n & 0x7FFF) << 16) | struct.unpack_from("<H", buf, p)[0]
                    p += 2
                out.append(buf[p:p + n * 2].decode("utf-16-le", "replace"))
        except Exception:
            out.append("")
    return out


def parse_android_manifest(data):
    """解析二进制 AndroidManifest.xml，取出 package / versionCode / versionName / minSdk。

    AXML 里元素名与属性名都是字符串池下标，但不同打包工具写出的基准不同
    （有的用 1 基、有的用 0 基），且属性名可能附带资源 ID。这里统一按
    「属性名字符串」匹配，并用根元素名反推基准，避免被下标约定坑到。
    """
    info = {}
    try:
        if len(data) < 8:
            return info
        if struct.unpack_from("<I", data, 0)[0] != AXML_MAGIC:
            text = data.decode("utf-8", "replace")
            for pat, key in ((r'package="([^"]+)"', "package"),
                             (r'android:versionCode="(\d+)"', "versionCode"),
                             (r'android:versionName="([^"]+)"', "versionName")):
                m = re.search(pat, text)
                if m:
                    info[key] = m.group(1)
            if "versionCode" in info:
                info["versionCode"] = int(info["versionCode"])
            return info

        off = 8
        strings = []
        elems = []          # [(name_idx, [(name_idx, raw, ttype, tdata), ...]), ...]
        total = len(data)
        while off + 8 <= total:
            ctype, hsize, csize = struct.unpack_from("<HHI", data, off)
            if csize <= 0 or off + csize > total:
                break
            if ctype == 0x0001:
                strings = _axml_string_pool(data, off)
            elif ctype == 0x0102:  # START_ELEMENT
                body = off + hsize
                ns, name, attr_start, attr_size, attr_count = struct.unpack_from(
                    "<IIHHH", data, body
                )
                attrs = []
                for i in range(attr_count):
                    p = body + attr_start + i * attr_size
                    if p + 20 > total:
                        break
                    attrs.append(struct.unpack_from("<IIIHBBI", data, p)[1:])
                elems.append((name, attrs))
            off += csize

        if not elems or not strings:
            return info

        # 反推下标基准：让第一个元素解析为 manifest
        base = 1
        for cand in (1, 0):
            idx = elems[0][0] - cand
            if 0 <= idx < len(strings) and strings[idx] == "manifest":
                base = cand
                break

        def sname(idx):
            i = idx - base
            return strings[i] if 0 <= i < len(strings) else ""

        def sval(raw, ttype, tdata):
            if raw != 0xFFFFFFFF and raw - base < len(strings):
                return strings[raw - base]
            if ttype == 0x03:
                return sname(tdata)
            if ttype in (0x10, 0x11):
                return tdata
            if ttype == 0x12:
                return bool(tdata)
            return tdata

        for name_idx, attrs in elems:
            elem = sname(name_idx)
            kv = {}
            for a_name, a_raw, a_tsz, a_res0, a_type, a_data in attrs:
                kv[sname(a_name)] = sval(a_raw, a_type, a_data)

            if elem == "manifest":
                info["package"] = str(kv.get("package", "") or "")
                try:
                    info["versionCode"] = int(kv.get("versionCode", 0) or 0)
                except Exception:
                    info["versionCode"] = 0
                vn = kv.get("versionName")
                info["versionName"] = str(vn) if vn is not None else ""
            elif elem == "uses-sdk":
                try:
                    info["minSdk"] = int(kv.get("minSdkVersion", 0) or 0)
                except Exception:
                    info["minSdk"] = 0
            elif elem == "application":
                lbl = kv.get("label")
                if isinstance(lbl, str) and lbl and not lbl.startswith("@"):
                    info["label"] = lbl
    except Exception as exc:  # 解析失败不影响上传，只是信息不全
        info["_error"] = str(exc)
    return info


def _lp(buf, off):
    """读取 u32 长度前缀数据。"""
    n = struct.unpack_from("<I", buf, off)[0]
    return buf[off + 4:off + 4 + n], off + 4 + n


def _signer_from_v2(path):
    """从 APK Signing Block 提取 v2/v3 签名证书 DER。"""
    with open(path, "rb") as f:
        f.seek(0, 2)
        size = f.tell()
        tail_len = min(size, 128 * 1024)
        f.seek(size - tail_len)
        tail = f.read()
        idx = tail.rfind(b"PK\x05\x06")
        if idx < 0:
            return None
        eocd = tail[idx:idx + 22]
        cd_size, cd_off = struct.unpack_from("<II", eocd, 12)
        if cd_off < 32 or cd_off > size:
            return None
        f.seek(cd_off - 24)
        foot = f.read(24)
        if len(foot) < 24 or foot[8:] != b"APK Sig Block 42":
            return None
        blk_size = struct.unpack_from("<Q", foot, 0)[0]
        start = cd_off - blk_size - 8
        if start < 0:
            return None
        f.seek(start)
        blk = f.read(blk_size + 8)
    pos = 8
    end = len(blk) - 24
    while pos + 12 <= end:
        ln = struct.unpack_from("<Q", blk, pos)[0]
        pid = struct.unpack_from("<I", blk, pos + 8)[0]
        if ln < 4 or pos + 8 + ln > len(blk):
            break
        val = blk[pos + 12:pos + 8 + ln]
        if pid in (0x7109871A, 0x1B93AD61):  # v2 / v3 scheme
            try:
                signers, _ = _lp(val, 0)
                signer, _ = _lp(signers, 0)
                signed_data, p = _lp(signer, 0)
                digests, p2 = _lp(signed_data, 0)
                certs, _ = _lp(signed_data, p2)
                cert, _ = _lp(certs, 0)
                if cert:
                    return cert
            except Exception:
                pass
        pos += 8 + ln
    return None


def apk_signer_sha256(path):
    """返回 (指纹, 来源)。指纹为大写冒号分隔，与 keytool 输出一致；失败返回 (None, 原因)。"""
    try:
        der = _signer_from_v2(path)
        if der:
            return ":".join("%02X" % b for b in hashlib.sha256(der).digest()), "v2"
    except Exception:
        pass
    try:
        out = subprocess.run(
            ["keytool", "-printcert", "-jarfile", path],
            capture_output=True, text=True, timeout=40,
        ).stdout
        m = re.search(r"SHA256:\s*([0-9A-Fa-f:]{60,})", out)
        if m:
            return m.group(1).upper(), "v1"
    except Exception:
        pass
    return None, "未识别"


def read_apk(path):
    """汇总 APK 的版本信息、大小、哈希、签名。"""
    meta = {"size": os.path.getsize(path),
            "sha256": hashlib.sha256(open(path, "rb").read()).hexdigest()}
    try:
        with zipfile.ZipFile(path) as z:
            meta.update(parse_android_manifest(z.read("AndroidManifest.xml")))
    except Exception as exc:
        meta["_error"] = str(exc)
    fp, src = apk_signer_sha256(path)
    meta["signerSha256"] = fp
    meta["signerSource"] = src
    return meta


# ------------------------------------------------------------------ 数据存储


class Store(object):
    def __init__(self, data_dir):
        self.dir = data_dir
        self.releases_dir = os.path.join(data_dir, "releases")
        self.tmp_dir = os.path.join(data_dir, "tmp")
        for d in (data_dir, self.releases_dir, self.tmp_dir):
            os.makedirs(d, exist_ok=True)
        self.lock = threading.RLock()
        self.state = self._load("state.json", {
            "schema": 1, "releases": [], "current": {"stable": None, "beta": None},
            "stats": {"checks": {}, "downloads": {}, "clients": {}}, "audit": [],
        })
        admin = self._load("admin.json", None)
        if not admin:
            salt = secrets.token_hex(16)
            iters = 200000
            pwd = os.environ.get("OTA_ADMIN_PASSWORD") or ("Shine-" + secrets.token_hex(4))
            admin = {
                "salt": salt,
                "iterations": iters,
                "pwd": hashlib.pbkdf2_hmac("sha256", pwd.encode("utf-8"),
                                           bytes.fromhex(salt), iters).hex(),
                "secret": secrets.token_hex(32),
                "ttl": 86400,
                "createdAt": now_ts(),
                "initialPassword": pwd,
                "mustChange": True,
            }
        self.admin = admin
        if "initialPassword" in admin:
            self._save_admin()
        self._migrate()

    def _migrate(self):
        st = self.state
        st.setdefault("stats", {"checks": {}, "downloads": {}, "clients": {}})
        st["stats"].setdefault("checks", {})
        st["stats"].setdefault("downloads", {})
        st["stats"].setdefault("clients", {})
        st.setdefault("current", {"stable": None, "beta": None})
        st.setdefault("audit", [])
        st.setdefault("releases", [])

    def _load(self, name, default):
        p = os.path.join(self.dir, name)
        try:
            with open(p, "r", encoding="utf-8") as f:
                return json.load(f)
        except Exception:
            return default

    def _hash(self, raw):
        return hashlib.pbkdf2_hmac(
            "sha256", raw.encode("utf-8"),
            bytes.fromhex(self.admin["salt"]), int(self.admin["iterations"])
        ).hex()

    def check_password(self, raw):
        try:
            return hmac.compare_digest(self._hash(raw), self.admin["pwd"])
        except Exception:
            return False

    def set_password(self, raw):
        with self.lock:
            self.admin["pwd"] = self._hash(raw)
            self.admin.pop("initialPassword", None)
            self.admin["mustChange"] = False
            self.admin["secret"] = secrets.token_hex(32)  # 改密后踢掉旧会话
            self._save_admin()

    def _save_admin(self):
        p = os.path.join(self.dir, "admin.json")
        tmp = p + ".tmp"
        with open(tmp, "w", encoding="utf-8") as f:
            json.dump(self.admin, f, ensure_ascii=False, indent=2)
        os.replace(tmp, p)
        os.chmod(p, 0o600)

    def save(self):
        with self.lock:
            p = os.path.join(self.dir, "state.json")
            tmp = p + ".tmp"
            with open(tmp, "w", encoding="utf-8") as f:
                json.dump(self.state, f, ensure_ascii=False, indent=2)
            os.replace(tmp, p)

    # ---------------- 版本操作 ----------------

    def find(self, rid):
        for r in self.state["releases"]:
            if r["id"] == rid:
                return r
        return None

    def current(self, channel="stable"):
        rid = self.state["current"].get(channel)
        return self.find(rid) if rid else None

    def newest(self, channel="stable"):
        """该渠道下版本号最大的一条（用于校验递增）。"""
        pool = [r for r in self.state["releases"] if channel == "all" or r.get("channel") == channel]
        return max(pool, key=lambda r: (r.get("versionCode", 0), r.get("uploadedAt", 0))) if pool else None

    def add_release(self, rel):
        with self.lock:
            self.state["releases"].append(rel)
            self.save()
        return rel

    def audit(self, action, detail="", ip=""):
        with self.lock:
            self.state["audit"].insert(0, {
                "ts": now_ts(), "when": iso(now_ts()),
                "action": action, "detail": detail, "ip": ip,
            })
            del self.state["audit"][200:]
            self.save()

    def bump_check(self, version_code):
        with self.lock:
            d = day_key()
            ks = self.state["stats"]["checks"]
            ks[d] = ks.get(d, 0) + 1
            # 只保留 60 天
            if len(ks) > 60:
                for k in sorted(ks)[:-60]:
                    ks.pop(k, None)
            cl = self.state["stats"]["clients"]
            key = str(version_code or 0)
            cl[key] = cl.get(key, 0) + 1
            self.save()

    def bump_download(self, rel):
        with self.lock:
            rel["downloads"] = rel.get("downloads", 0) + 1
            st = self.state["stats"]["downloads"]
            d = day_key()
            st[d] = st.get(d, 0) + 1
            if len(st) > 60:
                for k in sorted(st)[:-60]:
                    st.pop(k, None)
            self.save()


# ---------------------------------------------------------------- 会话签名

TOKEN_RE = re.compile(r"^(\d+)\.([0-9a-f]+)\.([0-9a-f]+)$")


def issue_token(store):
    exp = now_ts() + int(store.admin.get("ttl", 86400))
    nonce = secrets.token_hex(8)
    payload = "%d.%s" % (exp, nonce)
    sig = hmac.new(store.admin["secret"].encode(), payload.encode(), hashlib.sha256).hexdigest()[:32]
    return payload + "." + sig


def verify_token(store, token):
    if not token:
        return False
    m = TOKEN_RE.match(token)
    if not m:
        return False
    exp, nonce, sig = m.group(1), m.group(2), m.group(3)
    payload = "%s.%s" % (exp, nonce)
    want = hmac.new(store.admin["secret"].encode(), payload.encode(), hashlib.sha256).hexdigest()[:32]
    if not hmac.compare_digest(want, sig):
        return False
    return int(exp) > now_ts()


# ---------------------------------------------------------------- 业务逻辑


class Ota(object):
    def __init__(self, store, cfg):
        self.store = store
        self.cfg = cfg
        self.login_fails = {}
        self.login_lock = threading.Lock()

    # ---- 公开数据 ----

    def public_release(self, rel):
        if not rel:
            return None
        base = self.cfg["public_base"].rstrip("/")
        return {
            "id": rel["id"],
            "versionCode": rel.get("versionCode", 0),
            "versionName": rel.get("versionName", ""),
            "size": rel.get("size", 0),
            "sizeText": human_size(rel.get("size", 0)),
            "sha256": rel.get("sha256", ""),
            "notes": rel.get("notes", ""),
            "force": bool(rel.get("force")),
            "minSupported": int(rel.get("minSupported") or 0),
            "channel": rel.get("channel", "stable"),
            "publishedAt": rel.get("publishedAt", 0),
            "publishedAtText": iso(rel.get("publishedAt", 0)),
            "downloads": rel.get("downloads", 0),
            "url": "%s%s/dl/%s" % (base, PREFIX, rel["fileName"]),
        }

    def check(self, vc, channel):
        channel = "beta" if channel == "beta" else "stable"
        rel = self.store.current(channel)
        if rel is None and channel == "beta":
            rel = self.store.current("stable")
            channel = "stable"
        latest = self.public_release(rel)
        if not latest:
            return {"ok": True, "update": False, "latest": None,
                    "message": "暂无可用版本"}
        forced = bool(latest["force"]) or (vc > 0 and vc < int(latest["minSupported"] or 0))
        update = latest["versionCode"] > vc
        resp = {
            "ok": True,
            "update": update,
            "force": bool(update and forced),
            "channel": channel,
            "latest": latest,
            "installedVersionCode": vc,
            "serverTime": iso(now_ts()),
        }
        if update:
            resp["message"] = "发现新版本 %s" % latest["versionName"]
        elif vc > latest["versionCode"]:
            resp["message"] = "当前为更高版本（内测包）"
        else:
            resp["message"] = "已是最新版本"
        return resp

    def overview(self):
        st = self.store.state
        cur = self.store.current("stable")
        beta = self.store.current("beta")
        releases = sorted(
            st["releases"], key=lambda r: (r.get("versionCode", 0), r.get("uploadedAt", 0)),
            reverse=True,
        )
        days = []
        for i in range(6, -1, -1):
            d = (datetime.now(CST) - timedelta(days=i)).strftime("%Y-%m-%d")
            days.append({"day": d[5:], "checks": st["stats"]["checks"].get(d, 0),
                         "downloads": st["stats"]["downloads"].get(d, 0)})
        total_dl = sum(r.get("downloads", 0) for r in st["releases"])
        total_check = sum(st["stats"]["checks"].values())
        return {
            "ok": True,
            "site": self.cfg["site_name"],
            "now": iso(now_ts()),
            "current": self.public_release(cur),
            "beta": self.public_release(beta),
            "releases": [dict(self.public_release(r),
                              uploadedAtText=iso(r.get("uploadedAt", 0)),
                              package=r.get("package", ""),
                              signerSha256=r.get("signerSha256"),
                              signerSource=r.get("signerSource", ""),
                              isCurrent=(cur and r["id"] == cur["id"]),
                              isBeta=(beta and r["id"] == beta["id"])) for r in releases],
            "stats": {"totalDownloads": total_dl, "totalChecks": total_check,
                      "days": days, "clients": st["stats"]["clients"]},
            "audit": st["audit"][:30],
            "limits": {"maxUploadMB": int(self.cfg["max_upload"] // (1024 * 1024))},
            "mustChange": bool(self.store.admin.get("mustChange")),
        }

    # ---- 发布 ----

    def publish(self, rid, channel="stable", sync=True):
        st = self.store.state
        rel = self.store.find(rid)
        if not rel:
            raise ValueError("版本不存在")
        channel = "beta" if channel == "beta" else "stable"
        cur = self.store.current(channel)
        # 签名一致性：换签的包在老用户机上无法覆盖安装
        if cur and rel.get("signerSha256") and cur.get("signerSha256") \
                and rel["signerSha256"] != cur["signerSha256"]:
            raise ValueError(
                "签名不一致：当前线上版本证书指纹 %s，本包为 %s。"
                "换签后老用户无法覆盖安装，请用同一签名密钥重新打包。"
                % (cur["signerSha256"][:23], rel["signerSha256"][:23])
            )
        with self.store.lock:
            rel["publishedAt"] = now_ts()
            rel["channel"] = channel
            st["current"][channel] = rid
            if channel == "stable":
                # 稳定版发布后，下载页指向的安装包同步更新
                pass
            self.store.save()
        synced = None
        if sync and channel == "stable":
            synced = self.sync_download_page(rel)
        return rel, synced

    def sync_download_page(self, rel):
        """把线上版本同步到 /var/www/download/：安装包 + version.json。"""
        info = {"synced": [], "errors": []}
        target_dir = self.cfg.get("download_dir")
        if not target_dir or not os.path.isdir(target_dir):
            info["errors"].append("下载目录不可用：%s" % target_dir)
            return info
        src = os.path.join(self.store.releases_dir, rel["fileName"])
        dst = os.path.join(target_dir, "shine-xumis.apk")
        try:
            shutil.copyfile(src, dst + ".tmp")
            os.replace(dst + ".tmp", dst)
            os.chmod(dst, 0o644)
            info["synced"].append(dst)
        except Exception as exc:
            info["errors"].append("复制安装包失败：%s" % exc)
        try:
            vj = {
                "versionCode": rel.get("versionCode"), "versionName": rel.get("versionName"),
                "size": rel.get("size"), "sizeText": human_size(rel.get("size", 0)),
                "sha256": rel.get("sha256"), "publishedAt": iso(rel.get("publishedAt")),
                "notes": rel.get("notes", ""),
                "apk": "%s%s/dl/%s" % (self.cfg["public_base"].rstrip("/"), PREFIX, rel["fileName"]),
            }
            p = os.path.join(target_dir, "version.json")
            with open(p + ".tmp", "w", encoding="utf-8") as f:
                json.dump(vj, f, ensure_ascii=False, indent=2)
            os.replace(p + ".tmp", p)
            os.chmod(p, 0o644)
            info["synced"].append(p)
        except Exception as exc:
            info["errors"].append("写 version.json 失败：%s" % exc)
        return info


# ---------------------------------------------------------------- HTTP 处理


class Handler(BaseHTTPRequestHandler):
    server_version = "ShineOTA/" + VERSION
    protocol_version = "HTTP/1.1"
    store = None
    ota = None
    cfg = None

    # 关闭默认的访问日志（写自己的精简日志）
    def log_message(self, fmt, *args):
        sys.stderr.write("[%s] %s %s\n" % (iso(now_ts()), self.client_ip(), fmt % args))

    def client_ip(self):
        xff = self.headers.get("X-Forwarded-For", "")
        if xff:
            return xff.split(",")[0].strip()
        return self.client_address[0]

    # ---------- 基础响应 ----------

    def _cors(self):
        self.send_header("X-Content-Type-Options", "nosniff")
        self.send_header("Referrer-Policy", "same-origin")

    def send_json(self, obj, code=200, extra=None):
        body = json.dumps(obj, ensure_ascii=False).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        self._cors()
        for k, v in (extra or {}).items():
            self.send_header(k, v)
        self.end_headers()
        if self.command != "HEAD":
            self.wfile.write(body)

    def send_text(self, text, code=200, ctype="text/plain; charset=utf-8", extra=None):
        body = text.encode("utf-8") if isinstance(text, str) else text
        self.send_response(code)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(body)))
        self._cors()
        for k, v in (extra or {}).items():
            self.send_header(k, v)
        self.end_headers()
        if self.command != "HEAD":
            self.wfile.write(body)

    def send_file(self, path, ctype, download_name=None, count_rel=None, cache="no-cache"):
        try:
            size = os.path.getsize(path)
        except OSError:
            self.send_json({"ok": False, "error": "文件不存在"}, 404)
            return
        start, end = 0, size - 1
        code = 200
        rng = self.headers.get("Range")
        if rng:
            m = re.match(r"bytes=(\d*)-(\d*)", rng.strip())
            if m:
                if m.group(1):
                    start = int(m.group(1))
                    end = int(m.group(2)) if m.group(2) else size - 1
                elif m.group(2):
                    start = max(0, size - int(m.group(2)))
                if start >= size:
                    self.send_response(416)
                    self.send_header("Content-Range", "bytes */%d" % size)
                    self.send_header("Content-Length", "0")
                    self.end_headers()
                    return
                end = min(end, size - 1)
                code = 206

        length = end - start + 1
        self.send_response(code)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(length))
        self.send_header("Accept-Ranges", "bytes")
        self.send_header("Content-Disposition", 'attachment; filename="%s"' % (download_name or os.path.basename(path)))
        self.send_header("Cache-Control", cache)
        if code == 206:
            self.send_header("Content-Range", "bytes %d-%d/%d" % (start, end, size))
        self._cors()
        self.end_headers()
        if self.command == "HEAD":
            return
        try:
            with open(path, "rb") as f:
                f.seek(start)
                left = length
                while left > 0:
                    chunk = f.read(min(256 * 1024, left))
                    if not chunk:
                        break
                    self.wfile.write(chunk)
                    left -= len(chunk)
        except (BrokenPipeError, ConnectionResetError):
            return
        if count_rel is not None and start == 0:
            self.store.bump_download(count_rel)

    # ---------- 鉴权 ----------

    def session_ok(self):
        cookie = self.headers.get("Cookie") or ""
        m = re.search(r"ota_session=([^;]+)", cookie)
        return bool(m and verify_token(self.store, m.group(1)))

    def need_auth(self):
        if self.session_ok():
            return True
        self.send_json({"ok": False, "error": "未登录或会话已过期", "needLogin": True}, 401)
        return False

    def check_csrf(self):
        if self.headers.get("X-OTA-Request") != "1":
            self.send_json({"ok": False, "error": "缺少请求标识"}, 403)
            return False
        return True

    def rate_limited(self):
        ip = self.client_ip()
        now = now_ts()
        with self.ota.login_lock:
            arr = [t for t in self.ota.login_fails.get(ip, []) if now - t < 600]
            self.ota.login_fails[ip] = arr
            if len(arr) >= 6:
                self.send_json({"ok": False, "error": "尝试次数过多，请 10 分钟后再试"}, 429)
                return True
        return False

    def note_fail(self):
        ip = self.client_ip()
        with self.ota.login_lock:
            self.ota.login_fails.setdefault(ip, []).append(now_ts())

    # ---------- 路由 ----------

    def do_HEAD(self):
        self.do_GET()

    def do_OPTIONS(self):
        self.send_response(204)
        self.send_header("Allow", "GET, HEAD, POST, PATCH, DELETE, OPTIONS")
        self.send_header("Content-Length", "0")
        self.end_headers()

    def do_GET(self):
        u = urlparse(self.path)
        path = unquote(u.path)
        q = parse_qs(u.query)

        if path in ("/healthz", PREFIX + "/healthz"):
            return self.send_json({"ok": True, "version": VERSION, "ts": iso(now_ts())})

        if path == PREFIX:
            self.send_response(302)
            self.send_header("Location", PREFIX + "/")
            self.send_header("Content-Length", "0")
            self.end_headers()
            return

        if path in (PREFIX + "/", PREFIX + "/index.html"):
            return self.serve_admin_page()

        if path == PREFIX + "/api/check":
            try:
                vc = int(q.get("vc", ["0"])[0] or 0)
            except ValueError:
                vc = 0
            ch = (q.get("ch", ["stable"])[0] or "stable").lower()
            self.store.bump_check(vc)
            resp = self.ota.check(vc, ch)
            resp["sizeText"] = (resp.get("latest") or {}).get("sizeText", "")
            return self.send_json(resp, 200, {"Cache-Control": "no-store"})

        if path in (PREFIX + "/api/latest.json", PREFIX + "/latest.json"):
            cur = self.ota.public_release(self.store.current("stable"))
            return self.send_json(cur or {"ok": True, "latest": None})

        if path == PREFIX + "/api/versions":
            rels = sorted(self.store.state["releases"], key=lambda r: r.get("versionCode", 0), reverse=True)
            out = [{"versionName": r.get("versionName"), "versionCode": r.get("versionCode"),
                    "notes": r.get("notes", ""), "publishedAtText": iso(r.get("publishedAt", 0)),
                    "sizeText": human_size(r.get("size", 0))} for r in rels]
            return self.send_json({"ok": True, "versions": out})

        if path.startswith(PREFIX + "/dl/"):
            name = safe_name(os.path.basename(path))
            rel = next((r for r in self.store.state["releases"] if r["fileName"] == name), None)
            fpath = os.path.join(self.store.releases_dir, name)
            if not rel or not os.path.isfile(fpath):
                return self.send_json({"ok": False, "error": "安装包不存在"}, 404)
            return self.send_file(fpath, "application/vnd.android.package-archive",
                                  "shine-xumis-%s.apk" % (rel.get("versionName") or "app"),
                                  count_rel=rel)

        if path == PREFIX + "/api/me":
            if not self.session_ok():
                return self.send_json({"ok": True, "logged": False})
            return self.send_json({"ok": True, "logged": True})

        if path == PREFIX + "/api/overview":
            if not self.need_auth():
                return
            return self.send_json(self.ota.overview())

        return self.send_json({"ok": False, "error": "接口不存在"}, 404)

    def serve_admin_page(self):
        p = os.path.join(self.cfg["web_dir"], "admin.html")
        try:
            with open(p, "rb") as f:
                body = f.read()
        except OSError:
            return self.send_text("管理界面缺失：%s" % p, 500)
        self.send_response(200)
        self.send_header("Content-Type", "text/html; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        self.send_header("X-Frame-Options", "DENY")
        self.send_header("X-Robots-Tag", "noindex, nofollow, noarchive")
        self.send_header("Content-Security-Policy",
                         "default-src 'self'; img-src 'self' data:; style-src 'self' 'unsafe-inline'; "
                         "script-src 'self' 'unsafe-inline'; connect-src 'self'; frame-ancestors 'none'")
        self._cors()
        self.end_headers()
        if self.command != "HEAD":
            self.wfile.write(body)

    def do_POST(self):
        u = urlparse(self.path)
        path = unquote(u.path)

        if path == PREFIX + "/api/login":
            return self.handle_login()
        if not self.need_auth() or not self.check_csrf():
            return

        if path == PREFIX + "/api/logout":
            self.ota.store.audit("退出登录", "", self.client_ip())
            return self.send_json({"ok": True}, 200,
                                  {"Set-Cookie": "ota_session=; Path=/; Max-Age=0; HttpOnly; Secure; SameSite=Strict"})

        if path == PREFIX + "/api/upload":
            return self.handle_upload()

        if path == PREFIX + "/api/publish":
            body = self.read_json()
            rid = body.get("id")
            ch = body.get("channel", "stable")
            try:
                rel, synced = self.ota.publish(rid, ch)
            except ValueError as exc:
                return self.send_json({"ok": False, "error": str(exc)}, 400)
            self.store.audit("推送上线", "v%s → %s 渠道" % (rel.get("versionName"), ch), self.client_ip())
            return self.send_json({"ok": True, "release": self.ota.public_release(rel), "synced": synced})

        if path == PREFIX + "/api/password":
            body = self.read_json()
            old, new = body.get("old", ""), body.get("new", "")
            if not self.store.check_password(old):
                return self.send_json({"ok": False, "error": "原密码不正确"}, 400)
            if len(new) < 8:
                return self.send_json({"ok": False, "error": "新密码至少 8 位"}, 400)
            self.store.set_password(new)
            self.store.audit("修改口令", "", self.client_ip())
            return self.send_json({"ok": True, "message": "已更新，请重新登录"}, 200,
                                  {"Set-Cookie": "ota_session=; Path=/; Max-Age=0; HttpOnly; Secure; SameSite=Strict"})

        return self.send_json({"ok": False, "error": "接口不存在"}, 404)

    def do_PATCH(self):
        u = urlparse(self.path)
        path = unquote(u.path)
        if not self.need_auth() or not self.check_csrf():
            return
        m = re.match(r"^%s/api/release/([A-Za-z0-9]+)$" % re.escape(PREFIX), path)
        if not m:
            return self.send_json({"ok": False, "error": "接口不存在"}, 404)
        rel = self.store.find(m.group(1))
        if not rel:
            return self.send_json({"ok": False, "error": "版本不存在"}, 404)
        body = self.read_json()
        changes = []
        for k in ("notes", "force", "channel"):
            if k in body:
                if k == "force":
                    rel["force"] = bool(body[k])
                    changes.append("强制更新=%s" % ("开" if rel["force"] else "关"))
                elif k == "channel":
                    rel["channel"] = "beta" if body[k] == "beta" else "stable"
                    changes.append("渠道=%s" % rel["channel"])
                else:
                    rel["notes"] = str(body[k])[:4000]
                    changes.append("更新说明")
        if "minSupported" in body:
            try:
                rel["minSupported"] = max(0, int(body["minSupported"] or 0))
                changes.append("最低支持版本=%d" % rel["minSupported"])
            except Exception:
                pass
        self.store.save()
        self.store.audit("修改版本", "v%s · %s" % (rel.get("versionName"), "、".join(changes)), self.client_ip())
        if rel["id"] == (self.store.state["current"].get("stable") or ""):
            self.ota.sync_download_page(rel)
        return self.send_json({"ok": True, "release": self.ota.public_release(rel)})

    def do_DELETE(self):
        u = urlparse(self.path)
        path = unquote(u.path)
        if not self.need_auth() or not self.check_csrf():
            return
        m = re.match(r"^%s/api/release/([A-Za-z0-9]+)$" % re.escape(PREFIX), path)
        if not m:
            return self.send_json({"ok": False, "error": "接口不存在"}, 404)
        rel = self.store.find(m.group(1))
        if not rel:
            return self.send_json({"ok": False, "error": "版本不存在"}, 404)
        for ch, rid in list(self.store.state["current"].items()):
            if rid == rel["id"]:
                return self.send_json({"ok": False, "error": "该版本正在线上，请先推送其它版本再删除"}, 400)
        with self.store.lock:
            self.store.state["releases"] = [r for r in self.store.state["releases"] if r["id"] != rel["id"]]
            self.store.save()
        try:
            os.remove(os.path.join(self.store.releases_dir, rel["fileName"]))
        except OSError:
            pass
        self.store.audit("删除版本", "v%s" % rel.get("versionName"), self.client_ip())
        return self.send_json({"ok": True})

    # ---------- 具体处理 ----------

    def read_json(self):
        try:
            n = int(self.headers.get("Content-Length") or 0)
            raw = self.rfile.read(n) if n else b""
            return json.loads(raw.decode("utf-8") or "{}")
        except Exception:
            return {}

    def handle_login(self):
        if self.rate_limited():
            return
        body = self.read_json()
        pwd = body.get("password", "")
        if not self.store.check_password(pwd):
            self.note_fail()
            self.store.audit("登录失败", "口令错误", self.client_ip())
            time.sleep(0.4)
            return self.send_json({"ok": False, "error": "口令不正确"}, 401)
        token = issue_token(self.store)
        ttl = int(self.store.admin.get("ttl", 86400))
        self.store.audit("登录成功", "", self.client_ip())
        return self.send_json({"ok": True}, 200, {
            "Set-Cookie": "ota_session=%s; Path=/; Max-Age=%d; HttpOnly; Secure; SameSite=Strict" % (token, ttl)
        })

    def handle_upload(self):
        ctype = self.headers.get("Content-Type", "")
        n = int(self.headers.get("Content-Length") or 0)
        if n <= 0:
            return self.send_json({"ok": False, "error": "空请求"}, 400)
        if n > self.cfg["max_upload"]:
            return self.send_json({"ok": False, "error": "文件超过 %d MB 上限" % (self.cfg["max_upload"] // 1048576)}, 413)
        raw = self.rfile.read(n)
        fields, files = parse_multipart(raw, ctype)
        item = files.get("apk")
        if not item:
            return self.send_json({"ok": False, "error": "未收到安装包"}, 400)
        filename, blob = item
        if not blob:
            return self.send_json({"ok": False, "error": "安装包为空"}, 400)

        tmp = os.path.join(self.store.tmp_dir, "up-%s.apk" % secrets.token_hex(6))
        with open(tmp, "wb") as f:
            f.write(blob)
        try:
            meta = read_apk(tmp)
        except Exception as exc:
            os.remove(tmp)
            return self.send_json({"ok": False, "error": "解析安装包失败：%s" % exc}, 400)

        if meta.get("package") and meta["package"] != self.cfg["package"]:
            os.remove(tmp)
            return self.send_json({
                "ok": False,
                "error": "包名不匹配：本包为 %s，应为 %s" % (meta.get("package"), self.cfg["package"])
            }, 400)
        if not meta.get("versionCode"):
            os.remove(tmp)
            return self.send_json({"ok": False, "error": "无法读取 versionCode，请确认是完整的 APK"}, 400)

        # 同版本号已存在则替换文件，保留历史条目
        exists = next((r for r in self.store.state["releases"]
                       if r.get("versionCode") == meta["versionCode"]), None)
        rid = exists["id"] if exists else secrets.token_hex(5)
        fname = "shine-%s-%d-%s.apk" % (safe_name(meta.get("versionName") or "app"),
                                        meta["versionCode"], rid)
        final = os.path.join(self.store.releases_dir, fname)
        os.replace(tmp, final)
        os.chmod(final, 0o644)

        rel = {
            "id": rid,
            "versionCode": meta["versionCode"],
            "versionName": meta.get("versionName") or str(meta["versionCode"]),
            "package": meta.get("package", ""),
            "fileName": fname,
            "size": meta["size"],
            "sha256": meta["sha256"],
            "signerSha256": meta.get("signerSha256"),
            "signerSource": meta.get("signerSource", ""),
            "notes": (fields.get("notes") or "").strip()[:4000],
            "channel": "beta" if (fields.get("channel") or "stable") == "beta" else "stable",
            "force": (fields.get("force") or "").lower() in ("1", "true", "on"),
            "minSupported": int(fields.get("minSupported") or 0) if str(fields.get("minSupported") or "0").isdigit() else 0,
            "uploadedAt": now_ts(),
            "publishedAt": exists.get("publishedAt", 0) if exists else 0,
            "downloads": exists.get("downloads", 0) if exists else 0,
            "originName": filename,
        }
        if exists:
            with self.store.lock:
                idx = self.store.state["releases"].index(exists)
                self.store.state["releases"][idx] = rel
                self.store.save()
        else:
            self.store.add_release(rel)

        self.store.audit("上传安装包", "v%s(%d) · %s" % (rel["versionName"], rel["versionCode"], human_size(rel["size"])),
                         self.client_ip())

        result = {"ok": True,
                  "meta": {"package": rel["package"], "signerSha256": rel["signerSha256"],
                           "signerSource": rel["signerSource"], "sha256": rel["sha256"]}}

        if (fields.get("publish") or "").lower() in ("1", "true", "on"):
            try:
                rel2, synced = self.ota.publish(rel["id"], rel["channel"])
            except ValueError as exc:
                result["published"] = False
                result["error"] = str(exc)
                result["release"] = self.ota.public_release(rel)
                return self.send_json(result, 200)
            result["published"] = True
            result["synced"] = synced
            self.store.audit("推送上线", "v%s → %s 渠道" % (rel["versionName"], rel["channel"]), self.client_ip())
        result["release"] = self.ota.public_release(rel)
        return self.send_json(result)


def parse_multipart(body, content_type):
    m = re.search(r'boundary="?([^";]+)"?', content_type or "")
    if not m:
        return {}, {}
    delim = ("--" + m.group(1)).encode()
    fields, files = {}, {}
    for part in body.split(delim)[1:]:
        if part.startswith(b"--"):
            break
        part = part.lstrip(b"\r\n")
        if not part or b"\r\n\r\n" not in part:
            continue
        head, data = part.split(b"\r\n\r\n", 1)
        if data.endswith(b"\r\n"):
            data = data[:-2]
        head_txt = head.decode("utf-8", "replace")
        nm = re.search(r'name="([^"]*)"', head_txt)
        fn = re.search(r'filename="([^"]*)"', head_txt)
        if not nm:
            continue
        if fn is not None and fn.group(1):
            files[nm.group(1)] = (fn.group(1), data)
        else:
            fields[nm.group(1)] = data.decode("utf-8", "replace")
    return fields, files


# ---------------------------------------------------------------------- main


def main():
    ap = argparse.ArgumentParser(description="shine 星心 APK 推送服务")
    ap.add_argument("--data", default=os.environ.get("OTA_DATA", "/var/lib/shine-ota"))
    ap.add_argument("--web", default=os.environ.get("OTA_WEB", "/opt/shine-ota/web"))
    ap.add_argument("--host", default=os.environ.get("OTA_HOST", "127.0.0.1"))
    ap.add_argument("--port", type=int, default=int(os.environ.get("OTA_PORT", "8095")))
    ap.add_argument("--public-base", default=os.environ.get("OTA_PUBLIC_BASE", "https://shine.xumois.cn"))
    ap.add_argument("--download-dir", default=os.environ.get("OTA_DOWNLOAD_DIR", "/var/www/download"))
    ap.add_argument("--package", default=os.environ.get("OTA_PACKAGE", "com.shine.xumis.app"))
    ap.add_argument("--site-name", default=os.environ.get("OTA_SITE_NAME", "shine 星心"))
    ap.add_argument("--max-upload-mb", type=int, default=int(os.environ.get("OTA_MAX_UPLOAD_MB", "120")))
    args = ap.parse_args()

    store = Store(args.data)
    cfg = {
        "public_base": args.public_base,
        "download_dir": args.download_dir,
        "package": args.package,
        "site_name": args.site_name,
        "max_upload": args.max_upload_mb * 1024 * 1024,
        "web_dir": args.web,
    }
    ota = Ota(store, cfg)

    Handler.store = store
    Handler.ota = ota
    Handler.cfg = cfg

    httpd = ThreadingHTTPServer((args.host, args.port), Handler)
    httpd.daemon_threads = True

    sys.stderr.write(
        "[%s] shine-ota %s 启动： http://%s:%d%s/  数据 %s\n"
        % (iso(now_ts()), VERSION, args.host, args.port, PREFIX, args.data)
    )
    if store.admin.get("initialPassword"):
        sys.stderr.write(
            "[%s] 初始管理口令：%s （文件 %s/admin.json，登录后请立即修改）\n"
            % (iso(now_ts()), store.admin["initialPassword"], args.data)
        )
    try:
        httpd.serve_forever()
    except KeyboardInterrupt:
        sys.stderr.write("bye\n")


if __name__ == "__main__":
    main()
