package com.shine.xumis.app.util

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.content.FileProvider
import java.io.File

/**
 * WebView 统一配置。
 *
 * App 里「购物车 / 我的账户 / 来图定制 / 下单」这些需要完整交易流程的页面走站点 WebView，
 * 因此必须把 JS、DOM Storage、Cookie（含第三方）、混合内容以及**文件上传**都打开，
 * 否则站点上的图片上传（来图定制）和登录态会失效。
 *
 * 与系统权限相关的能力（地理位置、拍照上传、摄像头）也在这里统一收口，
 * 调用方只需要处理「还没授权时去申请」这一件事。
 */
object WebViewSetup {

    @SuppressLint("SetJavaScriptEnabled")
    fun apply(web: WebView) {
        AppPrefs.init(web.context)
        web.settings.apply {
            // 「设置 → 网页字号」通过 textZoom 生效，站内页面无需改 CSS
            textZoom = AppPrefs.textZoomPercent()
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            loadsImagesAutomatically = true
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            cacheMode = WebSettings.LOAD_DEFAULT
            useWideViewPort = true
            loadWithOverviewMode = true
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            allowFileAccess = true
            allowContentAccess = true
            // 站点地图/运费估算用到的 navigator.geolocation。
            // 开关打开只是允许 JS 发起请求，真正是否放行由下面的 onGeolocationPermissionsShowPrompt 决定。
            setGeolocationEnabled(true)
        }
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true)
        CookieManager.getInstance().flush()
    }

    /**
     * 站内 http(s) 留在 WebView，其余协议（tel/mailto/支付宝唤端等）交给系统处理。
     *
     * onOffline：主文档加载失败时回调 (失败的地址, 原因)，由调用方切到内置离线页；
     * 不传则维持原来的纯 WebView 行为。离线页本身是 file:// 加载，不会再次触发回调。
     */
    fun client(
        context: Context,
        onOffline: ((String, String) -> Unit)? = null
    ): WebViewClient = object : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val uri = request.url
            val scheme = uri.scheme?.lowercase() ?: return false
            if (scheme == "http" || scheme == "https") return false
            return try {
                val intent = Intent(Intent.ACTION_VIEW, uri)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                true
            } catch (e: Exception) {
                false
            }
        }

        override fun onReceivedError(
            view: WebView?,
            request: WebResourceRequest?,
            error: WebResourceError?
        ) {
            super.onReceivedError(view, request, error)
            if (request == null || !request.isForMainFrame) return
            val url = request.url?.toString() ?: return
            if (url.startsWith("file:")) return
            val reason = if (error?.errorCode == ERROR_TIMEOUT) "timeout" else "offline"
            onOffline?.invoke(url, reason)
        }

        override fun onReceivedHttpError(
            view: WebView?,
            request: WebResourceRequest?,
            errorResponse: WebResourceResponse?
        ) {
            super.onReceivedHttpError(view, request, errorResponse)
            if (request == null || !request.isForMainFrame) return
            val code = errorResponse?.statusCode ?: return
            if (code < 500) return
            val url = request.url?.toString() ?: return
            if (url.startsWith("file:")) return
            onOffline?.invoke(url, "http")
        }
    }

    // ---------------- 定位授权 ----------------

    /**
     * 站点请求地理定位时的判定。
     * @return true 表示已经直接应答（允许或明确拒绝）；false 表示要先向系统申请权限，
     *         调用方申请完再自己回调 callback.invoke(origin, granted, false)。
     */
    fun geoAllowDirectly(
        ctx: Context,
        origin: String,
        callback: GeolocationPermissions.Callback
    ): Boolean = when (Permissions.status(ctx, Permissions.Group.LOCATION)) {
        Permissions.Status.GRANTED, Permissions.Status.PARTIAL -> {
            callback.invoke(origin, true, false)
            true
        }

        Permissions.Status.UNAVAILABLE -> {
            callback.invoke(origin, false, false)
            true
        }

        Permissions.Status.DENIED -> false
    }

    /**
     * 网页调用摄像头（如扫溯源码）。
     * 只放行视频采集，麦克风一律拒绝——本应用不需要录音权限。
     */
    fun handlePermissionRequest(ctx: Context, request: PermissionRequest?) {
        val r = request ?: return
        val video = r.resources.filter { it == PermissionRequest.RESOURCE_VIDEO_CAPTURE }
        if (video.isEmpty() || !Permissions.isGranted(ctx, Permissions.Group.CAMERA)) {
            r.deny()
            return
        }
        runCatching { r.grant(video.toTypedArray()) }.onFailure { r.deny() }
    }

    // ---------------- 文件选择（相册 / 拍照） ----------------

    /** 拍照的临时输出文件，放在 cacheDir/captures 下，顺手清掉一天前的旧文件 */
    fun newCaptureFile(ctx: Context): File {
        val dir = File(ctx.cacheDir, "captures").apply { mkdirs() }
        val expire = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
        dir.listFiles()?.forEach { if (it.lastModified() < expire) it.delete() }
        return File(dir, "upload_${System.currentTimeMillis()}.jpg")
    }

    private fun captureUri(ctx: Context, file: File): Uri =
        FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", file)

    /**
     * 网页里点「上传图片」时弹出的选择器。
     *
     * 用 ACTION_CHOOSER 把「系统/厂商相册」与「相机」并列：
     * 小米、OPPO、vivo 各自的相册应用会以原生样式出现在列表里，
     * 比自绘一个「拍照 / 相册 / 文件」弹窗更符合这几家的使用习惯，也不会绕过它们的图片选择器。
     *
     * @param cameraOut 拍照输出文件；传 null 或本机无摄像头则只给相册
     */
    fun chooserIntent(
        ctx: Context,
        params: WebChromeClient.FileChooserParams?,
        cameraOut: File?
    ): Intent {
        val gallery = Intent(Intent.ACTION_GET_CONTENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        val accepts = params?.acceptTypes?.filter { it.isNotBlank() } ?: emptyList()
        val mimes = accepts.filter { it.contains("/") }
        if (mimes.isNotEmpty()) {
            gallery.type = mimes.first()
            gallery.putExtra(Intent.EXTRA_MIME_TYPES, mimes.toTypedArray())
        }
        if (params?.mode == WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE) {
            gallery.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }

        if (cameraOut == null || !Permissions.hardwareAvailable(ctx, Permissions.Group.CAMERA)) {
            return gallery
        }

        val uri = try {
            captureUri(ctx, cameraOut)
        } catch (e: Exception) {
            return gallery
        }
        val camera = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
            putExtra(MediaStore.EXTRA_OUTPUT, uri)
            // clipData + 两个 flag 一起给，才能让相机 App 拿到写权限（Android 7+ 必须）
            clipData = ClipData.newRawUri("capture", uri)
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent(Intent.ACTION_CHOOSER).apply {
            putExtra(Intent.EXTRA_INTENT, gallery)
            putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(camera))
            putExtra(Intent.EXTRA_TITLE, "选择图片来源")
        }
    }

    /**
     * 解析选择结果。
     * 拍照时系统通常只回 resultCode 不回 data，需要自己把刚拍的文件交回网页；
     * 用户取消则返回 null，网页上的上传按钮才能恢复可点。
     */
    fun chooserResult(
        ctx: Context,
        resultCode: Int,
        data: Intent?,
        cameraOut: File?
    ): Array<Uri>? {
        val fromData = WebChromeClient.FileChooserParams.parseResult(resultCode, data)
        if (!fromData.isNullOrEmpty()) return fromData
        if (resultCode == android.app.Activity.RESULT_OK &&
            cameraOut != null && cameraOut.exists() && cameraOut.length() > 0
        ) {
            return try {
                arrayOf(captureUri(ctx, cameraOut))
            } catch (e: Exception) {
                null
            }
        }
        return fromData
    }

    // ---------------- 其它 ----------------

    /**
     * 清理网页与图片缓存，但**保留 Cookie**——设置页里做清理不能把用户登录态一起清掉。
     * 表单数据与历史记录也不动，只回收最占空间的磁盘缓存。
     */
    fun clearWebCache(context: Context) {
        WebView(context).clearCache(true)
        CookieManager.getInstance().flush()
    }

    /** 原生分享：把商品名称 / 描述丢给系统分享面板（url 可为空） */
    fun shareIntent(title: String, text: String, url: String): Intent {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, if (url.isBlank()) text else "$text\n$url")
        }
        return Intent.createChooser(send, "分享到").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
