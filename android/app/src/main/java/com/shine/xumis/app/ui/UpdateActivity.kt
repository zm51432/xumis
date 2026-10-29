package com.shine.xumis.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.gson.Gson
import com.shine.xumis.app.databinding.ActivityUpdateBinding
import com.shine.xumis.app.util.AppPrefs
import com.shine.xumis.app.util.UpdateChecker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 软件更新页：展示新版本信息 → 下载（带进度）→ 校验 → 交系统安装器安装。
 *
 * 强制更新的版本不提供「稍后再说」，返回键也不放行；
 * 非强制的版本用户点「稍后」后，同一个版本不再主动弹窗（下次手动检查仍可看到）。
 */
class UpdateActivity : AppCompatActivity() {

    companion object {
        private const val EXTRA_RELEASE = "release_json"
        private const val EXTRA_FORCE = "force"

        fun intent(ctx: Context, rel: UpdateChecker.Release, force: Boolean): Intent =
            Intent(ctx, UpdateActivity::class.java)
                .putExtra(EXTRA_RELEASE, Gson().toJson(rel))
                .putExtra(EXTRA_FORCE, force)
    }

    private lateinit var binding: ActivityUpdateBinding
    private var release: UpdateChecker.Release? = null
    private var force = false
    private var busy = false
    private var apkFile: File? = null
    private var awaitingInstallPermission = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUpdateBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.topAppBar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.topAppBar.setNavigationOnClickListener { maybeClose() }

        val raw = intent.getStringExtra(EXTRA_RELEASE)
        release = try {
            Gson().fromJson(raw, UpdateChecker.Release::class.java)
        } catch (e: Exception) {
            null
        }
        val rel = release
        if (rel == null || rel.versionCode <= 0) {
            Toast.makeText(this, "更新信息不完整", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        force = intent.getBooleanExtra(EXTRA_FORCE, false) || rel.force

        render(rel)

        binding.btnPrimary.setOnClickListener {
            val file = apkFile
            if (file != null && file.exists()) install(file) else startDownload(rel)
        }
        binding.btnLater.setOnClickListener { skip(rel) }
    }

    override fun onResume() {
        super.onResume()
        // 从「安装未知应用」设置页回来：权限已给就直接拉起安装器
        if (awaitingInstallPermission) {
            awaitingInstallPermission = false
            val file = apkFile
            if (file != null && file.exists() && canInstall()) install(file)
        }
    }

    private fun render(rel: UpdateChecker.Release) {
        binding.tvVersion.text = "v" + rel.versionName
        val parts = mutableListOf<String>()
        if (rel.sizeText.isNotBlank()) parts.add("安装包 " + rel.sizeText)
        parts.add("当前版本 v" + UpdateChecker.installedVersionName(this))
        binding.tvMeta.text = parts.joinToString(" · ")

        binding.tvForce.visibility = if (force) View.VISIBLE else View.GONE

        val lines = UpdateChecker.noteLines(rel.notes)
        binding.tvNotes.text = if (lines.isEmpty()) {
            "本次更新包含问题修复与体验优化，建议升级到最新版本。"
        } else {
            lines.joinToString("\n") { line ->
                if (line.startsWith("·") || line.startsWith("-") || line.startsWith("•")) line else "· $line"
            }
        }

        if (force) {
            binding.btnLater.visibility = View.GONE
            onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (!busy) return
                    Toast.makeText(this@UpdateActivity, "正在下载更新包，请稍候", Toast.LENGTH_SHORT).show()
                }
            })
        } else {
            binding.btnLater.visibility = View.VISIBLE
        }
    }

    private fun startDownload(rel: UpdateChecker.Release) {
        if (busy) return
        busy = true
        binding.tvError.visibility = View.GONE
        binding.boxProgress.visibility = View.VISIBLE
        binding.pbDownload.progress = 0
        binding.tvProgress.text = "正在下载更新包…"
        binding.btnPrimary.isEnabled = false
        binding.btnPrimary.text = "正在下载…"
        binding.btnLater.isEnabled = false

        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    UpdateChecker.download(this@UpdateActivity, rel) { pct ->
                        runOnUiThread {
                            if (!isFinishing) {
                                binding.pbDownload.progress = pct
                                binding.tvProgress.text = "正在下载更新包… $pct%"
                            }
                        }
                    }
                }
            }
            busy = false
            binding.btnLater.isEnabled = true
            result.onSuccess { file ->
                apkFile = file
                binding.pbDownload.progress = 100
                binding.tvProgress.text = "下载完成，正在打开安装程序…"
                binding.btnPrimary.isEnabled = true
                binding.btnPrimary.text = "重新安装"
                install(file)
            }.onFailure { e ->
                binding.boxProgress.visibility = View.GONE
                binding.tvError.visibility = View.VISIBLE
                binding.tvError.text = buildString {
                    append("更新失败：")
                    append(e.message ?: "网络异常")
                    append("\n请检查手机网络后重试；多次失败可到官网下载页手动安装，数据不会丢失。")
                }
                binding.btnPrimary.isEnabled = true
                binding.btnPrimary.text = "重新下载"
            }
        }
    }

    private fun canInstall(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || packageManager.canRequestPackageInstalls()

    private fun install(file: File) {
        if (!canInstall()) {
            awaitingInstallPermission = true
            MaterialAlertDialogBuilder(this)
                .setTitle("允许安装更新")
                .setMessage(
                    "系统需要你允许「shine 星心」安装应用，才能完成本次更新。\n\n" +
                        "打开开关后返回，会自动继续安装。"
                )
                .setNegativeButton("取消", null)
                .setPositiveButton("去设置") { _, _ ->
                    runCatching {
                        startActivity(
                            Intent(
                                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                Uri.parse("package:$packageName")
                            )
                        )
                    }.onFailure {
                        Toast.makeText(this, "请到系统设置中允许安装未知应用", Toast.LENGTH_LONG).show()
                    }
                }
                .show()
            return
        }

        val uri = try {
            FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        } catch (e: Exception) {
            binding.tvError.visibility = View.VISIBLE
            binding.tvError.text = "安装包读取失败：" + (e.message ?: "未知错误")
            return
        }
        val view = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            startActivity(view)
        } catch (e: Exception) {
            binding.tvError.visibility = View.VISIBLE
            binding.tvError.text = "无法调起系统安装程序：" + (e.message ?: "未知错误")
        }
    }

    private fun skip(rel: UpdateChecker.Release) {
        AppPrefs.skippedVersionCode = rel.versionCode
        finish()
    }

    private fun maybeClose() {
        if (busy) {
            if (force) {
                Toast.makeText(this, "正在下载更新包，请稍候", Toast.LENGTH_SHORT).show()
                return
            }
            MaterialAlertDialogBuilder(this)
                .setTitle("取消下载？")
                .setMessage("更新包还没下载完，退出后需要重新下载。")
                .setNegativeButton("继续下载", null)
                .setPositiveButton("退出") { _, _ -> finish() }
                .show()
            return
        }
        if (force && apkFile == null) {
            Toast.makeText(this, "本次为重要更新，请完成安装后继续使用", Toast.LENGTH_SHORT).show()
            return
        }
        finish()
    }
}
