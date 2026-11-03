package com.shine.xumis.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.shine.xumis.app.api.CommerceApi
import com.shine.xumis.app.databinding.ActivityOrdersBinding
import com.shine.xumis.app.databinding.ItemOrderBinding
import com.shine.xumis.app.util.AppPrefs
import kotlinx.coroutines.launch

/** 我的订单：读取 /api/m/orders，按时间倒序展示订单卡片 */
class OrdersActivity : AppCompatActivity() {
    private lateinit var binding: ActivityOrdersBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppPrefs.init(applicationContext)
        binding = ActivityOrdersBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.topAppBar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.topAppBar.setNavigationOnClickListener { finish() }
        binding.btnGoShopping.setOnClickListener {
            startActivity(MainActivity.intentTo(this, MainActivity.TAB_HOME))
        }
        load()
    }

    private fun load() {
        binding.pb.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                // 登录态由后端按账号归并；游客带上下单时留下的邮箱，才能看到自己的订单
                val orders = CommerceApi.myOrders(AppPrefs.lastOrderEmail)
                binding.ordersContainer.removeAllViews()
                if (orders.isEmpty()) {
                    binding.tvEmpty.visibility = View.VISIBLE
                    binding.btnGoShopping.visibility = View.VISIBLE
                } else {
                    binding.tvEmpty.visibility = View.GONE
                    binding.btnGoShopping.visibility = View.GONE
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
                        card.root.setOnClickListener {
                            startActivity(Intent(this@OrdersActivity, OrderDetailActivity::class.java)
                                .putExtra("order_no", o.orderNo))
                        }
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
