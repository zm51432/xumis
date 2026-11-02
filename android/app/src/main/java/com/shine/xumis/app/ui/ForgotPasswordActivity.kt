package com.shine.xumis.app.ui

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.shine.xumis.app.api.CommerceApi
import com.shine.xumis.app.databinding.ActivityForgotPasswordBinding
import kotlinx.coroutines.launch

/** 找回密码（原生）：输入邮箱 → 发送重置邮件 */
class ForgotPasswordActivity : AppCompatActivity() {
    private lateinit var binding: ActivityForgotPasswordBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityForgotPasswordBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.topAppBar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.topAppBar.setNavigationOnClickListener { finish() }
        binding.btnSubmit.setOnClickListener { submit() }
    }

    private fun submit() {
        val email = binding.etEmail.text.toString().trim()
        if (!email.contains("@")) { binding.etEmail.error = "请输入正确的邮箱"; return }
        binding.pb.visibility = View.VISIBLE
        binding.btnSubmit.isEnabled = false
        lifecycleScope.launch {
            try {
                CommerceApi.forgotPassword(email)
                binding.tvHint.visibility = View.VISIBLE
                binding.tvHint.text = "重置邮件已发送到 $email，请查收。\n邮件里会有一串「重置令牌」，到「重置密码」页填入即可设置新密码。"
            } catch (e: Exception) {
                Toast.makeText(this@ForgotPasswordActivity, "发送失败：${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                binding.pb.visibility = View.GONE
                binding.btnSubmit.isEnabled = true
            }
        }
    }
}
