package com.shine.xumis.app.ui.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.shine.xumis.app.R
import com.shine.xumis.app.databinding.HeaderHomeBinding
import com.shine.xumis.app.model.CategoryItem

/**
 * 首页列表头部：品牌横幅 + 分类快捷筛选 + 区块标题。
 * 作为 ConcatAdapter 的第一段，随商品网格一起滚动，下拉刷新仍只作用于顶部。
 */
class HomeHeaderAdapter(
    private val onFeatured: () -> Unit,
    private val onCategory: (String?) -> Unit
) : RecyclerView.Adapter<HomeHeaderAdapter.VH>() {

    private var categories: List<CategoryItem> = emptyList()
    private var selected: String? = null
    private var countText: String = ""

    fun setCategories(list: List<CategoryItem>) {
        categories = list
        notifyItemChanged(0)
    }

    fun setSelected(code: String?) {
        selected = code
        notifyItemChanged(0)
    }

    fun setCount(text: String) {
        countText = text
        if (itemCount > 0) notifyItemChanged(0)
    }

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val banner: View = view.findViewById(R.id.banner)
        val btnFeatured: View = view.findViewById(R.id.btn_featured)
        val chipRow: LinearLayout = view.findViewById(R.id.chip_row)
        val tvCount: TextView = view.findViewById(R.id.tv_count)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = HeaderHomeBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b.root)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.banner.setOnClickListener { onFeatured() }
        holder.btnFeatured.setOnClickListener { onFeatured() }
        holder.tvCount.text = countText
        buildChips(holder)
    }

    private fun buildChips(holder: VH) {
        val ctx = holder.chipRow.context
        holder.chipRow.removeAllViews()
        val list = ArrayList<CategoryItem>(categories.size + 1)
        list.add(CategoryItem(null, "全部"))
        list.addAll(categories)
        for (c in list) {
            val tv = TextView(ctx)
            tv.text = c.label ?: c.code ?: "全部"
            tv.textSize = 12.5f
            tv.setPadding(dp(ctx, 14), dp(ctx, 7), dp(ctx, 14), dp(ctx, 7))
            val active = c.code == selected
            tv.setTextColor(ContextCompat.getColor(ctx, if (active) R.color.shine_on_red else R.color.shine_pink))
            tv.setBackgroundResource(if (active) R.drawable.bg_badge_hot else R.drawable.bg_badge_soft)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.rightMargin = dp(ctx, 8)
            tv.layoutParams = lp
            tv.setOnClickListener { onCategory(c.code) }
            holder.chipRow.addView(tv)
        }
    }

    private fun dp(ctx: Context, v: Int): Int =
        (v * ctx.resources.displayMetrics.density).toInt()

    override fun getItemCount(): Int = 1
}
