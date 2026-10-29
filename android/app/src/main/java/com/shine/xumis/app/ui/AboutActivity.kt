package com.shine.xumis.app.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.shine.xumis.app.api.ApiClient
import com.shine.xumis.app.databinding.ActivityAboutBinding
import com.shine.xumis.app.util.CatalogCache
import com.shine.xumis.app.util.OfflineAssets
import com.shine.xumis.app.util.WebViewSetup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 帮助与关于：版本、连接状态、离线资源清单、FAQ、联系方式。
 * 全部内容本地可得，不联网也能打开；不展示任何服务器地址。
 */
class AboutActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAboutBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAboutBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.topAppBar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.topAppBar.setNavigationOnClickListener { finish() }

        binding.tvVersion.text = "版本 ${versionName()} (${versionCode()})"
        binding.tvFaq.text = FAQ

        binding.btnCatalog.setOnClickListener {
            openAsset("离线商品册", OfflineAssets.CATALOG)
        }
        binding.btnPolicies.setOnClickListener {
            openAsset("配送与售后条款", OfflineAssets.POLICIES)
        }
        binding.btnShareApp.setOnClickListener {
            startActivity(
                WebViewSetup.shareIntent(
                    "shine 星心",
                    "shine 星心 · 饰品与手工品，官方手机客户端安装即用，断网也能浏览商品册。",
                    ""
                )
            )
        }

        fillOfflineStats()
        checkConnection()
    }

    private fun fillOfflineStats() {
        lifecycleScope.launch {
            val (imgs, bytes) = withContext(Dispatchers.IO) {
                OfflineAssets.imageCount(this@AboutActivity) to
                    OfflineAssets.imageBytes(this@AboutActivity)
            }
            val cached = CatalogCache.count(this@AboutActivity)
            binding.tvOfflineStat.text = buildString {
                append("· 商品图 $imgs 张（${OfflineAssets.sizeText(bytes)}）\n")
                append("· 离线商品册 1 份、配送与售后条款 1 份\n")
                append("· 内置 Nunito / Quicksand 字体 6 款，离线排版不变形\n")
                append("· 最近一次在线商品缓存：$cached 件（${CatalogCache.savedAtText(this@AboutActivity)}）")
            }
        }
    }

    private fun checkConnection() {
        binding.tvConn.text = "连接状态：检测中…"
        lifecycleScope.launch {
            try {
                val t0 = System.currentTimeMillis()
                val ping = ApiClient.api().ping()
                val ms = System.currentTimeMillis() - t0
                binding.tvConn.text = buildString {
                    append(if (ping.ok == true) "连接状态：正常" else "连接状态：服务异常")
                    append(" · ${ms} ms")
                    append(" · 数据库").append(if (ping.dbOk == true) "正常" else "异常")
                }
            } catch (e: Exception) {
                binding.tvConn.text = "连接状态：离线（可继续使用离线商品册与条款）\n${e.message}"
            }
        }
    }

    private fun openAsset(title: String, url: String) {
        startActivity(Intent(this, WebViewActivity::class.java).putExtra("url", url).putExtra("title", title))
    }

    private fun versionName(): String = try {
        packageManager.getPackageInfo(packageName, 0).versionName ?: "-"
    } catch (e: Exception) {
        "-"
    }

    private fun versionCode(): Long = try {
        val info = packageManager.getPackageInfo(packageName, 0)
        if (android.os.Build.VERSION.SDK_INT >= 28) info.longVersionCode
        else @Suppress("DEPRECATION") info.versionCode.toLong()
    } catch (e: Exception) {
        0L
    }

    companion object {
        private val FAQ = """
            1. 安装后需要做配置吗？
            不需要。App 出厂已在后台直连官方商店服务，打开即用，无需填写任何地址或账号配置。

            2. 没有网络还能用吗？
            可以。首页会展示上次缓存的商品列表，并标注缓存时间；「离线商品册」内置 12 件商品的图片、材质与描述，配送与售后条款也随包安装。

            3. 下单、支付在哪里完成？
            商品详情页点「去网页加购/购买」进入站点完整流程，支持 PayPal、微信支付、支付宝、万事达卡四种渠道。

            4. 登录状态会丢吗？
            App 与站点共用同一套 Cookie，切到购物车、我的账户仍在登录态；重启 App 后依然保留。

            5. 怎么确认服务端正常？
            本页上方显示实时连接状态；「运行状态」页可以跑 5 轮检测或 50 次压力自检，并保留历史结果。
        """.trimIndent()
    }
}
