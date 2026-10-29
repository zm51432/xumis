package com.shine.xumis.app.ui

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.shine.xumis.app.api.ApiClient
import com.shine.xumis.app.databinding.ActivitySettingsBinding
import com.shine.xumis.app.util.ServerConfig
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.topAppBar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.topAppBar.setNavigationOnClickListener { finish() }

        binding.etUrl.setText(ServerConfig.baseUrl)
        binding.tvResult.text = if (ServerConfig.isUsingDefault()) {
            "当前：线上服务器（出厂默认，无需修改）"
        } else {
            "当前：自定义地址"
        }

        binding.btnSave.setOnClickListener {
            val url = binding.etUrl.text.toString().trim()
            if (url.isEmpty()) {
                Toast.makeText(this, "地址不能为空", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            ServerConfig.baseUrl = url
            ApiClient.reset()
            binding.etUrl.setText(ServerConfig.baseUrl)
            Toast.makeText(this, "已保存，正在测试连接…", Toast.LENGTH_SHORT).show()
            testConnection()
        }

        binding.btnTest.setOnClickListener { testConnection() }

        binding.btnReset.setOnClickListener {
            ServerConfig.baseUrl = ServerConfig.DEFAULT_URL
            ApiClient.reset()
            binding.etUrl.setText(ServerConfig.baseUrl)
            binding.tvResult.text = "已恢复默认：${ServerConfig.PROD_URL}"
            Toast.makeText(this, "已恢复线上服务器", Toast.LENGTH_SHORT).show()
        }
    }

    /** 真实打一次 ping + 商品接口，确认「接口已打通」，而不是只保存一个地址 */
    private fun testConnection() {
        binding.tvResult.text = "正在连接 ${ServerConfig.baseUrl} …"
        lifecycleScope.launch {
            val t0 = System.currentTimeMillis()
            try {
                val ping = ApiClient.api().ping()
                val page = ApiClient.api().products(0, 1)
                val cost = System.currentTimeMillis() - t0
                binding.tvResult.text = buildString {
                    append("✅ 连接成功  ${cost} ms\n")
                    append("服务：${ping.service ?: "-"}   数据库：${if (ping.dbOk == true) "正常" else "异常"}\n")
                    append("接口：${ServerConfig.apiBase()}\n")
                    append("商品总数：${page.totalElements ?: 0}")
                }
            } catch (e: Exception) {
                binding.tvResult.text = buildString {
                    append("❌ 连接失败：${e.message}\n")
                    append("接口：${ServerConfig.apiBase()}\n")
                    append("请检查手机网络，或在上面确认服务器地址是否正确。")
                }
            }
        }
    }
}
