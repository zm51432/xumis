package com.shine.xumis.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.shine.xumis.app.api.ApiClient
import com.shine.xumis.app.databinding.ActivitySplashBinding
import kotlinx.coroutines.launch

class SplashActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySplashBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnSettings.visibility = View.GONE
        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        binding.btnRetry.visibility = View.GONE
        binding.btnRetry.setOnClickListener { checkServer() }
        checkServer()
    }

    private fun checkServer() {
        binding.pb.visibility = View.VISIBLE
        binding.btnRetry.visibility = View.GONE
        binding.btnSettings.visibility = View.GONE
        binding.tvStatus.text = "正在连接服务器…"
        lifecycleScope.launch {
            try {
                val r = ApiClient.api().ping()
                if (r.ok == true) {
                    binding.tvStatus.text = "连接成功"
                    startActivity(Intent(this@SplashActivity, MainActivity::class.java))
                    finish()
                } else {
                    onFail("服务器返回异常，请检查后端是否正常运行")
                }
            } catch (e: Exception) {
                onFail("无法连接：${e.message}")
            }
        }
    }

    private fun onFail(msg: String) {
        binding.pb.visibility = View.GONE
        binding.tvStatus.text = "$msg\n可在「设置」中修改服务器地址，或点击「重试连接」"
        binding.btnSettings.visibility = View.VISIBLE
        binding.btnRetry.visibility = View.VISIBLE
    }
}
