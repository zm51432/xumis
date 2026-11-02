package com.shine.xumis.app.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.shine.xumis.app.api.CommerceApi
import com.shine.xumis.app.databinding.ActivityOrdersBinding
import com.shine.xumis.app.databinding.ItemOrderBinding
import kotlinx.coroutines.launch

/** 我的订单：读取 /api/m/orders，按时间倒序展示订单卡片 */
class OrdersActivity : AppCompatActivity() {
    private lateinit var binding: ActivityOrdersBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOrdersBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.topAppBar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.topAppBar.setNavigationOnClickListener { finish() }
        load()
    }

    private fun load() {
        binding.pb.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                val orders = CommerceApi.myOrders()
                binding.ordersContainer.removeAllViews()
                if (orders.isEmpty()) {
                    binding.tvEmpty.visibility = View.VISIBLE
                } else {
                    binding.tvEmpty.visibility = View.GONE
                    val inflater = LayoutInflater.from(this@OrdersActivity)
                    for (o in orders) {
                        val card = ItemOrderBinding.inflate(inflater, binding.ordersContainer, false)
                        card.orderNo.text = "订单 ${o.orderNo}"
                        card.status.text = statusText(o.status)
                        card.date.text = o.createdAt
                        card.items.text = o.items.joinToString("\n") {
                            "${it.name ?: "商品"} × ${it.quantity}"
                        }
                        card.total.text = "合计 " + o.currency + " " +
                            String.format(java.util.Locale.US, "%.2f", o.total)
                        binding.ordersContainer.addView(card.root)
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(this@OrdersActivity, "加载失败：${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                binding.pb.visibility = View.GONE
            }
        }
    }

    private fun statusText(s: String): String = when (s.uppercase()) {
        "PENDING" -> "待处理"
        "PAID" -> "已付款"
        "SHIPPED" -> "已发货"
        "DONE" -> "已完成"
        "CANCELLED" -> "已取消"
        else -> s
    }
}
