package com.shine.xumis.app.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.shine.xumis.app.R
import com.shine.xumis.app.databinding.ItemCartBinding
import com.shine.xumis.app.model.CartItem
import com.shine.xumis.app.util.ServerConfig
import java.util.Locale

/** 原生购物车列表：图片 + 名称 + 单价 + 数量增减 + 删除 */
class CartAdapter(
    private val onQtyChange: (CartItem, Int) -> Unit,
    private val onRemove: (CartItem) -> Unit
) : RecyclerView.Adapter<CartAdapter.VH>() {

    private val items = mutableListOf<CartItem>()

    fun submit(list: List<CartItem>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    class VH(val binding: ItemCartBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemCartBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        val b = holder.binding
        b.itemName.text = item.name ?: item.nameEn ?: "商品"
        b.itemPrice.text = String.format(Locale.US, "US$%.2f", item.price ?: 0.0)
        b.tvQty.text = (item.quantity ?: 1).toString()

        val url = fullImageUrl(item.imageUrl)
        if (url != null) {
            Glide.with(b.itemImg.context)
                .load(url)
                .placeholder(R.drawable.ic_placeholder)
                .error(R.drawable.ic_placeholder)
                .thumbnail(0.2f)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .into(b.itemImg)
        } else {
            b.itemImg.setImageResource(R.drawable.ic_placeholder)
        }

        val qty = item.quantity ?: 1
        val maxQty = (item.stock ?: 99).coerceAtLeast(1)
        b.btnMinus.setOnClickListener { onQtyChange(item, qty - 1) }
        b.btnPlus.setOnClickListener {
            if (qty >= maxQty) {
                onQtyChange(item, qty)
            } else {
                onQtyChange(item, qty + 1)
            }
        }
        b.btnMinus.alpha = if (qty <= 1) 0.35f else 1f
        b.btnPlus.alpha = if (qty >= maxQty) 0.35f else 1f
        b.btnRemove.setOnClickListener { onRemove(item) }
    }

    override fun getItemCount(): Int = items.size

    private fun fullImageUrl(path: String?): String? {
        if (path.isNullOrBlank()) return null
        return if (path.startsWith("/")) ServerConfig.siteBase() + path.removePrefix("/") else path
    }
}
