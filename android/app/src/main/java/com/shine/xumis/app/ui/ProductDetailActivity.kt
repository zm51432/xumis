package com.shine.xumis.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.shine.xumis.app.R
import com.shine.xumis.app.api.ApiClient
import com.shine.xumis.app.databinding.ActivityProductDetailBinding
import com.shine.xumis.app.util.ServerConfig
import com.shine.xumis.app.util.ShareUtils
import kotlinx.coroutines.launch

class ProductDetailActivity : AppCompatActivity() {
    private lateinit var binding: ActivityProductDetailBinding
    private var productId: Long = 0L
    private var productTitle: String = "shine 星心"
    private var selectedSkuId: Long? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProductDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.topAppBar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.topAppBar.setNavigationOnClickListener { finish() }

        productId = intent.getLongExtra("product_id", 0L)

        // 「立即购买」：加购后直接进原生结算页，不再跳网页
        binding.btnBuy.setOnClickListener { buyNow() }
        // 「查看购物车」直接切到原生购物车页，不再打开网页
        binding.btnCart.setOnClickListener {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .putExtra("tab", "cart")
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            )
        }

        load()
    }

    /** 立即购买：先把本商品加入购物车，再打开原生结算页 */
    private fun buyNow() {
        if (productId <= 0L) {
            Toast.makeText(this, "商品信息加载中，请稍后", Toast.LENGTH_SHORT).show()
            return
        }
        binding.btnBuy.isEnabled = false
        lifecycleScope.launch {
            try {
                ApiClient.api().cartAdd(productId, 1, selectedSkuId)
                startActivity(Intent(this@ProductDetailActivity, CheckoutActivity::class.java))
            } catch (e: Exception) {
                Toast.makeText(this@ProductDetailActivity, "加入购物车失败：${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                binding.btnBuy.isEnabled = true
            }
        }
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
                    ShareUtils.shareIntent(
                        productTitle,
                        "$productTitle · shine 星心",
                        ServerConfig.siteBase() + "product/" + productId
                    )
                )
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
                binding.tvCustom.visibility = if (p.customizable == true) View.VISIBLE else View.GONE
                binding.tvDesc.text = p.description ?: "（暂无描述）"
                loadSkus()
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

    /** 阶段3：加载规格并渲染可选按钮，选中后加购/立即购买都会带上 skuId */
    private fun loadSkus() {
        lifecycleScope.launch {
            try {
                val list = ApiClient.api().productSkus(productId)
                binding.boxSkus.removeAllViews()
                if (list.isEmpty()) return@launch
                binding.tvSkuTitle.visibility = View.VISIBLE
                list.forEach { s ->
                    val b = TextView(this@ProductDetailActivity).apply {
                        val extra = s.priceDelta ?: 0.0
                        val extraText = if (extra > 0.0) "  (+US$${String.format("%.2f", extra)})" else ""
                        text = "${s.label ?: s.skuCode ?: "规格"}$extraText  ·  库存 ${s.stock ?: 0}"
                        textSize = 13f
                        setPadding(16, 12, 16, 12)
                        setBackgroundColor(getColor(R.color.shine_yellow_line))
                        setOnClickListener {
                            selectedSkuId = s.skuId
                            for (i in 0 until binding.boxSkus.childCount) {
                                binding.boxSkus.getChildAt(i).alpha = 0.55f
                            }
                            alpha = 1f
                        }
                    }
                    binding.boxSkus.addView(b)
                }
            } catch (e: Exception) {
                // 商品没有规格时保持原样，不影响购买
            }
        }
    }
}
