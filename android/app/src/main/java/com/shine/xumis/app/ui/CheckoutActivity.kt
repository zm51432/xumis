package com.shine.xumis.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.shine.xumis.app.R
import com.shine.xumis.app.api.ApiClient
import com.shine.xumis.app.databinding.ActivityCheckoutBinding
import com.shine.xumis.app.model.CheckoutInfo
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * 原生结算页：收货信息 + 订单摘要 + 支付方式，直接在本页提交订单，不跳网页。
 *
 * 支付说明：当前站点未接入支付网关，提交后订单状态为「待付款」，
 * 由商户按所选渠道（PayPal / 微信 / 支付宝 / 信用卡）发送付款指引。
 */
class CheckoutActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCheckoutBinding
    private var info: CheckoutInfo? = null
    private var couponCode: String? = null
    private var quotedTotal: Double = 0.0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCheckoutBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.btnSubmit.setOnClickListener { submit() }
        binding.btnCoupon.setOnClickListener { checkCoupon() }
        load()
    }

    private fun load() {
        lifecycleScope.launch {
            try {
                val data = ApiClient.api().checkout()
                info = data
                if (!data.ok || data.items.isEmpty()) {
                    toast("购物车是空的，先去挑几件吧")
                    finish()
                    return@launch
                }
                render(data)
            } catch (e: Exception) {
                toast("加载失败：${e.message}")
                finish()
            }
        }
    }

    private fun render(data: CheckoutInfo) {
        val u = data.user
        if (u != null && u.loggedIn) {
            if (binding.etName.text.isNullOrBlank()) binding.etName.setText(u.name ?: "")
            if (binding.etEmail.text.isNullOrBlank()) binding.etEmail.setText(u.email ?: "")
            if (binding.etPhone.text.isNullOrBlank()) binding.etPhone.setText(u.phone ?: "")
            if (binding.etCountry.text.isNullOrBlank()) binding.etCountry.setText(u.country ?: "")
            if (binding.etAddress.text.isNullOrBlank()) binding.etAddress.setText(u.address ?: "")
        }

        val sb = StringBuilder()
        data.items.forEach { item ->
            val qty = item.quantity ?: 1
            val line = (item.price ?: 0.0) * qty
            sb.append("· ${item.name ?: item.nameEn ?: "商品"} × $qty")
                .append("    US$").append(String.format(Locale.US, "%.2f", line)).append("\n")
        }
        binding.tvItems.text = sb.toString().trimEnd()
        quotedTotal = data.total
        renderCharges(data.subtotal, data.discount, data.shippingFee, data.taxAmount, data.total)
    }

    private fun renderCharges(subtotal: Double, discount: Double, shipping: Double, tax: Double, total: Double) {
        val usd = { v: Double -> String.format(Locale.US, "US$%.2f", v) }
        binding.tvCharges.text = buildString {
            append("商品小计：").append(usd(subtotal)).append("\n")
            if (discount > 0.0) append("优惠折扣：-").append(usd(discount)).append("\n")
            append("运费：").append(usd(shipping)).append("   税费：").append(usd(tax))
        }
        binding.tvTotal.text = String.format(Locale.US, "合计 US$%.2f", total)
    }

    private fun checkCoupon() {
        val code = binding.etCoupon.text.toString().trim()
        if (code.isBlank()) { binding.tvCouponMsg.text = "请输入优惠码"; return }
        binding.btnCoupon.isEnabled = false
        lifecycleScope.launch {
            try {
                val q = ApiClient.api().couponCheck(code)
                couponCode = if (q.ok) code else null
                binding.tvCouponMsg.text = q.message ?: ""
                if (q.ok) {
                    quotedTotal = q.total
                    renderCharges(q.subtotal, q.discount, q.shippingFee, q.taxAmount, q.total)
                }
            } catch (e: Exception) {
                binding.tvCouponMsg.text = "校验失败：${e.message}"
            } finally {
                binding.btnCoupon.isEnabled = true
            }
        }
    }

    private fun selectedPay(): Pair<String, String> = when (binding.rgPay.checkedRadioButtonId) {
        R.id.rb_wechat -> "WECHAT" to "微信支付"
        R.id.rb_alipay -> "ALIPAY" to "支付宝"
        R.id.rb_card -> "MASTERCARD" to "信用卡 / Mastercard"
        else -> "PAYPAL" to "PayPal"
    }

    private fun submit() {
        val name = binding.etName.text.toString().trim()
        val email = binding.etEmail.text.toString().trim()
        val phone = binding.etPhone.text.toString().trim()
        val country = binding.etCountry.text.toString().trim()
        val address = binding.etAddress.text.toString().trim()
        val note = binding.etNote.text.toString().trim()

        if (name.isBlank()) { toast("请填写收货人姓名"); return }
        if (!email.contains("@")) { toast("请填写正确的邮箱"); return }
        if (country.isBlank()) { toast("请填写国家 / 地区"); return }
        if (address.isBlank()) { toast("请填写详细地址"); return }

        val (code, label) = selectedPay()
        binding.btnSubmit.isEnabled = false
        binding.tvProgress.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                val resp = ApiClient.api().placeOrder(
                    customerName = name, email = email, phone = phone,
                    country = country, address = address, note = note,
                    currency = info?.currency ?: "USD", paymentMethod = code,
                    couponCode = couponCode
                )
                binding.tvProgress.visibility = View.GONE
                binding.btnSubmit.isEnabled = true

                val body = resp.body()
                if (resp.isSuccessful && body?.ok == true) {
                    showSuccess(body.orderNo ?: "", body.total ?: quotedTotal, label)
                } else {
                    when (body?.error) {
                        "badInfo" -> toast("收货信息不完整，请检查")
                        "emptyCart" -> toast("购物车是空的")
                        "invalidCart" -> toast("购物车里的商品已失效，请重新选择")
                        "noStock" -> toast("商品已售罄，请调整后再试")
                        else -> toast("下单失败，请稍后再试")
                    }
                }
            } catch (e: Exception) {
                binding.tvProgress.visibility = View.GONE
                binding.btnSubmit.isEnabled = true
                toast("下单失败：${e.message}")
            }
        }
    }

    private fun showSuccess(orderNo: String, total: Double, payLabel: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle("下单成功")
            .setMessage(
                "订单号：$orderNo\n" +
                    "订单金额：US$" + String.format(Locale.US, "%.2f", total) + "\n" +
                    "支付方式：$payLabel\n\n" +
                    "请按邮件中的付款指引完成支付，我们收到款项后会尽快安排发货。"
            )
            .setPositiveButton("查看订单") { _, _ ->
                startActivity(
                    Intent(this, MainActivity::class.java)
                        .putExtra("tab", "account")
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                )
                finish()
            }
            .setNegativeButton("继续逛逛") { _, _ -> finish() }
            .setCancelable(false)
            .show()
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}
