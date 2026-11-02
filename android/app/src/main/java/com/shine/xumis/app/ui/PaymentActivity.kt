package com.shine.xumis.app.ui

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.shine.xumis.app.R
import com.shine.xumis.app.databinding.ActivityPaymentBinding
import com.shine.xumis.app.util.PaymentPrefs

/** 支付方式：选择下单时的默认支付渠道（本地偏好，结算页预选） */
class PaymentActivity : AppCompatActivity() {
    private lateinit var binding: ActivityPaymentBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPaymentBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.topAppBar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.topAppBar.setNavigationOnClickListener { finish() }

        // 预选已保存的默认方式
        when (PaymentPrefs.defaultMethod(this)) {
            "WECHAT" -> binding.rbWechat.isChecked = true
            "ALIPAY" -> binding.rbAlipay.isChecked = true
            "MASTERCARD" -> binding.rbCard.isChecked = true
            else -> binding.rbPaypal.isChecked = true
        }

        binding.btnSave.setOnClickListener { save() }
    }

    private fun save() {
        val method = when (binding.rgPay.checkedRadioButtonId) {
            R.id.rb_wechat -> "WECHAT"
            R.id.rb_alipay -> "ALIPAY"
            R.id.rb_card -> "MASTERCARD"
            else -> "PAYPAL"
        }
        PaymentPrefs.saveDefault(this, method)
        Toast.makeText(this, "已保存默认支付方式", Toast.LENGTH_SHORT).show()
        finish()
    }
}
