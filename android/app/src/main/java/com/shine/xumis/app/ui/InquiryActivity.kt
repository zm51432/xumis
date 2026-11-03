package com.shine.xumis.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.shine.xumis.app.api.CommerceApi
import com.shine.xumis.app.databinding.ActivityInquiryBinding
import com.shine.xumis.app.util.LocalRecords
import kotlinx.coroutines.launch

/**
 * 询盘联系（原生）：提交咨询信息。
 * 支持从订单详情带参数进入（预填订单号），提交后落本机记录可回看。
 */
class InquiryActivity : AppCompatActivity() {
    private lateinit var binding: ActivityInquiryBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityInquiryBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.topAppBar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.topAppBar.setNavigationOnClickListener { finish() }
        prefill()
        binding.btnSubmit.setOnClickListener { submit() }
    }

    /** 从订单详情跳转时，把订单号等信息预填进来，用户不用重复输入 */
    private fun prefill() {
        intent.getStringExtra("prefill_name")?.takeIf { it.isNotBlank() }?.let { binding.etName.setText(it) }
        intent.getStringExtra("prefill_email")?.takeIf { it.isNotBlank() }?.let { binding.etEmail.setText(it) }
        intent.getStringExtra("prefill_subject")?.takeIf { it.isNotBlank() }?.let { binding.etSubject.setText(it) }
        intent.getStringExtra("prefill_message")?.takeIf { it.isNotBlank() }?.let { binding.etMessage.setText(it) }
    }

    private fun submit() {
        val name = binding.etName.text.toString().trim()
        val email = binding.etEmail.text.toString().trim()
        val subject = binding.etSubject.text.toString().trim()
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
                    subject = subject.takeIf { it.isNotBlank() },
                    message = message
                )
                // 本机留档：提交后可在「我的记录」回看
                LocalRecords.add(
                    this@InquiryActivity, LocalRecords.TYPE_INQUIRY,
                    title = subject.ifBlank { "在线咨询" },
                    detail = message,
                    email = email
                )
                MaterialAlertDialogBuilder(this@InquiryActivity)
                    .setTitle("已提交咨询")
                    .setMessage("我们会尽快通过邮箱（$email）回复你。")
                    .setCancelable(false)
                    .setNegativeButton("查看我的记录") { _, _ ->
                        startActivity(Intent(this@InquiryActivity, MyRecordsActivity::class.java))
                        finish()
                    }
                    .setPositiveButton("完成") { _, _ -> finish() }
                    .show()
            } catch (e: Exception) {
                Toast.makeText(this@InquiryActivity, "提交失败：${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                binding.pb.visibility = View.GONE
                binding.btnSubmit.isEnabled = true
            }
        }
    }
}
