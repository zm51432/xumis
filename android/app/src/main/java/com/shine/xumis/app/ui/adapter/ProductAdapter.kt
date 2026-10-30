package com.shine.xumis.app.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.shine.xumis.app.R
import com.shine.xumis.app.model.MobileProduct

class ProductAdapter(
    private val onClick: (MobileProduct) -> Unit,
    private val onAddCart: (MobileProduct) -> Unit
) : RecyclerView.Adapter<ProductAdapter.VH>() {

    private val items = mutableListOf<MobileProduct>()
    /** 正在加购的商品 id 集合：按钮禁用 + 半透明，避免手快点两次加两件 */
    private val adding = mutableSetOf<Long>()

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

    fun all(): List<MobileProduct> = items.toList()

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val img: ImageView = view.findViewById(R.id.item_img)
        val name: TextView = view.findViewById(R.id.item_name)
        val price: TextView = view.findViewById(R.id.item_price)
        val cat: TextView = view.findViewById(R.id.item_cat)
        val badge: TextView = view.findViewById(R.id.item_badge)
        val stock: TextView = view.findViewById(R.id.item_stock)
        val sold: TextView = view.findViewById(R.id.item_sold)
        val add: ImageButton = view.findViewById(R.id.item_add)
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

        holder.badge.visibility = if (p.featured == true) View.VISIBLE else View.GONE

        // 缺货标在价格右侧，比原来只在详情页提示更早一步
        val stockText = p.stockText()
        holder.stock.text = stockText
        holder.stock.visibility = if (stockText.isBlank()) View.GONE else View.VISIBLE

        // 已售（热销榜或商品字段）
        val soldText = p.soldText()
        holder.sold.text = soldText
        holder.sold.visibility = if (soldText == null) View.GONE else View.VISIBLE

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

        // 加购按钮：缺货时禁用
        val id = p.id ?: -1L
        val isAdding = adding.contains(id)
        holder.add.isEnabled = p.inStock != false && !isAdding
        holder.add.alpha = if (p.inStock == false) 0.35f else (if (isAdding) 0.5f else 1f)
        holder.add.setOnClickListener {
            if (p.inStock == false) return@setOnClickListener
            onAddCart(p)
        }

        holder.itemView.setOnClickListener { onClick(p) }
    }

    override fun getItemCount(): Int = items.size

    /** 加购状态切换（按钮禁用 + 半透明，避免手快点两次加两件） */
    fun setAdding(id: Long?, addingNow: Boolean) {
        val key = id ?: return
        val changed = if (addingNow) adding.add(key) else adding.remove(key)
        if (!changed) return
        val pos = items.indexOfFirst { it.id == key }
        if (pos >= 0) notifyItemChanged(pos)
    }
}
