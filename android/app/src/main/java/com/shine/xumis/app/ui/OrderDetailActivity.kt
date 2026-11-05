package com.shine.xumis.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.shine.xumis.app.R
import com.shine.xumis.app.api.ApiClient
import com.shine.xumis.app.databinding.ActivityOrderDetailBinding
import com.shine.xumis.app.model.OrderDetail
import com.shine.xumis.app.model.OrderLine
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * 账单 / 订单详情（原生）。
 * 点「我的」里的订单不再弹网页，改为这里直接展示：状态、支付方式、金额、商品明细、收货信息。
 */
class OrderDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOrderDetailBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOrderDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.topAppBar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.topAppBar.setNavigationOnClickListener { finish() }

        val orderNo = intent.getStringExtra("order_no").orEmpty()
        if (orderNo.isBlank()) {
            showError("缺少订单号，无法打开账单")
            return
        }
        load(orderNo)
    }

    private fun load(orderNo: String) {
        binding.tvProgress.visibility = View.VISIBLE
        binding.tvError.visibility = View.GONE
        lifecycleScope.launch {
            try {
                val resp = ApiClient.api().orderDetail(orderNo)
                binding.tvProgress.visibility = View.GONE
                val d = resp.body()
                if (resp.isSuccessful && d?.ok == true) {
                    render(d)
                } else {
                    when (d?.error) {
                        "notLoggedIn" -> showError("登录状态已失效，请回到「我的」重新登录")
                        "notFound" -> showError("这笔订单不存在或已被删除")
                        "forbidden" -> showError("这笔订单不属于当前登录账号")
                        else -> showError("读取账单失败，请稍后重试")
                    }
                }
            } catch (e: Exception) {
                binding.tvProgress.visibility = View.GONE
                showError("网络异常：${e.message}")
            }
        }
    }

    private fun render(d: OrderDetail) {
        binding.boxSummary.visibility = View.VISIBLE
        binding.boxItemsPanel.visibility = View.VISIBLE
        binding.boxShipping.visibility = View.VISIBLE

        binding.tvOrderNo.text = d.orderNo ?: "—"
        binding.tvStatus.text = statusText(d.status)
        binding.tvStatus.setBackgroundResource(statusBg(d.status))
        binding.tvTime.text = formatTime(d.createdAt)
        binding.tvPay.text = "支付方式：" + payText(d.paymentMethod)
        binding.tvTotal.text = money(d.totalAmount, d.currency)
        renderTracking(d)

        binding.boxItems.removeAllViews()
        val items = d.items ?: emptyList()
        items.forEach { binding.boxItems.addView(itemRow(it, d.currency)) }
        if (items.isEmpty()) {
            binding.boxItems.addView(grayText("（这笔订单没有商品明细）"))
        }

        binding.tvReceiver.text = buildString {
            append("收货人：").append(d.customerName?.ifBlank { "—" } ?: "—").append("\n")
            append("邮箱：").append(d.email?.ifBlank { "—" } ?: "—").append("\n")
            append("电话：").append(d.phone?.ifBlank { "—" } ?: "—").append("\n")
            append("国家/地区：").append(d.country?.ifBlank { "—" } ?: "—").append("\n")
            append("地址：").append(d.address?.ifBlank { "—" } ?: "—")
        }
        val note = d.note?.trim().orEmpty()
        binding.tvNote.visibility = if (note.isEmpty()) View.GONE else View.VISIBLE
        binding.tvNote.text = "备注：$note"
    }

    /** 阶段3：物流单号 + 轨迹时间线（原生展示，不再跳网页） */
    private fun renderTracking(d: OrderDetail) {
        val hasTrack = !d.trackingNo.isNullOrBlank() || !d.trackingEvents.isNullOrEmpty()
        binding.boxTracking.visibility = if (hasTrack) View.VISIBLE else View.GONE
        if (!hasTrack) return
        binding.tvTracking.text = buildString {
            if (!d.carrier.isNullOrBlank()) append("承运商：").append(d.carrier).append("\n")
            if (!d.trackingNo.isNullOrBlank()) append("运单号：").append(d.trackingNo).append("\n")
            if (!d.shippedAt.isNullOrBlank()) append("发货时间：").append(d.shippedAt.replace("T", " ").take(16))
        }.trimEnd().ifBlank { "—" }
        binding.boxTrackingEvents.removeAllViews()
        val events = d.trackingEvents ?: emptyList()
        if (events.isEmpty()) {
            binding.boxTrackingEvents.addView(grayText("（暂无轨迹节点）"))
            return
        }
        events.forEach { ev ->
            val tv = TextView(this).apply {
                val t = (ev.eventTime ?: "").replace("T", " ").take(16)
                text = "● $t  ${ev.location ?: ""}\n   ${ev.description ?: ""}"
                textSize = 12.5f
                setTextColor(getColor(R.color.shine_text))
                setPadding(0, 8, 0, 8)
            }
            binding.boxTrackingEvents.addView(tv)
        }
    }

    private fun itemRow(line: OrderLine, currency: String?): View {
        val price = line.price ?: 0.0
        val qty = line.quantity ?: 1
        val row = TextView(this)
        val sku = line.skuLabel?.let { "  [$it]" } ?: ""
        row.text = "${line.name ?: "商品"}$sku  ·  ${money(price, currency)} × $qty  =  ${money(price * qty, currency)}"
        row.textSize = 13f
        row.setTextColor(getColor(R.color.shine_text))
        row.setPadding(0, 10, 0, 10)
        row.setOnClickListener {
            val pid = line.productId
            if (pid != null && pid > 0) {
                startActivity(Intent(this, ProductDetailActivity::class.java).putExtra("product_id", pid))
            }
        }
        return row
    }

    private fun grayText(s: String): TextView = TextView(this).apply {
        text = s
        textSize = 12.5f
        setTextColor(getColor(R.color.shine_sub))
        setPadding(0, 10, 0, 10)
    }

    private fun showError(msg: String) {
        binding.tvProgress.visibility = View.GONE
        binding.tvError.text = msg
        binding.tvError.visibility = View.VISIBLE
    }

    private fun money(v: Double?, currency: String?): String {
        val symbol = if (currency.equals("CNY", true)) "¥" else "US$"
        return String.format(Locale.US, "%s%.2f", symbol, v ?: 0.0)
    }

    /** 后端时间是 ISO 格式（2026-11-02T18:46:28），展示成 2026-11-02 18:46 */
    private fun formatTime(raw: String?): String {
        if (raw.isNullOrBlank()) return "—"
        return try {
            val t = raw.replace("T", " ")
            if (t.length >= 16) "下单时间：" + t.substring(0, 16) else "下单时间：$t"
        } catch (e: Exception) {
            "下单时间：$raw"
        }
    }

    private fun statusText(s: String?): String = when (s?.uppercase()) {
        "PENDING" -> "待支付"
        "PAID" -> "已支付"
        "SHIPPED" -> "已发货"
        "DONE" -> "已完成"
        "CANCELLED" -> "已取消"
        else -> s ?: "—"
    }

    private fun statusBg(s: String?): Int = when (s?.uppercase()) {
        "PAID", "SHIPPED", "DONE" -> R.drawable.bg_chip_yellow
        else -> R.drawable.bg_chip_red
    }

    private fun payText(s: String?): String = when (s?.uppercase()) {
        "PAYPAL" -> "PayPal"
        "WECHAT" -> "微信支付"
        "ALIPAY" -> "支付宝"
        "MASTERCARD" -> "信用卡 / Mastercard"
        else -> s ?: "—"
    }
}
