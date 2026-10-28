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
