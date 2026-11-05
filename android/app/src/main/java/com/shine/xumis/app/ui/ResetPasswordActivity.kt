package com.shine.xumis.app.ui

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.shine.xumis.app.api.ApiClient
import com.shine.xumis.app.databinding.ActivityResetPasswordBinding
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 原生找回密码页：邮箱验证码 → 直接设新密码。
 * 以前这里会跳到网页版 /forgot-password 再点邮件里的链接，现在全程在 App 内完成。
 */
class ResetPasswordActivity : AppCompatActivity() {

    private lateinit var binding: ActivityResetPasswordBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityResetPasswordBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.topAppBar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.topAppBar.setNavigationOnClickListener { finish() }

        intent.getStringExtra("email")?.let { binding.etEmail.setText(it) }

        binding.btnSendCode.setOnClickListener { sendCode() }
        binding.btnReset.setOnClickListener { submit() }
    }

    private fun sendCode() {
        val email = binding.etEmail.text.toString().trim()
        if (!email.contains("@")) return error("请输入正确的邮箱地址")
        binding.btnSendCode.isEnabled = false
        binding.tvCodeHint.visibility = View.GONE
        lifecycleScope.launch {
            try {
                val r = ApiClient.api().passwordSendCode(email)
                when {
                    r.ok -> {
                        hint("验证码已发送到 $email，请查收")
                        startCooldown()
                    }
                    r.error == "noAccount" -> {
                        error("该邮箱还没有注册，请先注册新账号")
                        binding.btnSendCode.isEnabled = true
                    }
                    r.error == "rateLimited" -> {
                        error("发送过于频繁，请 1 分钟后再试")
                        startCooldown()
                    }
                    else -> {
                        error("邮箱格式不正确")
                        binding.btnSendCode.isEnabled = true
                    }
                }
            } catch (e: Exception) {
                error("发送失败：${e.message}")
                binding.btnSendCode.isEnabled = true
            }
        }
    }

    private fun startCooldown() {
        var left = 60
        binding.btnSendCode.isEnabled = false
        lifecycleScope.launch {
            while (left > 0) {
                binding.btnSendCode.text = "${left}s 后重发"
                delay(1000)
                left--
            }
            binding.btnSendCode.text = "发送验证码"
            binding.btnSendCode.isEnabled = true
        }
    }

    private fun submit() {
        val email = binding.etEmail.text.toString().trim()
        val code = binding.etCode.text.toString().trim()
        val pw = binding.etPassword.text.toString()
        val pw2 = binding.etPassword2.text.toString()

        if (!email.contains("@")) return error("请输入正确的邮箱地址")
        if (code.length != 6) return error("请输入 6 位验证码")
        if (pw.length < 8) return error("新密码至少 8 位")
        if (pw != pw2) return error("两次输入的密码不一致")

        loading(true)
        lifecycleScope.launch {
            try {
                val resp = ApiClient.api().passwordReset(email, code, pw)
                loading(false)
                if (resp.isSuccessful && resp.body()?.ok == true) {
                    Toast.makeText(this@ResetPasswordActivity, "密码已重置，请用新密码登录", Toast.LENGTH_LONG).show()
                    setResult(RESULT_OK)
                    finish()
                } else {
                    error(translate(resp.body()?.error))
                }
            } catch (e: Exception) {
                loading(false)
                error("重置失败：${e.message}")
            }
        }
    }

    private fun translate(code: String?): String = when (code) {
        "badCode" -> "验证码不正确或已过期，请重新获取"
        "weakPassword" -> "新密码至少 8 位"
        "noAccount" -> "该邮箱还没有注册"
        else -> "重置失败，请稍后重试"
    }

    private fun loading(on: Boolean) {
        binding.tvProgress.visibility = if (on) View.VISIBLE else View.GONE
        binding.btnReset.isEnabled = !on
        binding.tvError.visibility = View.GONE
    }

    private fun error(msg: String) {
        binding.tvError.text = msg
        binding.tvError.visibility = View.VISIBLE
    }

    private fun hint(msg: String) {
        binding.tvCodeHint.text = msg
        binding.tvCodeHint.visibility = View.VISIBLE
        binding.tvError.visibility = View.GONE
    }
}
