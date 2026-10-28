package com.shine.xumis.app.ui

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.shine.xumis.app.api.ApiClient
import com.shine.xumis.app.databinding.ActivitySettingsBinding
import com.shine.xumis.app.util.ServerConfig

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

        binding.btnSave.setOnClickListener {
            val url = binding.etUrl.text.toString().trim()
            if (url.isEmpty()) {
                Toast.makeText(this, "地址不能为空", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            ServerConfig.baseUrl = url
            ApiClient.reset()
            Toast.makeText(this, "已保存，返回首页将重新连接", Toast.LENGTH_SHORT).show()
            finish()
        }

        binding.btnReset.setOnClickListener {
            ServerConfig.baseUrl = ServerConfig.DEFAULT_URL
            ApiClient.reset()
            binding.etUrl.setText(ServerConfig.baseUrl)
            Toast.makeText(this, "已重置为默认（模拟器地址 10.0.2.2:8080）", Toast.LENGTH_SHORT).show()
        }
    }
}
