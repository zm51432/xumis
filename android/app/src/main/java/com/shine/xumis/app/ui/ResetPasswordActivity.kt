package com.shine.xumis.app.ui

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.shine.xumis.app.api.CommerceApi
import com.shine.xumis.app.databinding.ActivityResetPasswordBinding
import kotlinx.coroutines.launch

/** 重置密码（原生）：填邮件里的重置令牌 + 新密码 */
class ResetPasswordActivity : AppCompatActivity() {
    private lateinit var binding: ActivityResetPasswordBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityResetPasswordBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.topAppBar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.topAppBar.setNavigationOnClickListener { finish() }
        binding.btnSubmit.setOnClickListener { submit() }
    }

    private fun submit() {
        val token = binding.etToken.text.toString().trim()
        val password = binding.etPassword.text.toString()
        val confirm = binding.etConfirm.text.toString()
        if (token.isBlank()) { binding.etToken.error = "请填写重置令牌"; return }
        if (password.length < 8) { binding.etPassword.error = "新密码至少 8 位"; return }
        if (password != confirm) { binding.etConfirm.error = "两次输入不一致"; return }

        binding.pb.visibility = View.VISIBLE
        binding.btnSubmit.isEnabled = false
        lifecycleScope.launch {
            try {
                CommerceApi.resetPassword(token, password)
                Toast.makeText(this@ResetPasswordActivity, "密码已重置，请重新登录", Toast.LENGTH_SHORT).show()
                finish()
            } catch (e: Exception) {
                Toast.makeText(this@ResetPasswordActivity, "重置失败：${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                binding.pb.visibility = View.GONE
                binding.btnSubmit.isEnabled = true
            }
        }
    }
}
