package com.shine.xumis.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.shine.xumis.app.R
import com.shine.xumis.app.databinding.ActivityMainBinding
import com.shine.xumis.app.util.AppPrefs
import com.shine.xumis.app.util.Permissions
import com.shine.xumis.app.util.UpdateChecker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    private companion object {
        /** 前台静默检查的最小间隔：6 小时（只在检查成功后计时） */
        const val UPDATE_CHECK_INTERVAL = 6 * 60 * 60 * 1000L
        const val KEY_TAB = "selected_tab"

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

        // 恢复上次停留的 Tab：转屏或从后台被杀后重建，高亮和内容保持一致
        val startTab = savedInstanceState?.getInt(KEY_TAB, R.id.nav_home) ?: R.id.nav_home
        binding.bottomNav.selectedItemId = startTab

        AppPrefs.init(applicationContext)
        if (savedInstanceState == null && !AppPrefs.permissionPrompted) {
            AppPrefs.permissionPrompted = true
            showPermissionIntro()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_TAB, binding.bottomNav.selectedItemId)
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
