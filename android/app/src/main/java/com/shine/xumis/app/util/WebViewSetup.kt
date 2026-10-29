package com.shine.xumis.app.util

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient

/**
 * WebView 统一配置。
 *
 * App 里「购物车 / 我的账户 / 来图定制 / 下单」这些需要完整交易流程的页面走站点 WebView，
 * 因此必须把 JS、DOM Storage、Cookie（含第三方）、混合内容以及**文件上传**都打开，
 * 否则站点上的图片上传（来图定制）和登录态会失效。
 */
object WebViewSetup {

    @SuppressLint("SetJavaScriptEnabled")
    fun apply(web: WebView) {
        web.settings.apply {
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
        }
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true)
        CookieManager.getInstance().flush()
    }

    /** 站内 http(s) 留在 WebView，其余协议（tel/mailto/支付宝唤端等）交给系统处理 */
    fun client(context: Context): WebViewClient = object : WebViewClient() {
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
    }

    /**
     * 文件选择 Intent：站点的「来图定制」需要上传参考图，
     * 未实现 onShowFileChooser 时 WebView 里点上传会没有任何反应。
     */
    fun fileChooserIntent(params: WebChromeClient.FileChooserParams?): Intent {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        val accepts = params?.acceptTypes?.filter { it.isNotBlank() } ?: emptyList()
        val mimes = accepts.filter { it.contains("/") }
        if (mimes.isNotEmpty()) {
            intent.type = mimes.first()
            intent.putExtra(Intent.EXTRA_MIME_TYPES, mimes.toTypedArray())
        }
        if (params?.mode == WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE) {
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
        return intent
    }

    /** 从系统选择结果解析出 WebView 需要的 Uri 数组 */
    fun parseChooserResult(resultCode: Int, data: Intent?): Array<Uri>? =
        WebChromeClient.FileChooserParams.parseResult(resultCode, data)
}
