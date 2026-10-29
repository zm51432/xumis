package com.shine.xumis.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.shine.xumis.app.api.ApiClient
import com.shine.xumis.app.databinding.ActivityStatusBinding
import com.shine.xumis.app.util.CatalogCache
import com.shine.xumis.app.util.CrashGuard
import com.shine.xumis.app.util.HealthLog
import com.shine.xumis.app.util.OfflineAssets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 运行状态：把「App 到底靠不靠得住」变成可读的数字。
 *
 * 采样 5 轮 ping + 商品 + 分类，给出通过率与延迟；连同缓存商品数、离线资源、
 * 历史检测时间一起展示，结果落盘，下次打开还能看到上次的结论。
 */
class StatusActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStatusBinding
    private val rounds = 5

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStatusBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.topAppBar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.topAppBar.setNavigationOnClickListener { finish() }

        binding.btnRun.setOnClickListener { run(rounds) }
        binding.btnPressure.setOnClickListener {
            startActivity(Intent(this, DiagnosticsActivity::class.java))
        }

        renderLast()
        renderDetail()
        renderCrash()
    }

    /** 展示上一次的检测结论（没有就跑一次） */
    private fun renderLast() {
        val last = HealthLog.last(this)
        if (last == null) {
            run(rounds)
        } else {
            binding.tvRate.text = "${last.rate}%"
            binding.tvAvg.text = "${last.avgMs} ms"
            binding.tvMax.text = "${last.maxMs} ms"
            binding.tvVerdict.text =
                if (last.fail == 0) "上次检测：全部通过 ✓" else "上次检测：${last.fail} 次失败"
            binding.tvVerdictSub.text =
                "${last.pass}/${last.total} 轮成功 · ${last.timeText} · 可点下方重新检测"
        }
    }

    private fun run(total: Int) {
        binding.btnRun.isEnabled = false
        binding.progress.visibility = View.VISIBLE
        binding.progress.progress = 0
        binding.tvLog.text = ""
        binding.tvVerdict.text = "正在检测…"

        var pass = 0
        var fail = 0
        var sum = 0L
        var max = 0L

        lifecycleScope.launch {
            for (i in 1..total) {
                val t0 = System.currentTimeMillis()
                val ok = withContext(Dispatchers.IO) { sampleOnce() }
                val cost = System.currentTimeMillis() - t0
                sum += cost
                if (cost > max) max = cost
                if (ok) pass++ else fail++
                binding.tvLog.append("第 $i 轮：${if (ok) "通过" else "失败"}  ${cost}ms\n")
                binding.progress.progress = i * 100 / total
            }
            val avg = sum / total
            HealthLog.save(this@StatusActivity, pass, fail, avg, max)

            binding.tvRate.text = "${pass * 100 / total}%"
            binding.tvAvg.text = "$avg ms"
            binding.tvMax.text = "$max ms"
            binding.tvVerdict.text =
                if (fail == 0) "全部 $total 轮通过 ✓" else "$fail 轮失败，请检查后端"
            binding.tvVerdictSub.text =
                "商品、分类、健康检查三个接口各调用一次算一轮 · 平均 ${avg}ms · 最大 ${max}ms"
            binding.progress.visibility = View.GONE
            binding.btnRun.isEnabled = true
            renderDetail()
        }
    }

    private suspend fun sampleOnce(): Boolean = try {
        val ping = ApiClient.api().ping()
        val page = ApiClient.api().products(0, 5)
        val cats = ApiClient.api().categories()
        ping.ok == true && page.content?.isNotEmpty() == true && cats.isNotEmpty()
    } catch (e: Exception) {
        false
    }

    private fun renderDetail() {
        val cached = CatalogCache.count(this)
        val imgs = OfflineAssets.imageCount(this)
        val bytes = OfflineAssets.sizeText(OfflineAssets.imageBytes(this))
        val last = HealthLog.last(this)
        binding.tvDays.text = "${HealthLog.daysInUse(this)} 天"
        binding.tvDetail.text = buildString {
            append("缓存商品　$cached 件（${CatalogCache.savedAtText(this@StatusActivity)}）\n")
            append("离线资源　商品图 $imgs 张 / $bytes，条款 1 份\n")
            append("上次检测　${last?.timeText ?: "—"}\n")
            append("崩溃记录　${if (CrashGuard.lastCrash(this@StatusActivity) == null) "无" else "有（见下方）"}")
        }
    }

    private fun renderCrash() {
        val crash = CrashGuard.lastCrash(this) ?: return
        binding.tvCrash.visibility = View.VISIBLE
        binding.tvCrash.text = "上次异常记录：\n$crash"
    }
}
