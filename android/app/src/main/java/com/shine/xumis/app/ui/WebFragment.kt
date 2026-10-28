package com.shine.xumis.app.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.shine.xumis.app.databinding.FragmentWebBinding
import com.shine.xumis.app.util.ServerConfig

/** 通用 WebView 容器（购物车 / 我的 等需要完整交易流程的页面走这里） */
open class WebFragment : Fragment() {
    private var _binding: FragmentWebBinding? = null
    private val binding get() = _binding!!
    open fun url(): String = ServerConfig.siteBase()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentWebBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.web.settings.javaScriptEnabled = true
        binding.web.settings.domStorageEnabled = true
        binding.web.webViewClient = android.webkit.WebViewClient()
        binding.web.loadUrl(url())
    }

    override fun onDestroyView() {
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
