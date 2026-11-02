package com.shine.xumis.app.ui

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.shine.xumis.app.api.CommerceApi
import com.shine.xumis.app.databinding.ActivityCustomIdeaBinding
import kotlinx.coroutines.launch

/** 来图定制（原生）：提交定制需求 */
class CustomIdeaActivity : AppCompatActivity() {
    private lateinit var binding: ActivityCustomIdeaBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCustomIdeaBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.topAppBar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.topAppBar.setNavigationOnClickListener { finish() }
        binding.btnSubmit.setOnClickListener { submit() }
    }

    private fun submit() {
        val name = binding.etName.text.toString().trim()
        val email = binding.etEmail.text.toString().trim()
        val desc = binding.etDesc.text.toString().trim()
        if (name.isBlank()) { binding.etName.error = "请填写姓名"; return }
        if (!email.contains("@")) { binding.etEmail.error = "请填写正确的邮箱"; return }
        if (desc.isBlank()) { binding.etDesc.error = "请描述定制想法"; return }

        binding.pb.visibility = View.VISIBLE
        binding.btnSubmit.isEnabled = false
        lifecycleScope.launch {
            try {
                CommerceApi.customIdea(
                    customerName = name,
                    email = email,
                    country = binding.etCountry.text.toString().trim().takeIf { it.isNotBlank() },
                    description = desc,
                    expectCategory = binding.etCategory.text.toString().trim().takeIf { it.isNotBlank() }
                )
                Toast.makeText(this@CustomIdeaActivity, "已提交，我们会尽快联系你", Toast.LENGTH_SHORT).show()
                finish()
            } catch (e: Exception) {
                Toast.makeText(this@CustomIdeaActivity, "提交失败：${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                binding.pb.visibility = View.GONE
                binding.btnSubmit.isEnabled = true
            }
        }
    }
}
