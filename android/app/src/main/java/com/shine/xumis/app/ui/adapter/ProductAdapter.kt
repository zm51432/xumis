package com.shine.xumis.app.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.shine.xumis.app.R
import com.shine.xumis.app.model.MobileProduct

/**
 * 商品卡片适配器：只负责「浏览 → 点击进详情」，不在卡片上加购。
 * 加购 / 立即购买统一在商品详情页完成（用户要求：点图片才进购物车或购买选项）。
 */
class ProductAdapter(
    private val onClick: (MobileProduct) -> Unit
) : RecyclerView.Adapter<ProductAdapter.VH>() {

    /** 销量达到该值视为「热销」，卡片左上角显示红底角标 */
    private companion object {
        const val HOT_THRESHOLD = 20
    }

    private val items = mutableListOf<MobileProduct>()

    /** 首次加载 / 下拉刷新：整体替换 */
    fun submit(list: List<MobileProduct>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    /** 翻页加载：追加到末尾，只刷新新增部分，避免整屏闪一下 */
    fun append(list: List<MobileProduct>) {
        if (list.isEmpty()) return
        val start = items.size
        items.addAll(list)
        notifyItemRangeInserted(start, list.size)
    }

    fun size(): Int = items.size

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val img: ImageView = view.findViewById(R.id.item_img)
        val name: TextView = view.findViewById(R.id.item_name)
        val price: TextView = view.findViewById(R.id.item_price)
        val cat: TextView = view.findViewById(R.id.item_cat)
        val badge: TextView = view.findViewById(R.id.item_badge)
        val stock: TextView = view.findViewById(R.id.item_stock)
        val sold: TextView = view.findViewById(R.id.item_sold)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_product, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val p = items[position]
        holder.name.text = p.title()
        holder.price.text = p.priceText()

        val cat = p.category?.takeIf { it.isNotBlank() }
        holder.cat.text = cat ?: ""
        holder.cat.visibility = if (cat == null) View.GONE else View.VISIBLE

        // 角标：销量高显示红底「热销」，否则若是精选显示淡红「精选」，都没有就隐藏
        val sales = p.sales ?: 0
        when {
            sales >= HOT_THRESHOLD -> {
                holder.badge.visibility = View.VISIBLE
                holder.badge.text = "热销"
                holder.badge.setBackgroundResource(R.drawable.bg_badge_hot)
                holder.badge.setTextColor(ContextCompat.getColor(holder.badge.context, android.R.color.white))
            }
            p.featured == true -> {
                holder.badge.visibility = View.VISIBLE
                holder.badge.text = "精选"
                holder.badge.setBackgroundResource(R.drawable.bg_badge_soft)
                holder.badge.setTextColor(ContextCompat.getColor(holder.badge.context, R.color.shine_pink))
            }
            else -> holder.badge.visibility = View.GONE
        }

        // 缺货标在价格右侧，比原来只在详情页提示更早一步
        val stockText = p.stockText()
        holder.stock.text = stockText
        holder.stock.visibility = if (stockText.isBlank()) View.GONE else View.VISIBLE

        // 已售（热销榜或商品字段）：做成柔和的奶油小药丸
        val soldText = p.soldText()
        holder.sold.text = soldText
        if (soldText == null) {
            holder.sold.visibility = View.GONE
        } else {
            holder.sold.visibility = View.VISIBLE
            holder.sold.setBackgroundResource(R.drawable.bg_sold)
        }

        val imgUrl = p.fullImageUrl()
        if (imgUrl != null) {
            Glide.with(holder.img.context)
                .load(imgUrl)
                .placeholder(R.drawable.ic_placeholder)
                .error(R.drawable.ic_placeholder)
                .thumbnail(0.2f)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .into(holder.img)
        } else {
            holder.img.setImageResource(R.drawable.ic_placeholder)
        }

        // 整卡（含图片）点击进详情，加购 / 购买在详情页完成
        holder.itemView.setOnClickListener { onClick(p) }
    }

    override fun getItemCount(): Int = items.size
}
