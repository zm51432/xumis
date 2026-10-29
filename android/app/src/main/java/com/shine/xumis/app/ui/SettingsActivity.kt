package com.shine.xumis.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.shine.xumis.app.R
import com.shine.xumis.app.api.ApiClient
import com.shine.xumis.app.databinding.ActivitySettingsBinding
import com.shine.xumis.app.databinding.ItemSettingsRowBinding
import com.shine.xumis.app.util.AppPrefs
import com.shine.xumis.app.util.CatalogCache
import com.shine.xumis.app.util.OfflineAssets
import com.shine.xumis.app.util.Permissions
import com.shine.xumis.app.util.WebViewSetup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 设置：按常规 App 的分组目录组织，不再暴露任何服务器地址。
 *
 * 通用 / 内容与存储 / 帮助与支持 / 关于 四组，
 * 每组是可点击的设置行（图标 + 标题 + 说明 + 当前值），与系统设置的交互习惯一致。
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.topAppBar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.topAppBar.setNavigationOnClickListener { finish() }

        AppPrefs.init(applicationContext)

        setUpGeneral()
        setUpContent()
        setUpPermission()
        setUpHelp()
        setUpAbout()

        refreshRows()
    }

    override fun onResume() {
        super.onResume()
        // 从权限中心回来时刷新「x/4 项已允许」
        if (::binding.isInitialized) setValue(binding.rowPermissions, Permissions.summary(this))
    }

    // ---------------- 通用 ----------------

    private fun setUpGeneral() {
        bind(
            binding.rowTextZoom, R.drawable.ic_text_size, "网页字号",
            "站点页面在 App 内的显示大小"
        ) { chooseTextZoom() }

        bind(
            binding.rowNetwork, R.drawable.ic_network, "检查网络",
            "检测当前网络与商店服务的连通情况"
        ) { checkNetwork() }

        bind(
            binding.rowCache, R.drawable.ic_cache, "清除网页缓存",
            "只清理网页与图片缓存，不影响登录状态"
        ) { confirmClearCache() }
    }

    private fun chooseTextZoom() {
        MaterialAlertDialogBuilder(this)
            .setTitle("网页字号")
            .setSingleChoiceItems(AppPrefs.textZoomOptions, AppPrefs.textZoomIndex) { dialog, which ->
                AppPrefs.textZoomIndex = which
                dialog.dismiss()
                refreshRows()
                Toast.makeText(this, "已切换为${AppPrefs.textZoomLabel()}，重新打开页面后生效", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun checkNetwork() {
        binding.rowNetwork.tvSub.text = "正在检测…"
        lifecycleScope.launch {
            val t0 = System.currentTimeMillis()
            try {
                val ping = ApiClient.api().ping()
                val page = ApiClient.api().products(0, 1)
                val ms = System.currentTimeMillis() - t0
                binding.rowNetwork.tvSub.text = buildString {
                    append("已连接 · $ms ms")
                    append(" · 数据库").append(if (ping.dbOk == true) "正常" else "异常")
                    append(" · 商品 ${page.totalElements ?: 0} 件")
                }
            } catch (e: Exception) {
                binding.rowNetwork.tvSub.text = "连接失败，请检查手机网络后重试"
            }
        }
    }

    private fun confirmClearCache() {
        MaterialAlertDialogBuilder(this)
            .setTitle("清除网页缓存")
            .setMessage("将清理 App 内的网页与图片缓存，登录状态、购物车与离线商品册不受影响。")
            .setNegativeButton("取消", null)
            .setPositiveButton("清除") { _, _ ->
                WebViewSetup.clearWebCache(this)
                Toast.makeText(this, "缓存已清理", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    // ---------------- 内容与存储 ----------------

    private fun setUpContent() {
        bind(
            binding.rowCatalog, R.drawable.ic_storage, "离线商品缓存",
            "正在统计…"
        ) { openOffline(OfflineAssets.CATALOG, "离线商品册") }

        bind(
            binding.rowOffline, R.drawable.ic_image, "离线资源",
            "正在统计…"
        ) { showOfflineDetail() }
    }

    private fun showOfflineDetail() {
        val imgs = OfflineAssets.imageCount(this)
        val size = OfflineAssets.sizeText(OfflineAssets.imageBytes(this))
        MaterialAlertDialogBuilder(this)
            .setTitle("离线资源")
            .setMessage(
                "以下内容随安装包内置，不额外消耗流量：\n\n" +
                    "· 商品图 $imgs 张（$size）\n" +
                    "· 离线商品册、离线首页、配送与售后条款 3 个页面\n" +
                    "· Nunito / Quicksand 字体 6 款，离线排版不变形\n\n" +
                    "断网时首页、商品册与条款均可正常浏览。"
            )
            .setNegativeButton("关闭", null)
            .setPositiveButton("打开商品册") { _, _ -> openOffline(OfflineAssets.CATALOG, "离线商品册") }
            .show()
    }

    // ---------------- 权限与隐私 ----------------

    private fun setUpPermission() {
        bind(
            binding.rowPermissions, R.drawable.ic_shield, "权限管理",
            "定位、手机相册、手机相机、蓝牙（仅此四项）"
        ) {
            startActivity(Intent(this, PermissionActivity::class.java))
        }
    }

    // ---------------- 帮助与支持 ----------------

    private fun setUpHelp() {
        bind(binding.rowStatus, R.drawable.ic_status, "运行状态", "通过率、延迟、缓存与崩溃记录") {
            startActivity(Intent(this, StatusActivity::class.java))
        }
        bind(binding.rowDiag, R.drawable.ic_diagnostics, "接口自检（50 次）", "连续校验商品、分类与健康接口") {
            startActivity(Intent(this, DiagnosticsActivity::class.java))
        }
        bind(binding.rowAbout, R.drawable.ic_help, "帮助与关于", "常见问题、离线内容与联系方式") {
            startActivity(Intent(this, AboutActivity::class.java))
        }
        bind(binding.rowPolicies, R.drawable.ic_doc, "配送与售后条款", "发货、跨境运费、退换与支付说明") {
            openOffline(OfflineAssets.POLICIES, "配送与售后条款")
        }
    }

    // ---------------- 关于 ----------------

    private fun setUpAbout() {
        bind(binding.rowVersion, R.drawable.ic_info, "版本", null, chevron = false)
        bind(binding.rowShare, R.drawable.ic_share, "分享给朋友", "把 App 推荐给朋友") {
            startActivity(
                WebViewSetup.shareIntent(
                    "shine 星心",
                    "shine 星心 · 饰品与手工品，官方手机客户端安装即用，断网也能浏览商品册。",
                    ""
                )
            )
        }
    }

    // ---------------- 刷新 ----------------

    private fun refreshRows() {
        setValue(binding.rowTextZoom, AppPrefs.textZoomLabel())
        setValue(binding.rowVersion, "v${versionName()}（${versionCode()}）")
        setValue(binding.rowPermissions, Permissions.summary(this))

        val snap = CatalogCache.load(this)
        binding.rowCatalog.tvSub.text = if (snap == null) {
            "暂无缓存，联网浏览后自动保存"
        } else {
            "${snap.items.size} 件 · 缓存于 ${CatalogCache.savedAtText(this)}"
        }

        lifecycleScope.launch {
            val (imgs, bytes) = withContext(Dispatchers.IO) {
                OfflineAssets.imageCount(this@SettingsActivity) to
                    OfflineAssets.imageBytes(this@SettingsActivity)
            }
            binding.rowOffline.tvSub.text =
                "商品图 $imgs 张 · ${OfflineAssets.sizeText(bytes)} · 内置字体 6 款"
        }
    }

    // ---------------- 小工具 ----------------

    /** 统一设置行的图标 / 文案 / 箭头 / 点击行为 */
    private fun bind(
        row: ItemSettingsRowBinding,
        icon: Int,
        title: String,
        sub: String? = null,
        value: String? = null,
        chevron: Boolean = true,
        onClick: (() -> Unit)? = null
    ) {
        row.ivIcon.setImageResource(icon)
        row.tvTitle.text = title
        row.tvSub.text = sub ?: ""
        row.tvSub.visibility = if (sub.isNullOrBlank()) View.GONE else View.VISIBLE
        row.tvValue.text = value ?: ""
        row.tvValue.visibility = if (value.isNullOrBlank()) View.GONE else View.VISIBLE
        row.ivChevron.visibility = if (chevron) View.VISIBLE else View.GONE
        row.root.isClickable = onClick != null
        if (onClick == null) row.root.background = null
        else row.root.setOnClickListener { onClick() }
    }

    /** 设置行右侧的当前值（空则隐藏） */
    private fun setValue(row: ItemSettingsRowBinding, text: String) {
        row.tvValue.text = text
        row.tvValue.visibility = if (text.isBlank()) View.GONE else View.VISIBLE
    }

    private fun openOffline(url: String, title: String) {
        startActivity(
            Intent(this, WebViewActivity::class.java)
                .putExtra("url", url)
                .putExtra("title", title)
        )
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
}
