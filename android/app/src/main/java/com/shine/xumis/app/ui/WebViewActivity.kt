package com.shine.xumis.app.ui

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Bundle
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.shine.xumis.app.databinding.ActivityWebviewBinding
import com.shine.xumis.app.util.OfflineAssets
import com.shine.xumis.app.util.ServerConfig
import com.shine.xumis.app.util.WebViewSetup

/** 单页 WebView：商品详情/购物车/下单/定制等站点页面在 App 内打开 */
class WebViewActivity : AppCompatActivity() {
    private lateinit var binding: ActivityWebviewBinding
    private var filePathCallback: ValueCallback<Array<Uri>>? = null

    private val fileChooser = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val cb = filePathCallback
        filePathCallback = null
        cb?.onReceiveValue(WebViewSetup.parseChooserResult(result.resultCode, result.data))
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
                return try {
                    fileChooser.launch(WebViewSetup.fileChooserIntent(params))
                    true
                } catch (e: Exception) {
                    filePathCallback = null
                    false
                }
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
        binding.web.destroy()
        super.onDestroy()
    }
}
