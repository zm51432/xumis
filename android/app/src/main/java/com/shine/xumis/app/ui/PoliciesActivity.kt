package com.shine.xumis.app.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.shine.xumis.app.databinding.ActivityPoliciesBinding

/** 配送与售后条款（原生）：随包内置的静态文本，不再用网页 */
class PoliciesActivity : AppCompatActivity() {
    private lateinit var binding: ActivityPoliciesBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPoliciesBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.topAppBar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.topAppBar.setNavigationOnClickListener { finish() }
        binding.tvContent.text = POLICIES
    }

    companion object {
        private val POLICIES = """
            一、发货与时效
            订单确认后 1–3 个工作日内发货；定制类商品需按沟通的工期另行排期。发货后我们会通过邮件或站内通知提供物流单号。

            二、跨境运费与关税
            满额订单享受包邮优惠，未满额按重量/地区计费，结算时显示。目的国可能产生的进口关税由买家承担，请在购买前了解当地政策。

            三、支付方式
            支持 PayPal、微信支付、支付宝、信用卡/借记卡。下单后请在约定时间内完成付款，逾期订单可能被取消。

            四、退换货
            收到商品 7 天内，如属质量问题或发错货，可申请退换；定制类商品除质量问题外不支持退换。请保留原始包装与购买凭证。

            五、售后联系
            请通过「我的 → 询盘联系」或订单详情页与我们联系，我们会尽快回复。
        """.trimIndent()
    }
}
