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
import com.google.android.material.snackbar.Snackbar
import com.shine.xumis.app.R
import com.shine.xumis.app.api.ApiClient
import com.shine.xumis.app.api.CartApi
import com.shine.xumis.app.api.CommerceApi
import com.shine.xumis.app.databinding.ActivityProductDetailBinding
import com.shine.xumis.app.util.ServerConfig
import com.shine.xumis.app.util.WebViewSetup
import kotlinx.coroutines.launch

class ProductDetailActivity : AppCompatActivity() {
    private lateinit var binding: ActivityProductDetailBinding
    private var productId: Long = 0L
    private var productTitle: String = "shine 星心"
    /** 阶段3：当前选中的规格（无规格商品为 null，加购时不带 skuId） */
    private var selectedSkuId: Long? = null
    private var basePrice: Double? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProductDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.topAppBar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.topAppBar.setNavigationOnClickListener { finish() }

        productId = intent.getLongExtra("product_id", 0L)

        // 立即购买：原生加购后直接进结算页（不再跳商品 Web 落地页）
        binding.btnBuy.setOnClickListener { buyNow() }
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
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun load() {
        binding.progress.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                val p = ApiClient.api().product(productId)
                productTitle = p.title()
                basePrice = p.price
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
                loadSkus()
            } catch (e: Exception) {
                Toast.makeText(this@ProductDetailActivity, "加载失败：${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                binding.progress.visibility = View.GONE
            }
        }
    }

    /**
     * 阶段3：加载规格并渲染可选按钮，默认选中第一个有货的规格；
     * 选中后加购 / 立即购买都会带上 skuId，价格随规格加价实时刷新。
     */
    private fun loadSkus() {
        lifecycleScope.launch {
            try {
                val list = CommerceApi.productSkus(productId)
                binding.boxSkus.removeAllViews()
                if (list.isEmpty()) return@launch
                binding.tvSkuTitle.visibility = View.VISIBLE
                var default: Long? = null
                list.forEach { s ->
                    if (default == null && s.stock > 0) default = s.skuId
                    val b = android.widget.TextView(this@ProductDetailActivity).apply {
                        val extra = s.priceDelta
                        val extraText = if (extra > 0.0)
                            "  (+" + String.format(java.util.Locale.US, "$%.2f", extra) + ")" else ""
                        text = "${s.label ?: s.labelEn ?: s.skuCode ?: "规格"}$extraText  ·  库存 ${s.stock}"
                        textSize = 13f
                        setPadding(24, 18, 24, 18)
                        setBackgroundColor(getColor(R.color.shine_yellow_soft))
                        alpha = 0.55f
                        setOnClickListener {
                            selectSku(s.skuId, s.priceDelta, s.stock)
                            for (i in 0 until binding.boxSkus.childCount) {
                                binding.boxSkus.getChildAt(i).alpha = 0.55f
                            }
                            alpha = 1f
                        }
                    }
                    binding.boxSkus.addView(b)
                    if (s.stock <= 0) b.isEnabled = false
                }
                // 默认选中第一个有货的规格，省一步点击
                default?.let { skuId ->
                    val idx = list.indexOfFirst { it.skuId == skuId }
                    if (idx >= 0) {
                        selectedSkuId = skuId
                        binding.boxSkus.getChildAt(idx)?.alpha = 1f
                        updatePriceWithDelta(list[idx].priceDelta)
                    }
                }
            } catch (_: Exception) {
                // 商品没有规格时保持原样，不影响购买
            }
        }
    }

    private fun selectSku(skuId: Long, priceDelta: Double, stock: Int) {
        selectedSkuId = skuId
        updatePriceWithDelta(priceDelta)
        binding.tvStock.text = if (stock > 0) "库存 $stock" else "缺货"
    }

    /** 选中规格后把价格刷新为 基础价 + 规格加价 */
    private fun updatePriceWithDelta(delta: Double) {
        val base = basePrice ?: return
        binding.tvPrice.text = String.format(java.util.Locale.US, "$%.2f", base + delta)
    }

    private fun addToCart() {
        binding.btnCart.isEnabled = false
        lifecycleScope.launch {
            try {
                val count = CartApi.add(productId, 1, selectedSkuId)
                CartApi.publish(count)
                // 加购后给一个「去购物车」动作，形成 加购 → 结算 的闭环
                Snackbar.make(binding.root, "已加入购物车（$count 件）", Snackbar.LENGTH_LONG)
                    .setAction("去购物车") {
                        startActivity(MainActivity.intentTo(this@ProductDetailActivity, MainActivity.TAB_CART))
                    }
                    .setActionTextColor(getColor(R.color.shine_mint))
                    .show()
            } catch (e: Exception) {
                Toast.makeText(this@ProductDetailActivity, "加购失败：${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                binding.btnCart.isEnabled = true
            }
        }
    }

    /** 立即购买：加购一件后直接进入原生结算页 */
    private fun buyNow() {
        binding.btnBuy.isEnabled = false
        lifecycleScope.launch {
            try {
                val count = CartApi.add(productId, 1, selectedSkuId)
                CartApi.publish(count)
                startActivity(Intent(this@ProductDetailActivity, CheckoutActivity::class.java))
            } catch (e: Exception) {
                Toast.makeText(this@ProductDetailActivity, "加购失败：${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                binding.btnBuy.isEnabled = true
            }
        }
    }
}
