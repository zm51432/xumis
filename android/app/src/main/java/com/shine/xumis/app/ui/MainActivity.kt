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
        /** 前台静默检查的最小间隔：6 小时 */
        const val UPDATE_CHECK_INTERVAL = 6 * 60 * 60 * 1000L
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.topAppBar)

        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> switchTo(HomeFragment())
                R.id.nav_search -> switchTo(SearchFragment())
                R.id.nav_cart -> switchTo(CartFragment())
                R.id.nav_account -> switchTo(AccountFragment())
                else -> false
            }
        }

        if (savedInstanceState == null) {
            binding.bottomNav.selectedItemId = R.id.nav_home
        }

        AppPrefs.init(applicationContext)
        if (savedInstanceState == null && !AppPrefs.permissionPrompted) {
            AppPrefs.permissionPrompted = true
            showPermissionIntro()
        }
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
     * 静默检查更新：进入前台时看一次，但 6 小时内不重复请求；
     * 用户点过「稍后再说」的同一个版本不再弹，除非服务端标了强制更新。
     */
    private fun checkUpdateQuietly() {
        val now = System.currentTimeMillis()
        if (now - AppPrefs.lastUpdateCheck < UPDATE_CHECK_INTERVAL) return
        AppPrefs.lastUpdateCheck = now
        lifecycleScope.launch {
            val resp = runCatching {
                withContext(Dispatchers.IO) { UpdateChecker.check(this@MainActivity) }
            }.getOrNull() ?: return@launch
            val rel = resp.latest ?: return@launch
            if (!resp.update) return@launch
            if (!resp.force && AppPrefs.skippedVersionCode == rel.versionCode) return@launch
            if (isFinishing) return@launch
            startActivity(UpdateActivity.intent(this@MainActivity, rel, resp.force))
        }
    }

    private fun switchTo(fragment: Fragment): Boolean {
        supportFragmentManager.beginTransaction()
            .replace(R.id.container, fragment)
            .commit()
        return true
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
