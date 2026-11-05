package com.shine.xumis.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.shine.xumis.app.databinding.ActivityCatalogBinding
import com.shine.xumis.app.model.MobileProduct
import com.shine.xumis.app.ui.adapter.ProductAdapter
import com.shine.xumis.app.util.CatalogCache

/**
 * 离线商品册（原生）：把上一次成功拉取的商品快照用原生网格渲染。
 * 原来的实现是打开 assets 里的 catalog.html（WebView），现在不依赖任何网页。
 */
class CatalogActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCatalogBinding
    private val adapter = ProductAdapter { openDetail(it) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCatalogBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.topAppBar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.topAppBar.setNavigationOnClickListener { finish() }

        binding.rv.adapter = adapter

        val snap = CatalogCache.load(this)
        val items = snap?.items ?: emptyList()
        if (items.isEmpty()) {
            binding.tvEmpty.visibility = View.VISIBLE
            binding.tvHint.text = "离线商品册 · 暂无缓存"
        } else {
            adapter.submit(items)
            binding.tvEmpty.visibility = View.GONE
            binding.tvHint.text = "离线商品册 · 共 ${items.size} 件（缓存于 ${CatalogCache.savedAtText(this)}）"
        }
    }

    private fun openDetail(p: MobileProduct) {
        startActivity(
            Intent(this, ProductDetailActivity::class.java).putExtra("product_id", p.id ?: 0L)
        )
    }
}
