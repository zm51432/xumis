package com.shine.xumis.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.shine.xumis.app.R
import com.shine.xumis.app.api.ApiClient
import com.shine.xumis.app.databinding.ActivityProductDetailBinding
import com.shine.xumis.app.util.ServerConfig
import kotlinx.coroutines.launch

class ProductDetailActivity : AppCompatActivity() {
    private lateinit var binding: ActivityProductDetailBinding
    private var productId: Long = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProductDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.topAppBar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.topAppBar.setNavigationOnClickListener { finish() }

        productId = intent.getLongExtra("product_id", 0L)

        binding.btnBuy.setOnClickListener { openWeb(ServerConfig.siteBase() + "product/" + productId) }
        binding.btnCart.setOnClickListener { openWeb(ServerConfig.siteBase() + "cart") }

        load()
    }

    private fun load() {
        binding.progress.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                val p = ApiClient.api().product(productId)
                binding.tvTitle.text = p.title()
                binding.tvPrice.text = p.priceText()
                binding.tvCat.text = p.category ?: ""
                binding.tvMaterial.text = if (p.material.isNullOrBlank()) "材质：—" else "材质：${p.material}"
                binding.tvStock.text = p.stockText()
                binding.tvCustom.visibility = if (p.customizable == true) View.VISIBLE else View.GONE
                binding.tvDesc.text = p.description ?: "（暂无描述）"
                val url = p.fullImageUrl()
                if (url != null) {
                    Glide.with(this@ProductDetailActivity).load(url)
                        .placeholder(R.drawable.ic_placeholder).into(binding.imgProduct)
                }
            } catch (e: Exception) {
                Toast.makeText(this@ProductDetailActivity, "加载失败：${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                binding.progress.visibility = View.GONE
            }
        }
    }

    private fun openWeb(url: String) {
        startActivity(Intent(this, WebViewActivity::class.java).putExtra("url", url))
    }
}
