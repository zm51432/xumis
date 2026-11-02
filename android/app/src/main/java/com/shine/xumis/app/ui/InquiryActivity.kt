package com.shine.xumis.app.ui

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.shine.xumis.app.api.CommerceApi
import com.shine.xumis.app.databinding.ActivityInquiryBinding
import kotlinx.coroutines.launch

/** 询盘联系（原生）：提交咨询信息 */
class InquiryActivity : AppCompatActivity() {
    private lateinit var binding: ActivityInquiryBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityInquiryBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.topAppBar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.topAppBar.setNavigationOnClickListener { finish() }
        binding.btnSubmit.setOnClickListener { submit() }
    }

    private fun submit() {
        val name = binding.etName.text.toString().trim()
        val email = binding.etEmail.text.toString().trim()
        val message = binding.etMessage.text.toString().trim()
        if (name.isBlank()) { binding.etName.error = "请填写姓名"; return }
        if (!email.contains("@")) { binding.etEmail.error = "请填写正确的邮箱"; return }
        if (message.isBlank()) { binding.etMessage.error = "请填写咨询内容"; return }

        binding.pb.visibility = View.VISIBLE
        binding.btnSubmit.isEnabled = false
        lifecycleScope.launch {
            try {
                CommerceApi.inquiry(
                    name = name,
                    email = email,
                    subject = binding.etSubject.text.toString().trim().takeIf { it.isNotBlank() },
                    message = message
                )
                Toast.makeText(this@InquiryActivity, "已提交，我们会尽快回复你", Toast.LENGTH_SHORT).show()
                finish()
            } catch (e: Exception) {
                Toast.makeText(this@InquiryActivity, "提交失败：${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                binding.pb.visibility = View.GONE
                binding.btnSubmit.isEnabled = true
            }
        }
    }
}
