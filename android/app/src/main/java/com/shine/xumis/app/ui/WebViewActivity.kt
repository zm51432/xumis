package com.shine.xumis.app.ui

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Bundle
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.shine.xumis.app.databinding.ActivityWebviewBinding
import com.shine.xumis.app.util.OfflineAssets
import com.shine.xumis.app.util.Permissions
import com.shine.xumis.app.util.ServerConfig
import com.shine.xumis.app.util.WebViewSetup
import java.io.File

/**
 * 单页 WebView：商品详情/购物车/下单/定制等站点页面在 App 内打开。
 *
 * 站点用到的系统能力在这里按需申请：定位（运费/自提点）、
 * 相册与拍照（来图定制上传）、摄像头（扫溯源码）。
 */
class WebViewActivity : AppCompatActivity() {
    private lateinit var binding: ActivityWebviewBinding
    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private var cameraOut: File? = null
    private var geoOrigin: String? = null
    private var geoCallback: GeolocationPermissions.Callback? = null

    private val fileChooser = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val cb = filePathCallback
        filePathCallback = null
        cb?.onReceiveValue(WebViewSetup.chooserResult(this, result.resultCode, result.data, cameraOut))
        cameraOut = null
    }

    private val geoPerms = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        val origin = geoOrigin
        val cb = geoCallback
        geoOrigin = null
        geoCallback = null
        if (origin != null && cb != null) {
            cb.invoke(origin, Permissions.isGranted(this, Permissions.Group.LOCATION), false)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWebviewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.topAppBar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.topAppBar.setNavigationOnClickListener { finish() }

        val url = intent.getStringExtra("url") ?: ServerConfig.siteBase()
        intent.getStringExtra("title")?.let { binding.topAppBar.title = it }

        WebViewSetup.apply(binding.web)
        binding.web.webViewClient = WebViewSetup.client(this) { bad, reason ->
            binding.web.loadUrl(OfflineAssets.indexUrl(bad, reason))
        }
        binding.web.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                webView: WebView?,
                callback: ValueCallback<Array<Uri>>?,
                params: FileChooserParams?
            ): Boolean {
                filePathCallback?.onReceiveValue(null)
                filePathCallback = callback
                cameraOut = if (Permissions.isGranted(this@WebViewActivity, Permissions.Group.CAMERA)) {
                    WebViewSetup.newCaptureFile(this@WebViewActivity)
                } else {
                    null
                }
                return try {
                    fileChooser.launch(
                        WebViewSetup.chooserIntent(this@WebViewActivity, params, cameraOut)
                    )
                    true
                } catch (e: Exception) {
                    filePathCallback = null
                    cameraOut = null
                    false
                }
            }

            override fun onGeolocationPermissionsShowPrompt(
                origin: String?,
                callback: GeolocationPermissions.Callback?
            ) {
                if (origin == null || callback == null) return
                if (WebViewSetup.geoAllowDirectly(this@WebViewActivity, origin, callback)) return
                geoOrigin = origin
                geoCallback = callback
                geoPerms.launch(Permissions.manifestPerms(Permissions.Group.LOCATION))
            }

            override fun onPermissionRequest(request: PermissionRequest?) {
                WebViewSetup.handlePermissionRequest(this@WebViewActivity, request)
            }

            override fun onReceivedTitle(view: WebView?, title: String?) {
                if (!title.isNullOrBlank()) binding.topAppBar.title = title
            }
        }
        binding.web.loadUrl(url)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.web.canGoBack()) binding.web.goBack() else finish()
            }
        })
    }

    override fun onDestroy() {
        filePathCallback?.onReceiveValue(null)
        filePathCallback = null
        geoCallback?.invoke(geoOrigin ?: "", false, false)
        geoCallback = null
        geoOrigin = null
        cameraOut = null
        binding.web.destroy()
        super.onDestroy()
    }
}
