package com.shine.xumis.app.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.shine.xumis.app.R
import com.shine.xumis.app.model.MobileProduct

class ProductAdapter(
    private val onClick: (MobileProduct) -> Unit
) : RecyclerView.Adapter<ProductAdapter.VH>() {

    private val items = mutableListOf<MobileProduct>()

    fun submit(list: List<MobileProduct>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val img: ImageView = view.findViewById(R.id.item_img)
        val name: TextView = view.findViewById(R.id.item_name)
        val price: TextView = view.findViewById(R.id.item_price)
        val cat: TextView = view.findViewById(R.id.item_cat)
        val badge: TextView = view.findViewById(R.id.item_badge)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_product, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val p = items[position]
        holder.name.text = p.title()
        holder.price.text = p.priceText()
        holder.cat.text = p.category ?: ""
        if (p.featured == true) {
            holder.badge.visibility = View.VISIBLE
        } else {
            holder.badge.visibility = View.GONE
        }
        val imgUrl = p.fullImageUrl()
        if (imgUrl != null) {
            Glide.with(holder.img.context).load(imgUrl).placeholder(R.drawable.ic_placeholder).into(holder.img)
        } else {
            holder.img.setImageResource(R.drawable.ic_placeholder)
        }
        holder.itemView.setOnClickListener { onClick(p) }
    }

    override fun getItemCount(): Int = items.size
}
