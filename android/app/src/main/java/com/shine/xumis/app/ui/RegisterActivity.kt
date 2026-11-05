package com.shine.xumis.app.ui

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.shine.xumis.app.api.ApiClient
import com.shine.xumis.app.databinding.ActivityRegisterBinding
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 原生注册页：邮箱 → 发送验证码 → 填码 → 设密码 → 完成。
 * 不再打开网页版的 /register，交互与网页规则一致（6 位码、10 分钟有效、60 秒冷却）。
 */
class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding
    private var cooling = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.topAppBar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.topAppBar.setNavigationOnClickListener { finish() }

        // 从登录页带过来的邮箱，直接预填
        intent.getStringExtra("email")?.let { binding.etEmail.setText(it) }

        binding.btnSendCode.setOnClickListener { sendCode() }
        binding.btnRegister.setOnClickListener { submit() }
    }

    private fun sendCode() {
        val email = binding.etEmail.text.toString().trim()
        if (!email.contains("@")) {
            error("请输入正确的邮箱地址")
            return
        }
        binding.btnSendCode.isEnabled = false
        binding.tvCodeHint.visibility = View.GONE
        lifecycleScope.launch {
            try {
                val r = ApiClient.api().registerSendCode(email)
                when {
                    r.ok -> {
                        hint("验证码已发送到 $email，请查收（可能在垃圾邮件里）")
                        startCooldown()
                    }
                    r.error == "dupEmail" -> {
                        error("该邮箱已注册，请直接登录或找回密码")
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

    /** 60 秒重发冷却，与服务端限流保持一致 */
    private fun startCooldown() {
        cooling = 60
        binding.btnSendCode.isEnabled = false
        lifecycleScope.launch {
            while (cooling > 0) {
                binding.btnSendCode.text = "${cooling}s 后重发"
                delay(1000)
                cooling--
            }
            binding.btnSendCode.text = "发送验证码"
            binding.btnSendCode.isEnabled = true
        }
    }

    private fun submit() {
        val email = binding.etEmail.text.toString().trim()
        val code = binding.etCode.text.toString().trim()
        val name = binding.etName.text.toString().trim()
        val pw = binding.etPassword.text.toString()
        val pw2 = binding.etPassword2.text.toString()

        if (!email.contains("@")) return error("请输入正确的邮箱地址")
        if (code.length != 6) return error("请输入 6 位验证码")
        if (pw.length < 8) return error("密码至少 8 位")
        if (pw != pw2) return error("两次输入的密码不一致")

        showLoading(true)
        lifecycleScope.launch {
            try {
                val resp = ApiClient.api().register(email, pw, name, code)
                showLoading(false)
                if (resp.isSuccessful && resp.body()?.ok == true) {
                    Toast.makeText(this@RegisterActivity, "注册成功，已自动登录", Toast.LENGTH_SHORT).show()
                    setResult(RESULT_OK)
                    finish()
                } else {
                    error(translate(resp.body()?.error))
                }
            } catch (e: Exception) {
                showLoading(false)
                error("注册失败：${e.message}")
            }
        }
    }

    private fun translate(code: String?): String = when (code) {
        "badCode" -> "验证码不正确或已过期，请重新获取"
        "dupEmail" -> "该邮箱已注册，请直接登录"
        "weakPassword" -> "密码至少 8 位"
        "badEmail" -> "邮箱格式不正确"
        else -> "注册失败，请稍后重试"
    }

    private fun showLoading(loading: Boolean) {
        binding.tvProgress.visibility = if (loading) View.VISIBLE else View.GONE
        binding.btnRegister.isEnabled = !loading
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
