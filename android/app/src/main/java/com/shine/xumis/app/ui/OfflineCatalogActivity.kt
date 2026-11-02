package com.shine.xumis.app.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.shine.xumis.app.databinding.ActivityOfflineCatalogBinding
import com.shine.xumis.app.model.MobileProduct
import com.shine.xumis.app.ui.adapter.ProductAdapter
import com.shine.xumis.app.util.CatalogCache

/** 离线商品册（原生）：展示最近一次缓存的商品列表，不再用网页 */
class OfflineCatalogActivity : AppCompatActivity() {
    private lateinit var binding: ActivityOfflineCatalogBinding
    private val adapter = ProductAdapter(onClick = { openDetail(it) })

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOfflineCatalogBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.topAppBar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.topAppBar.setNavigationOnClickListener { finish() }

        binding.rv.adapter = adapter
        val items = CatalogCache.load(this)?.items.orEmpty()
        if (items.isEmpty()) {
            binding.emptyState.visibility = android.view.View.VISIBLE
        } else {
            binding.emptyState.visibility = android.view.View.GONE
            adapter.submit(items)
        }
    }

    private fun openDetail(p: MobileProduct) {
        startActivity(Intent(this, ProductDetailActivity::class.java).putExtra("product_id", p.id ?: 0L))
    }
}
