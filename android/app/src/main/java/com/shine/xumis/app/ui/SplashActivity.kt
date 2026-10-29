package com.shine.xumis.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.shine.xumis.app.api.ApiClient
import com.shine.xumis.app.databinding.ActivitySplashBinding
import com.shine.xumis.app.util.CatalogCache
import com.shine.xumis.app.util.OfflineAssets
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 启动页：探活 → 进主界面。
 *
 * 弱网下先自动重试 3 次（0.8s / 1.6s 退避）再判定失败；
 * 判定失败也不是死路——有本地缓存可以离线进入，没有缓存就直接给离线商品册。
 * 页面上不出现服务器地址，用户只需要「重试」或「离线进入」两个选择。
 */
class SplashActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySplashBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnRetry.visibility = View.GONE
        binding.btnRetry.setOnClickListener { checkServer() }
        binding.btnOffline.visibility = View.GONE
        checkServer()
    }

    private fun checkServer() {
        binding.pb.visibility = View.VISIBLE
        binding.btnRetry.visibility = View.GONE
        binding.btnOffline.visibility = View.GONE
        binding.tvStatus.text = "正在连接商城…"
        lifecycleScope.launch {
            var lastError: String? = null
            for (attempt in 1..3) {
                try {
                    val r = ApiClient.api().ping()
                    if (r.ok == true) {
                        binding.tvStatus.text = "连接成功"
                        startActivity(Intent(this@SplashActivity, MainActivity::class.java))
                        finish()
                        return@launch
                    }
                    lastError = "服务暂时不可用，请稍后重试"
                } catch (e: Exception) {
                    lastError = "网络连接失败"
                }
                if (attempt < 3) {
                    binding.tvStatus.text = "第 $attempt 次连接失败，正在重试…"
                    delay(attempt * 800L)
                }
            }
            onFail(lastError ?: "网络连接失败")
        }
    }

    private fun onFail(msg: String) {
        binding.pb.visibility = View.GONE
        binding.tvStatus.text = "$msg\n可以重试，或先离线浏览已缓存的内容"
        binding.btnRetry.visibility = View.VISIBLE

        val snapshot = CatalogCache.load(this)
        binding.btnOffline.visibility = View.VISIBLE
        if (snapshot != null) {
            binding.btnOffline.text = "离线进入（缓存 ${snapshot.items.size} 件商品）"
            binding.btnOffline.setOnClickListener {
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            }
        } else {
            binding.btnOffline.text = "浏览离线商品册"
            binding.btnOffline.setOnClickListener {
                startActivity(
                    Intent(this, WebViewActivity::class.java)
                        .putExtra("url", OfflineAssets.CATALOG)
                        .putExtra("title", "离线商品册")
                )
            }
        }
    }
}
