package com.shine.xumis.app.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.shine.xumis.app.databinding.ActivityDocBinding

/**
 * 原生文字页：配送与售后条款、隐私说明等。
 * 内容内置在 App 里，断网可读，也不再调用任何网页地址。
 */
class DocActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityDocBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.topAppBar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.topAppBar.setNavigationOnClickListener { finish() }

        val title = intent.getStringExtra(EXTRA_TITLE) ?: "条款"
        val body = intent.getStringExtra(EXTRA_BODY) ?: POLICIES

        binding.topAppBar.title = title
        binding.tvTitle.text = title
        binding.tvBody.text = body
        binding.tvSubtitle.text = "本页随 App 一起安装，无需联网即可查阅"
    }

    companion object {
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_BODY = "body"

        fun intent(ctx: Context, title: String, body: String? = null): Intent =
            Intent(ctx, DocActivity::class.java)
                .putExtra(EXTRA_TITLE, title)
                .putExtra(EXTRA_BODY, body ?: POLICIES)

        /** 配送与售后条款（与站点 /policies 内容一致，纯文本版） */
        val POLICIES = """
            【发货与物流】
            备货时效
            · 现货款式：付款后 1–2 个工作日内出库。
            · 来图定制：确认设计稿后 5–7 个工作日完成制作并出库。
            · 节假日与大促期间顺延，会在订单页标注预计发货日。

            配送范围与运费
            · 中国大陆：顺丰 / 中通，满 ¥199 包邮，未满收取 ¥12。
            · 港澳台及海外：DHL / 4PX 专线，运费在下单页按重量实时计算。
            · 跨境包裹可能产生关税，由收件人承担，具体以当地海关规定为准。

            物流跟踪
            · 出库后可在「我的 → 订单」查看订单状态与轨迹。

            【退换与售后】
            七天无理由
            · 非定制商品签收后 7 天内，不影响二次销售可申请退换。
            · 商品需保持完整包装、吊牌与随附赠品齐全。
            · 往返运费由买家承担；质量问题由本店承担。

            不支持退换的情形
            · 来图定制、刻字、改尺寸等按需制作商品。
            · 已佩戴、已清洗或人为损坏的商品。
            · 超过 7 天且无质量问题的商品。

            质量问题处理
            · 收到商品 48 小时内拍照联系客服，核实后可免费重做或全额退款，运费由本店承担。

            【支付方式】
            · PayPal：支持信用卡与 PayPal 余额，跨境订单推荐。
            · 微信支付：中国大陆用户扫码或跳转 App 支付。
            · 支付宝：支持余额、花呗与银行卡。
            · 万事达卡（Mastercard）：境外卡组织通道，以美元结算。

            所有渠道均在 App 结算页选择；扣款成功但订单未更新时，请保留支付凭证联系客服。

            【定制服务说明】
            · 「来图定制」支持上传参考图，客服会在 1 个工作日内确认可行性与报价。
            · 定制款一经确认设计稿并开始制作，不支持取消与退换。
            · 手工制品存在细微差异（纹路、色差、尺寸 ±2mm），属正常工艺范围。

            【联系我们】
            客服时间：周一至周六 09:30–18:30（GMT+8）
            邮箱：service@shine.xumois.cn
            询盘表单：站点「询盘联系」页面，24 小时内回复。

            shine 星心 · 饰品与手工品
        """.trimIndent()

        /** 隐私与权限说明 */
        val PRIVACY = """
            【我们收集什么】
            本 App 只在你主动使用时上传必要信息：
            · 下单：收货人、邮箱、电话、国家/地区、地址（用于发货与通知）。
            · 注册/登录：邮箱与密码（密码只保存不可逆哈希，我们不保存明文）。
            · 来图定制：你主动选择的参考图（仅用于确认设计与制作）。

            【系统权限】
            本 App 最多申请四项权限，都可在系统设置里随时关闭：
            · 定位：估算运费、匹配就近自提点。
            · 手机相册：上传定制参考图。
            · 手机相机：下单现场拍图、扫溯源码。
            · 蓝牙：连接蓝牙标签打印机。

            【我们不做什么】
            · 不读取通讯录、短信、通话记录。
            · 不采集与购物无关的设备信息。
            · 不向第三方出售或共享你的个人信息。

            【数据保存】
            登录态保存在本机（Cookie），退出登录即清除；
            删除账号可联系 service@shine.xumois.cn。
        """.trimIndent()
    }
}
