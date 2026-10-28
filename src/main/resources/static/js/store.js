// 语言切换：显式写入 Cookie 语言后跳回当前页（保持路径与其它查询参数）
// 说明：不能靠"删除 ?lang= 参数"切回中文——那样 LocaleChangeInterceptor 不会触发，
// Cookie 里仍是英文，所以中文永远切不回来。
function setLang(l) {
  const url = new URL(window.location.href);
  url.searchParams.delete('lang');            // 清掉旧参数，避免残留覆盖
  const back = url.pathname + (url.search || '');
  window.location.href = '/set-lang?l=' + encodeURIComponent(l)
    + '&redirect=' + encodeURIComponent(back);
}

// 按钮：按当前页面语言切换到另一种（跟随 <html lang>，而不是 URL 参数）
function toggleLang(btn) {
  const cur = (btn && btn.dataset && btn.dataset.lang)
    || document.documentElement.lang || 'zh';
  setLang(String(cur).toLowerCase().startsWith('en') ? 'zh' : 'en');
}

// 币种切换：写入 session 后跳回当前页
function setCurrency(code) {
  const back = window.location.pathname + window.location.search;
  window.location.href = '/set-currency?c=' + encodeURIComponent(code) + '&redirect=' + encodeURIComponent(back);
}

// 复制文本（订单号等），带提示
function copyText(text) {
  const done = () => showToast(document.documentElement.lang === 'en' ? 'Copied' : '已复制');
  if (navigator.clipboard && window.isSecureContext) {
    navigator.clipboard.writeText(text).then(done).catch(fallbackCopy);
  } else {
    fallbackCopy();
  }
  function fallbackCopy() {
    const ta = document.createElement('textarea');
    ta.value = text; ta.style.position = 'fixed'; ta.style.opacity = '0';
    document.body.appendChild(ta); ta.select();
    try { document.execCommand('copy'); done(); } catch (e) { showToast(text); }
    ta.remove();
  }
}

// 商品图片加载失败时的兜底（避免破图/空白影响观感）
function imgFallback(el) {
  if (!el || el.dataset.fallback === '1') return;
  el.dataset.fallback = '1';
  el.src = '/img/placeholder.svg';
}

// 轻提示 Toast：加购、操作反馈统一走这里，不打断浏览
function showToast(msg, type) {
  let box = document.getElementById('toastBox');
  if (!box) {
    box = document.createElement('div');
    box.id = 'toastBox';
    box.className = 'toast-box';
    document.body.appendChild(box);
  }
  const t = document.createElement('div');
  t.className = 'toast' + (type === 'error' ? ' toast-error' : '');
  t.textContent = msg;
  box.appendChild(t);
  requestAnimationFrame(() => t.classList.add('show'));
  setTimeout(() => {
    t.classList.remove('show');
    setTimeout(() => t.remove(), 300);
  }, 2200);
}

// 加入购物车：无刷新提交，成功后更新角标并弹提示
function addToCart(productId, qty) {
  const q = (qty && qty > 0) ? qty : 1;
  const body = new URLSearchParams();
  body.set('productId', String(productId));
  body.set('quantity', String(q));
  fetch('/cart/add-json', {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded', 'Accept': 'application/json', 'X-CSRF-Token': csrfToken() },
    body: body
  }).then(r => r.json())
    .then(d => {
      if (d && d.ok) {
        updateCartBadge(d.count);
        const txt = document.documentElement.lang === 'en' ? 'Added to cart' : '已加入购物车';
        showToast(txt);
      } else {
        showToast((d && d.message) || '加入购物车失败', 'error');
      }
    })
    .catch(() => showToast('网络异常，请重试', 'error'));
}

// 更新导航购物车角标（带一次弹跳动画）
function updateCartBadge(count) {
  document.querySelectorAll('.cart-link .badge').forEach(b => {
    b.textContent = count;
    b.classList.remove('pop');
    void b.offsetWidth;
    b.classList.add('pop');
  });
}

// 数量步进器
function stepQty(delta) {
  const el = document.getElementById('qtyInput');
  if (!el) return;
  const max = parseInt(el.getAttribute('max') || '99', 10);
  let v = parseInt(el.value || '1', 10) + delta;
  v = Math.max(1, Math.min(isNaN(max) ? 99 : max, v));
  el.value = v;
}

// 商品管理：预览上传的图片
function previewProductImage(input) {
  const preview = document.getElementById('imgPreview');
  if (preview && input.files && input.files[0]) {
    const reader = new FileReader();
    reader.onload = e => { preview.src = e.target.result; };
    reader.readAsDataURL(input.files[0]);
  }
}

// 商品列表排序：保持分类与搜索条件
function buildSortUrl(sort) {
  const url = new URL(window.location.href);
  url.searchParams.set('sort', sort);
  url.searchParams.set('page', '0');
  return url.pathname + url.search;
}

// 自定义想法页面：预览上传图片
function previewImage(input) {
  const preview = document.getElementById('preview');
  if (input.files && input.files[0]) {
    const reader = new FileReader();
    reader.onload = e => {
      preview.src = e.target.result;
      preview.style.display = 'block';
    };
    reader.readAsDataURL(input.files[0]);
  }
}

// 手机端汉堡菜单开合
function toggleMenu() {
  const links = document.getElementById('navLinks');
  const btn = document.querySelector('.hamburger');
  if (links) links.classList.toggle('open');
  if (btn) btn.classList.toggle('open');
  // 点击菜单项后自动收起
  if (links && links.classList.contains('open')) {
    links.querySelectorAll('a').forEach(a => a.addEventListener('click', () => {
      links.classList.remove('open');
      if (btn) btn.classList.remove('open');
    }, { once: true }));
  }
}

// ===== CSRF =====
// 令牌由服务端放在 <meta name="_csrf">，这里给所有 POST 表单自动补隐藏域，
// 这样每个表单不用手写，也不会漏掉导致 403。
function csrfToken() {
  const m = document.querySelector('meta[name="_csrf"]');
  return m ? m.content : '';
}
function injectCsrf() {
  const t = csrfToken();
  if (!t) return;
  document.querySelectorAll('form').forEach(f => {
    const m = (f.getAttribute('method') || 'get').toLowerCase();
    if (m !== 'post') return;
    if (f.querySelector('input[name="_csrf"]')) return;
    const i = document.createElement('input');
    i.type = 'hidden'; i.name = '_csrf'; i.value = t;
    f.appendChild(i);
  });
}

// 滚动渐入动画
document.addEventListener('DOMContentLoaded', () => {
  injectCsrf();
  const items = document.querySelectorAll('.reveal');
  if (!('IntersectionObserver' in window) || items.length === 0) {
    items.forEach(el => el.classList.add('show'));
  } else {
    const io = new IntersectionObserver((entries) => {
      entries.forEach(e => {
        if (e.isIntersecting) { e.target.classList.add('show'); io.unobserve(e.target); }
      });
    }, { threshold: 0.12 });
    items.forEach(el => io.observe(el));
  }
  initCookieConsent();
});

// Cookie 同意横幅（跨境合规）：未同意则展示，同意后写入本地 Cookie
function initCookieConsent() {
  if (localStorage.getItem('shine_cookie_ok') === '1') return;
  const banner = document.createElement('div');
  banner.className = 'cookie-banner';
  banner.innerHTML =
    '<span class="cookie-text">' + (document.documentElement.lang === 'en'
      ? 'We use cookies to improve your experience. By continuing you agree.'
      : '我们使用 Cookie 提升浏览体验，继续浏览即表示同意。') + '</span>' +
    '<button class="cookie-ok">' + (document.documentElement.lang === 'en' ? 'Got it' : '知道了') + '</button>';
  document.body.appendChild(banner);
  banner.querySelector('.cookie-ok').addEventListener('click', () => {
    localStorage.setItem('shine_cookie_ok', '1');
    banner.remove();
  });
}
