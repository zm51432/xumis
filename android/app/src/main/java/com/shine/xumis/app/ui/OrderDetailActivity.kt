package com.shine.xumis.app.ui

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.shine.xumis.app.api.CommerceApi
import com.shine.xumis.app.databinding.ActivityOrderDetailBinding
import kotlinx.coroutines.launch

/** 订单详情（原生）：订单号/状态/明细/收货信息/合计 */
class OrderDetailActivity : AppCompatActivity() {
    private lateinit var binding: ActivityOrderDetailBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOrderDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.topAppBar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.topAppBar.setNavigationOnClickListener { finish() }

        val orderNo = intent.getStringExtra("order_no")
        if (orderNo.isNullOrBlank()) { finish(); return }
        load(orderNo)
    }

    private fun load(orderNo: String) {
        binding.pb.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                val d = CommerceApi.orderDetail(orderNo)
                binding.tvOrderNo.text = "订单 ${d.orderNo}"
                binding.tvStatus.text = statusText(d.status)
                binding.tvCreated.text = d.createdAt
                binding.tvPay.text = "支付方式：" + (payLabel(d.paymentMethod) ?: "未选择")
                binding.tvItems.text = d.items.joinToString("\n") {
                    "${it.name ?: "商品"} × ${it.quantity}    ${usd(it.price)}"
                }
                binding.tvShip.text = buildString {
                    append(d.customerName ?: "—")
                    if (!d.phone.isNullOrBlank()) append("\n电话：${d.phone}")
                    if (!d.country.isNullOrBlank()) append("\n${d.country}")
                    if (!d.address.isNullOrBlank()) append("\n${d.address}")
                }
                if (!d.note.isNullOrBlank()) {
                    binding.tvNote.visibility = View.VISIBLE
                    binding.tvNote.text = "备注：${d.note}"
                }
                binding.tvTotal.text = "合计 " + d.currency + " " + usd(d.total)
            } catch (e: Exception) {
                Toast.makeText(this@OrderDetailActivity, "加载失败：${e.message}", Toast.LENGTH_SHORT).show()
                finish()
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

    private fun payLabel(s: String?): String? = when (s?.uppercase()) {
        "WECHAT" -> "微信"
        "ALIPAY" -> "支付宝"
        "MASTERCARD" -> "信用卡"
        "PAYPAL" -> "PayPal"
        else -> null
    }

    private fun usd(v: Double): String = "$" + String.format(java.util.Locale.US, "%.2f", v)
}
