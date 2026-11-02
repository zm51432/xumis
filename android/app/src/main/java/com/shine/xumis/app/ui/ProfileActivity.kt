package com.shine.xumis.app.ui

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.shine.xumis.app.api.CommerceApi
import com.shine.xumis.app.databinding.ActivityProfileBinding
import kotlinx.coroutines.launch

/** 收货资料：查看并编辑收货人姓名 / 电话 / 国家 / 地址 */
class ProfileActivity : AppCompatActivity() {
    private lateinit var binding: ActivityProfileBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.topAppBar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.topAppBar.setNavigationOnClickListener { finish() }
        binding.btnSave.setOnClickListener { save() }
        load()
    }

    private fun load() {
        lifecycleScope.launch {
            try {
                val me = CommerceApi.me()
                val u = me.user
                if (me.loggedIn && u != null) {
                    binding.etName.setText(u.name ?: "")
                    binding.etPhone.setText(u.phone ?: "")
                    binding.etCountry.setText(u.country ?: "")
                    binding.etAddress.setText(u.address ?: "")
                }
            } catch (e: Exception) {
                Toast.makeText(this@ProfileActivity, "加载失败：${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun save() {
        binding.pb.visibility = View.VISIBLE
        binding.btnSave.isEnabled = false
        lifecycleScope.launch {
            try {
                CommerceApi.updateProfile(
                    binding.etName.text.toString().trim(),
                    binding.etPhone.text.toString().trim(),
                    binding.etCountry.text.toString().trim(),
                    binding.etAddress.text.toString().trim()
                )
                Toast.makeText(this@ProfileActivity, "已保存", Toast.LENGTH_SHORT).show()
                finish()
            } catch (e: Exception) {
                Toast.makeText(this@ProfileActivity, "保存失败：${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                binding.pb.visibility = View.GONE
                binding.btnSave.isEnabled = true
            }
        }
    }
}
