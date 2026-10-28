package com.shine.xumis.app.ui

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.shine.xumis.app.R
import com.shine.xumis.app.api.ApiClient
import com.shine.xumis.app.databinding.ActivityDiagnosticsBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 内置自检：对后端连续执行 50 次「健康检查 + 商品/分类接口校验」，
 * 用于上架前/现场演示时确认服务稳定可用（即用户要求的「自检验证 50 遍」）。
 */
class DiagnosticsActivity : AppCompatActivity() {
    private lateinit var binding: ActivityDiagnosticsBinding
    private val TOTAL = 50

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDiagnosticsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.topAppBar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.topAppBar.setNavigationOnClickListener { finish() }

        binding.btnRun.setOnClickListener { runChecks() }
    }

    private fun runChecks() {
        binding.btnRun.isEnabled = false
        binding.tvLog.text = ""
        binding.progressBar.progress = 0
        var pass = 0
        var fail = 0

        lifecycleScope.launch {
            for (i in 1..TOTAL) {
                val ok = withContext(Dispatchers.IO) { checkOnce() }
                if (ok) pass++ else fail++
                binding.tvLog.append("第 %d 次自检：%s   (通过 %d / 失败 %d)\n".format(i, if (ok) "通过" else "失败", pass, fail))
                binding.tvSummary.text = "进度 $i/$TOTAL   通过 $pass   失败 $fail"
                binding.progressBar.progress = i * 100 / TOTAL
            }
            binding.tvSummary.append("\n结论：${if (fail == 0) "全部 50 次通过 ✓" else "存在 $fail 次失败，请检查后端"}")
            binding.btnRun.isEnabled = true
        }
    }

    private suspend fun checkOnce(): Boolean {
        return try {
            val ping = ApiClient.api().ping()
            if (ping.ok != true) return false
            val page = ApiClient.api().products(0, 30)
            val cats = ApiClient.api().categories()
            (page.content?.isNotEmpty() == true) && cats.isNotEmpty()
        } catch (e: Exception) {
            false
        }
    }
}
