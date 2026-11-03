package com.shine.xumis.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.shine.xumis.app.api.ApiClient
import com.shine.xumis.app.api.CartApi
import com.shine.xumis.app.api.CommerceApi
import com.shine.xumis.app.api.OrderDetail
import com.shine.xumis.app.databinding.ActivityOrderDetailBinding
import kotlinx.coroutines.launch

/** 订单详情（原生）：订单号/状态/明细/收货信息/合计 + 再次购买 / 联系客服 */
class OrderDetailActivity : AppCompatActivity() {
    private lateinit var binding: ActivityOrderDetailBinding
    private var detail: OrderDetail? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOrderDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.topAppBar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.topAppBar.setNavigationOnClickListener { finish() }

        binding.tvOrderNo.setOnClickListener { copyOrderNo() }
        binding.btnContact.setOnClickListener { contact() }
        binding.btnRebuy.setOnClickListener { rebuy() }

        val orderNo = intent.getStringExtra("order_no")
        if (orderNo.isNullOrBlank()) { finish(); return }
        load(orderNo)
    }

    private fun load(orderNo: String) {
        binding.pb.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                val d = CommerceApi.orderDetail(orderNo)
                detail = d
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

    /** 点击订单号复制：客服沟通时直接粘贴，不用手抄 */
    private fun copyOrderNo() {
        val no = detail?.orderNo ?: return
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        cm.setPrimaryClip(ClipData.newPlainText("订单号", no))
        Toast.makeText(this, "订单号已复制", Toast.LENGTH_SHORT).show()
    }

    /** 联系客服：带着订单号进咨询页，商家能直接定位这一单 */
    private fun contact() {
        val d = detail ?: return
        startActivity(
            Intent(this, InquiryActivity::class.java)
                .putExtra("prefill_name", d.customerName ?: "")
                .putExtra("prefill_email", d.email ?: "")
                .putExtra("prefill_subject", "订单咨询（${d.orderNo}）")
                .putExtra("prefill_message", "关于订单 ${d.orderNo} 的咨询（取消 / 售后 / 其他）：\n")
        )
    }

    /**
     * 再次购买：订单明细只存了商品名，这里回查当前目录按名字匹配，
     * 命中的商品重新加购并直达购物车；全部下架则给出明确提示。
     */
    private fun rebuy() {
        val d = detail ?: return
        binding.btnRebuy.isEnabled = false
        lifecycleScope.launch {
            try {
                val products = ApiClient.api().products(0, 50).content ?: emptyList()
                val byName = products.associateBy { it.name }
                var added = 0
                var lastCount = 0
                for (item in d.items) {
                    val p = byName[item.name] ?: continue
                    val id = p.id ?: continue
                    lastCount = CommerceApi.add(id, item.quantity.coerceAtLeast(1))
                    added++
                }
                if (added == 0) {
                    Toast.makeText(this@OrderDetailActivity, "商品已下架，暂时无法再次购买", Toast.LENGTH_LONG).show()
                    return@launch
                }
                CartApi.publish(lastCount)
                Toast.makeText(this@OrderDetailActivity, "已加入购物车", Toast.LENGTH_SHORT).show()
                startActivity(MainActivity.intentTo(this@OrderDetailActivity, MainActivity.TAB_CART))
            } catch (e: Exception) {
                Toast.makeText(this@OrderDetailActivity, "再次购买失败：${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                binding.btnRebuy.isEnabled = true
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
