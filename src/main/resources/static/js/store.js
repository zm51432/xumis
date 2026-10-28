// 语言切换：读取当前 ?lang= 并切换中/英，保持当前路径
function toggleLang() {
  const url = new URL(window.location.href);
  const current = url.searchParams.get('lang');
  const next = (current === 'en') ? 'zh' : 'en';
  if (next === 'zh') {
    url.searchParams.delete('lang');
  } else {
    url.searchParams.set('lang', 'en');
  }
  window.location.href = url.pathname + url.search;
}

// 币种切换：写入 session 后跳回当前页
function setCurrency(code) {
  const back = window.location.pathname + window.location.search;
  window.location.href = '/set-currency?c=' + encodeURIComponent(code) + '&redirect=' + encodeURIComponent(back);
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

// 滚动渐入动画
document.addEventListener('DOMContentLoaded', () => {
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
