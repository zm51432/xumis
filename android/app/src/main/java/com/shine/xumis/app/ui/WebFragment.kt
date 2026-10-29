package com.shine.xumis.app.ui

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.shine.xumis.app.databinding.FragmentWebBinding
import com.shine.xumis.app.util.OfflineAssets
import com.shine.xumis.app.util.Permissions
import com.shine.xumis.app.util.ServerConfig
import com.shine.xumis.app.util.WebViewSetup
import java.io.File

/**
 * 通用 WebView 容器（购物车 / 我的账户 等需要完整交易流程的页面走这里）。
 * 与站点共用同一套后端接口与登录态，因此 Cookie、JS、文件上传都要开启。
 *
 * 站点用到的系统能力（定位、拍照上传、摄像头）在这里按需申请权限：
 * 未授权时先向系统申请，授权后原地放行，用户不需要退出页面。
 */
open class WebFragment : Fragment() {
    private var _binding: FragmentWebBinding? = null
    private val binding get() = _binding!!
    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private var cameraOut: File? = null
    private var geoOrigin: String? = null
    private var geoCallback: GeolocationPermissions.Callback? = null

    private val fileChooser = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val cb = filePathCallback
        filePathCallback = null
        val ctx = context
        if (cb != null && ctx != null) {
            cb.onReceiveValue(
                WebViewSetup.chooserResult(ctx, result.resultCode, result.data, cameraOut)
            )
        } else {
            cb?.onReceiveValue(null)
        }
        cameraOut = null
    }

    /** 站点请求定位时若还没授权，这里申请；结果回来后补答 WebView 的询问 */
    private val geoPerms = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        val origin = geoOrigin
        val cb = geoCallback
        geoOrigin = null
        geoCallback = null
        val ctx = context ?: return@registerForActivityResult
        if (origin != null && cb != null) {
            cb.invoke(origin, Permissions.isGranted(ctx, Permissions.Group.LOCATION), false)
        }
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
                // 已授予相机权限才把「拍照」并进选择器，否则系统相机 App 会直接拒绝
                cameraOut = if (Permissions.isGranted(ctx, Permissions.Group.CAMERA)) {
                    WebViewSetup.newCaptureFile(ctx)
                } else {
                    null
                }
                return try {
                    fileChooser.launch(WebViewSetup.chooserIntent(ctx, params, cameraOut))
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
                if (WebViewSetup.geoAllowDirectly(ctx, origin, callback)) return
                geoOrigin = origin
                geoCallback = callback
                geoPerms.launch(Permissions.manifestPerms(Permissions.Group.LOCATION))
            }

            override fun onPermissionRequest(request: PermissionRequest?) {
                WebViewSetup.handlePermissionRequest(ctx, request)
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
        geoCallback?.invoke(geoOrigin ?: "", false, false)
        geoCallback = null
        geoOrigin = null
        cameraOut = null
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
