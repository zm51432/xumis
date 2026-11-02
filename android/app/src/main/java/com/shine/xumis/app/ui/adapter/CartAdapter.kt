package com.shine.xumis.app.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.shine.xumis.app.R
import com.shine.xumis.app.api.CartLine

class CartAdapter(
    private val onQtyChange: (CartLine, Int) -> Unit,
    private val onRemove: (CartLine) -> Unit
) : RecyclerView.Adapter<CartAdapter.VH>() {

    private val items = mutableListOf<CartLine>()
    /** 正在操作（改数量/删除）的商品 id，按钮禁用避免手快连点 */
    private val busy = mutableSetOf<Long>()

    fun submit(list: List<CartLine>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    fun setBusy(id: Long, b: Boolean) {
        val changed = if (b) busy.add(id) else busy.remove(id)
        if (!changed) return
        val pos = items.indexOfFirst { it.id == id }
        if (pos >= 0) notifyItemChanged(pos)
    }

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val img: ImageView = view.findViewById(R.id.img)
        val name: TextView = view.findViewById(R.id.name)
        val price: TextView = view.findViewById(R.id.price)
        val lineTotal: TextView = view.findViewById(R.id.line_total)
        val qty: TextView = view.findViewById(R.id.qty)
        val btnMinus: ImageButton = view.findViewById(R.id.btn_minus)
        val btnPlus: ImageButton = view.findViewById(R.id.btn_plus)
        val btnRemove: ImageButton = view.findViewById(R.id.btn_remove)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_cart, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val line = items[position]
        holder.name.text = line.name
        holder.price.text = usd(line.price)
        holder.lineTotal.text = "小计 " + usd(line.lineTotal)
        holder.qty.text = line.qty.toString()

        if (line.imageUrl != null) {
            Glide.with(holder.img.context).load(line.imageUrl)
                .placeholder(R.drawable.ic_placeholder).error(R.drawable.ic_placeholder)
                .into(holder.img)
        } else {
            holder.img.setImageResource(R.drawable.ic_placeholder)
        }

        val isBusy = busy.contains(line.id)
        holder.btnMinus.isEnabled = !isBusy && line.qty > 1
        holder.btnPlus.isEnabled = !isBusy
        holder.btnRemove.isEnabled = !isBusy
        holder.btnMinus.alpha = if (isBusy) 0.4f else 1f
        holder.btnPlus.alpha = if (isBusy) 0.4f else 1f

        holder.btnMinus.setOnClickListener { onQtyChange(line, line.qty - 1) }
        holder.btnPlus.setOnClickListener { onQtyChange(line, line.qty + 1) }
        holder.btnRemove.setOnClickListener { onRemove(line) }
    }

    override fun getItemCount(): Int = items.size

    private fun usd(v: Double): String = "$" + String.format(java.util.Locale.US, "%.2f", v)
}
