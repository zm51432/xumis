package com.shine.xumis.app.ui

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.shine.xumis.app.api.CommerceApi
import com.shine.xumis.app.databinding.ActivityPasswordBinding
import kotlinx.coroutines.launch

/** 修改密码：原密码 + 新密码 + 确认，调用 /api/m/auth/password */
class PasswordActivity : AppCompatActivity() {
    private lateinit var binding: ActivityPasswordBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPasswordBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.topAppBar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.topAppBar.setNavigationOnClickListener { finish() }
        binding.btnSave.setOnClickListener { save() }
    }

    private fun save() {
        val old = binding.etOld.text.toString()
        val new = binding.etNew.text.toString()
        val confirm = binding.etConfirm.text.toString()
        if (old.isBlank()) { binding.etOld.error = "请输入原密码"; return }
        if (new.length < 8) { binding.etNew.error = "新密码至少 8 位"; return }
        if (new != confirm) { binding.etConfirm.error = "两次输入不一致"; return }

        binding.pb.visibility = View.VISIBLE
        binding.btnSave.isEnabled = false
        lifecycleScope.launch {
            try {
                CommerceApi.changePassword(old, new)
                Toast.makeText(this@PasswordActivity, "密码已修改", Toast.LENGTH_SHORT).show()
                finish()
            } catch (e: Exception) {
                Toast.makeText(this@PasswordActivity, "修改失败：${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                binding.pb.visibility = View.GONE
                binding.btnSave.isEnabled = true
            }
        }
    }
}
