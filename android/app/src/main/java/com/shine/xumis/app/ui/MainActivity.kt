package com.shine.xumis.app.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.shine.xumis.app.R
import com.shine.xumis.app.api.CartApi
import com.shine.xumis.app.databinding.ActivityMainBinding
import com.shine.xumis.app.util.AppPrefs
import com.shine.xumis.app.util.Permissions
import com.shine.xumis.app.util.UpdateChecker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    companion object {
        /** 前台静默检查的最小间隔：6 小时（只在检查成功后计时） */
        const val UPDATE_CHECK_INTERVAL = 6 * 60 * 60 * 1000L
        const val KEY_TAB = "selected_tab"

        /** 外部指定打开哪个 Tab（cart / home），用于「去购物车」「去逛逛」等闭环跳转 */
        const val EXTRA_OPEN_TAB = "open_tab"
        const val TAB_HOME = "home"
        const val TAB_CART = "cart"

        /** 统一跳转：清空栈回到主界面并选中目标 Tab */
        fun intentTo(ctx: Context, tab: String): Intent =
            Intent(ctx, MainActivity::class.java)
                .putExtra(EXTRA_OPEN_TAB, tab)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)

        /** 四个 Tab 的 Fragment 标签：切回来时复用同一个实例，不重建 WebView */
        const val TAG_HOME = "tab_home"
        const val TAG_SEARCH = "tab_search"
        const val TAG_CART = "tab_cart"
        const val TAG_ACCOUNT = "tab_account"
    }

    private var checkingUpdate = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.topAppBar)

        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home, R.id.nav_search, R.id.nav_cart, R.id.nav_account ->
                    showTab(item.itemId)
                else -> false
            }
        }

        // 再次点同一个 Tab：首页回滚到顶部，其余不动（常见 App 手势，避免误触）
        binding.bottomNav.setOnItemReselectedListener { item ->
            if (item.itemId == R.id.nav_home) {
                (supportFragmentManager.findFragmentByTag(TAG_HOME) as? HomeFragment)?.scrollToTop()
            }
        }

        // 恢复上次停留的 Tab：转屏或从后台被杀后重建，高亮和内容保持一致
        val startTab = when (intent.getStringExtra(EXTRA_OPEN_TAB)) {
            TAB_CART -> R.id.nav_cart
            TAB_HOME -> R.id.nav_home
            else -> savedInstanceState?.getInt(KEY_TAB, R.id.nav_home) ?: R.id.nav_home
        }
        binding.bottomNav.selectedItemId = startTab

        AppPrefs.init(applicationContext)
        if (savedInstanceState == null && !AppPrefs.permissionPrompted) {
            AppPrefs.permissionPrompted = true
            showPermissionIntro()
        }

        observeCartBadge()
    }

    /**
     * 购物车角标：加购成功后 CartApi 会广播最新件数，这里把数字挂到底栏的购物车图标上。
     * 跟随 STARTED 生命周期，切到后台自动停收。
     */
    private fun observeCartBadge() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                CartApi.changes.collect { count ->
                    val badge = binding.bottomNav.getOrCreateBadge(R.id.nav_cart)
                    badge.isVisible = count > 0
                    badge.number = count
                    badge.backgroundColor = getColor(R.color.shine_pink)
                    badge.badgeTextColor = getColor(R.color.shine_on_red)
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_TAB, binding.bottomNav.selectedItemId)
    }

    /** 已在前台时被「去购物车 / 去逛逛」唤起：直接切 Tab，不重建界面 */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        when (intent.getStringExtra(EXTRA_OPEN_TAB)) {
            TAB_CART -> binding.bottomNav.selectedItemId = R.id.nav_cart
            TAB_HOME -> binding.bottomNav.selectedItemId = R.id.nav_home
        }
    }

    /**
     * 切换 Tab：已有实例就 show/hide，没有才创建。
     * 这样购物车、我的这类 WebView 页面切回来还在原位置，不会重新加载。
     */
    private fun showTab(itemId: Int): Boolean {
        val (tag, factory) = when (itemId) {
            R.id.nav_home -> TAG_HOME to { HomeFragment() as Fragment }
            R.id.nav_search -> TAG_SEARCH to { SearchFragment() as Fragment }
            R.id.nav_cart -> TAG_CART to { CartFragment() as Fragment }
            R.id.nav_account -> TAG_ACCOUNT to { AccountFragment() as Fragment }
            else -> return false
        }

        val fm = supportFragmentManager
        val tx = fm.beginTransaction()
        for (t in listOf(TAG_HOME, TAG_SEARCH, TAG_CART, TAG_ACCOUNT)) {
            fm.findFragmentByTag(t)?.let { if (!it.isHidden) tx.hide(it) }
        }

        val existing = fm.findFragmentByTag(tag)
        if (existing == null) {
            tx.add(R.id.container, factory(), tag)
        } else {
            tx.show(existing)
        }
        tx.commit()
        return true
    }

    /**
     * 首次启动说明四类权限的用途，再去权限中心统一开启。
     * 不在这里一次性申请：小米 / OPPO / vivo 对同时弹四个权限框的处理不一，
     * 集中在权限中心里逐项确认，用户更清楚每个权限要做什么。
     */
    private fun showPermissionIntro() {
        if (Permissions.grantedCount(this) == Permissions.Group.values().size) return
        MaterialAlertDialogBuilder(this)
            .setTitle("完善使用体验")
            .setMessage(
                "App 只需要以下四项系统权限，其它一律不申请：\n\n" +
                    "· 定位 —— 估算运费、匹配就近自提点\n" +
                    "· 手机相册 —— 来图定制上传参考图\n" +
                    "· 手机相机 —— 下单现场拍图、扫溯源码\n" +
                    "· 蓝牙 —— 连接蓝牙标签打印机\n\n" +
                    "都可以拒绝，拒绝后仅对应功能不可用。"
            )
            .setNegativeButton("稍后再说", null)
            .setPositiveButton("去开启") { _, _ ->
                startActivity(Intent(this, PermissionActivity::class.java))
            }
            .show()
    }

    override fun onResume() {
        super.onResume()
        checkUpdateQuietly()
    }

    /**
     * 静默检查更新：进入前台时看一次，6 小时内不重复请求；
     * 用户点过「稍后再说」的同一个版本不再弹，除非服务端标了强制更新。
     *
     * 注意：时间戳在**请求成功后**才写入。之前是请求前就写，
     * 网络抖一下就要干等 6 小时才会再查。
     */
    private fun checkUpdateQuietly() {
        if (checkingUpdate) return
        val now = System.currentTimeMillis()
        if (now - AppPrefs.lastUpdateCheck < UPDATE_CHECK_INTERVAL) return
        checkingUpdate = true
        lifecycleScope.launch {
            val resp = runCatching {
                withContext(Dispatchers.IO) { UpdateChecker.check(this@MainActivity) }
            }.getOrNull()
            checkingUpdate = false
            if (resp == null) return@launch

            AppPrefs.lastUpdateCheck = System.currentTimeMillis()
            val rel = resp.latest ?: return@launch
            if (!resp.update) return@launch
            if (!resp.force && AppPrefs.skippedVersionCode == rel.versionCode) return@launch
            if (isFinishing || isDestroyed) return@launch
            startActivity(UpdateActivity.intent(this@MainActivity, rel, resp.force))
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
