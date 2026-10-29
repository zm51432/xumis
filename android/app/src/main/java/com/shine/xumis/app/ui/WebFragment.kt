package com.shine.xumis.app.ui

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.shine.xumis.app.databinding.FragmentWebBinding
import com.shine.xumis.app.util.OfflineAssets
import com.shine.xumis.app.util.ServerConfig
import com.shine.xumis.app.util.WebViewSetup

/**
 * 通用 WebView 容器（购物车 / 我的账户 等需要完整交易流程的页面走这里）。
 * 与站点共用同一套后端接口与登录态，因此 Cookie、JS、文件上传都要开启。
 */
open class WebFragment : Fragment() {
    private var _binding: FragmentWebBinding? = null
    private val binding get() = _binding!!
    private var filePathCallback: ValueCallback<Array<Uri>>? = null

    private val fileChooser = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val cb = filePathCallback
        filePathCallback = null
        cb?.onReceiveValue(WebViewSetup.parseChooserResult(result.resultCode, result.data))
    }

    open fun url(): String = ServerConfig.siteBase()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentWebBinding.inflate(inflater, container, false)
        return binding.root
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val ctx = requireContext()
        WebViewSetup.apply(binding.web)
        // 主文档加载失败（断网/超时/5xx）时切到内置离线页，避免整屏白页
        binding.web.webViewClient = WebViewSetup.client(ctx) { url, reason ->
            _binding?.web?.loadUrl(OfflineAssets.indexUrl(url, reason))
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
        }
        binding.web.loadUrl(url())

        // 站内返回：先回退 WebView 历史，到底了再交给 Activity。
        // 绑定 viewLifecycleOwner，切页/销毁时自动注销，避免多次注册导致返回被处理两遍。
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    val web = _binding?.web ?: return
                    if (web.canGoBack()) {
                        web.goBack()
                    } else {
                        isEnabled = false
                        requireActivity().onBackPressedDispatcher.onBackPressed()
                    }
                }
            }
        )
    }

    override fun onDestroyView() {
        filePathCallback?.onReceiveValue(null)
        filePathCallback = null
        binding.web.destroy()
        _binding = null
        super.onDestroyView()
    }
}

class CartFragment : WebFragment() {
    override fun url() = ServerConfig.siteBase() + "cart"
}

class AccountFragment : WebFragment() {
    override fun url() = ServerConfig.siteBase() + "account"
}
