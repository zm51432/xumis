package com.shine.xumis.app.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.shine.xumis.app.R
import com.shine.xumis.app.api.ApiClient
import com.shine.xumis.app.api.CartApi
import com.shine.xumis.app.databinding.ActivityProductDetailBinding
import com.shine.xumis.app.util.ServerConfig
import com.shine.xumis.app.util.WebViewSetup
import kotlinx.coroutines.launch

class ProductDetailActivity : AppCompatActivity() {
    private lateinit var binding: ActivityProductDetailBinding
    private var productId: Long = 0L
    private var productTitle: String = "shine 星心"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProductDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.topAppBar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.topAppBar.setNavigationOnClickListener { finish() }

        productId = intent.getLongExtra("product_id", 0L)

        // 立即购买：进入商品落地页（落地页上有完整的下单/支付流程）
        binding.btnBuy.setOnClickListener {
            openWeb(ServerConfig.siteBase() + "product/" + productId)
        }
        // 加入购物车：原生调用，加完留在详情页，不跳转
        binding.btnCart.setOnClickListener { addToCart() }

        load()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.product_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            R.id.menu_share -> {
                startActivity(
                    WebViewSetup.shareIntent(
                        productTitle,
                        "$productTitle · shine 星心",
                        ServerConfig.siteBase() + "product/" + productId
                    )
                )
                true
            }
            R.id.menu_browser -> {
                try {
                    startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(ServerConfig.siteBase() + "product/" + productId))
                    )
                } catch (e: Exception) {
                    Toast.makeText(this, "没有可用的浏览器", Toast.LENGTH_SHORT).show()
                }
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun load() {
        binding.progress.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                val p = ApiClient.api().product(productId)
                productTitle = p.title()
                binding.tvTitle.text = p.title()
                binding.tvPrice.text = p.priceText()
                binding.tvCat.text = p.category ?: ""
                binding.tvMaterial.text = if (p.material.isNullOrBlank()) "材质：—" else "材质：${p.material}"
                binding.tvStock.text = p.stockText()
                binding.tvSold.text = p.soldText() ?: ""
                binding.tvSold.visibility = if (p.soldText() == null) View.GONE else View.VISIBLE
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

    private fun addToCart() {
        binding.btnCart.isEnabled = false
        lifecycleScope.launch {
            try {
                val count = CartApi.add(productId)
                CartApi.publish(count)
                Toast.makeText(this@ProductDetailActivity, "已加入购物车（$count 件）", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this@ProductDetailActivity, "加购失败：${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                binding.btnCart.isEnabled = true
            }
        }
    }

    private fun openWeb(url: String) {
        startActivity(Intent(this, WebViewActivity::class.java).putExtra("url", url))
    }
}
