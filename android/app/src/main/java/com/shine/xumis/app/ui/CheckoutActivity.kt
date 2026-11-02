package com.shine.xumis.app.ui

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.shine.xumis.app.R
import com.shine.xumis.app.api.CartApi
import com.shine.xumis.app.api.CheckoutForm
import com.shine.xumis.app.api.CommerceApi
import com.shine.xumis.app.databinding.ActivityCheckoutBinding
import com.shine.xumis.app.util.PaymentPrefs
import kotlinx.coroutines.launch

/**
 * 原生结算：填收货信息 + 选支付方式 → 提交订单（POST /api/m/orders）→ 显示订单号。
 * 全程在 App 内完成，不再跳 Web。
 */
class CheckoutActivity : AppCompatActivity() {
    private lateinit var binding: ActivityCheckoutBinding
    private var submitting = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCheckoutBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.topAppBar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.topAppBar.setNavigationOnClickListener { finish() }

        binding.btnSubmit.setOnClickListener { submit() }
        binding.btnDone.setOnClickListener { finish() }

        load()
    }

    private fun load() {
        lifecycleScope.launch {
            try {
                // 合计金额（用当前购物车）
                val cart = CommerceApi.cart()
                binding.tvSummary.text = "共 ${cart.count} 件，合计 $" +
                    String.format(java.util.Locale.US, "%.2f", cart.total)
                if (cart.isEmpty) {
                    Toast.makeText(this@CheckoutActivity, "购物车是空的", Toast.LENGTH_SHORT).show()
                    finish()
                    return@launch
                }
                // 已登录则预填收货资料
                val me = CommerceApi.me()
                val u = me.user
                if (me.loggedIn && u != null) {
                    binding.etName.setText(u.name ?: "")
                    binding.etEmail.setText(u.email)
                    binding.etPhone.setText(u.phone ?: "")
                    binding.etCountry.setText(u.country ?: "")
                    binding.etAddress.setText(u.address ?: "")
                }
                // 预选默认支付方式
                when (PaymentPrefs.defaultMethod(this@CheckoutActivity)) {
                    "WECHAT" -> binding.rbWechat.isChecked = true
                    "ALIPAY" -> binding.rbAlipay.isChecked = true
                    "MASTERCARD" -> binding.rbCard.isChecked = true
                }
            } catch (e: Exception) {
                Toast.makeText(this@CheckoutActivity, "加载失败：${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun submit() {
        if (submitting) return
        val name = binding.etName.text.toString().trim()
        val email = binding.etEmail.text.toString().trim()
        if (name.isBlank()) { binding.etName.error = "请填写收货人姓名"; return }
        if (!email.contains("@")) { binding.etEmail.error = "请填写正确的邮箱"; return }

        val pay = when (binding.rgPay.checkedRadioButtonId) {
            R.id.rb_wechat -> "WECHAT"
            R.id.rb_alipay -> "ALIPAY"
            R.id.rb_card -> "MASTERCARD"
            else -> "PAYPAL"
        }
        val payLabel = when (pay) {
            "WECHAT" -> "微信"
            "ALIPAY" -> "支付宝"
            "MASTERCARD" -> "信用卡"
            else -> "PayPal"
        }

        submitting = true
        binding.btnSubmit.isEnabled = false
        binding.pb.visibility = View.VISIBLE
        val form = CheckoutForm(
            customerName = name,
            email = email,
            phone = binding.etPhone.text.toString().trim().takeIf { it.isNotBlank() },
            country = binding.etCountry.text.toString().trim().takeIf { it.isNotBlank() },
            address = binding.etAddress.text.toString().trim().takeIf { it.isNotBlank() },
            note = binding.etNote.text.toString().trim().takeIf { it.isNotBlank() },
            currency = "USD",
            paymentMethod = pay
        )
        lifecycleScope.launch {
            try {
                val r = CommerceApi.placeOrder(form)
                CartApi.publish(0)
                showSuccess(r.orderNo, payLabel)
            } catch (e: Exception) {
                Toast.makeText(this@CheckoutActivity, "下单失败：${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                submitting = false
                binding.btnSubmit.isEnabled = true
                binding.pb.visibility = View.GONE
            }
        }
    }

    private fun showSuccess(orderNo: String, payLabel: String) {
        binding.formScroll.visibility = View.GONE
        binding.successLayout.visibility = View.VISIBLE
        binding.tvOrderNo.text = "订单号：$orderNo\n付款方式：$payLabel"
    }
}
