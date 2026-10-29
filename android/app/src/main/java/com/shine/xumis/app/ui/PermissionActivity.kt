package com.shine.xumis.app.ui

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.shine.xumis.app.R
import com.shine.xumis.app.databinding.ActivityPermissionBinding
import com.shine.xumis.app.databinding.ItemSettingsRowBinding
import com.shine.xumis.app.util.DeviceProbe
import com.shine.xumis.app.util.Permissions
import java.io.File
import java.util.Locale

/**
 * 权限与兼容性。
 *
 * 这里只处理四类权限：定位、手机相册、手机相机、蓝牙。
 * 每一行点进去都能就地验证权限是否真的生效（取坐标 / 数照片 / 试拍 / 扫蓝牙），
 * 而不是只显示一个「已授权」的静态文字。
 */
class PermissionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPermissionBinding
    private var pendingGroup: Permissions.Group? = null
    private var captureFile: File? = null

    private val permLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            refresh()
            val group = pendingGroup
            pendingGroup = null
            if (group == null) {
                val denied = result.filterValues { !it }.keys
                if (denied.isEmpty()) toast("权限已全部允许")
            } else {
                afterRequest(group)
            }
        }

    private val takePicture =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
            if (!ok) {
                toast("已取消拍照")
                return@registerForActivityResult
            }
            val f = captureFile
            if (f == null || !f.exists() || f.length() == 0L) {
                toast("拍照未返回照片，请重试")
                return@registerForActivityResult
            }
            val opt = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(f.absolutePath, opt)
            MaterialAlertDialogBuilder(this)
                .setTitle("拍照成功")
                .setMessage(
                    "相机权限已生效，照片已保存到应用缓存：\n\n" +
                        "· 尺寸 ${opt.outWidth} × ${opt.outHeight}\n" +
                        "· 体积 ${"%.1f".format(Locale.US, f.length() / 1024f / 1024f)} MB\n" +
                        "· 路径 ${f.absolutePath.removePrefix(filesDir.parent ?: "")}\n\n" +
                        "下单时「来图定制」上传参考图走的就是这条链路。"
                )
                .setPositiveButton("好", null)
                .show()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPermissionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.topAppBar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.topAppBar.setNavigationOnClickListener { finish() }

        setUpRows()
        binding.btnGrantAll.setOnClickListener { grantAll() }
        refresh()
    }

    override fun onResume() {
        super.onResume()
        // 用户可能刚从系统设置页放开权限回来
        refresh()
    }

    // ---------------- 行绑定 ----------------

    private fun setUpRows() {
        bind(binding.rowLocation, R.drawable.ic_location, Permissions.Group.LOCATION) {
            when (Permissions.status(this, Permissions.Group.LOCATION)) {
                Permissions.Status.GRANTED, Permissions.Status.PARTIAL -> locate()
                Permissions.Status.UNAVAILABLE -> toast("本机没有定位模块")
                Permissions.Status.DENIED -> request(Permissions.Group.LOCATION)
            }
        }

        bind(binding.rowGallery, R.drawable.ic_gallery, Permissions.Group.GALLERY) {
            when (Permissions.status(this, Permissions.Group.GALLERY)) {
                Permissions.Status.DENIED -> request(Permissions.Group.GALLERY)
                else -> showPhotos()
            }
        }

        bind(binding.rowCamera, R.drawable.ic_camera, Permissions.Group.CAMERA) {
            when (Permissions.status(this, Permissions.Group.CAMERA)) {
                Permissions.Status.DENIED -> request(Permissions.Group.CAMERA)
                Permissions.Status.UNAVAILABLE -> toast("本机没有摄像头")
                else -> shoot()
            }
        }

        bind(binding.rowBluetooth, R.drawable.ic_bluetooth, Permissions.Group.BLUETOOTH) {
            when (Permissions.status(this, Permissions.Group.BLUETOOTH)) {
                Permissions.Status.UNAVAILABLE -> MaterialAlertDialogBuilder(this)
                    .setTitle("本机没有蓝牙模块")
                    .setMessage("这台设备不带蓝牙硬件，App 会自动隐藏蓝牙相关入口，其余功能不受影响。")
                    .setPositiveButton("好", null)
                    .show()

                Permissions.Status.DENIED -> request(Permissions.Group.BLUETOOTH)
                else -> scanBluetooth()
            }
        }

        // ---- 厂商适配 ----
        bindStatic(
            binding.rowBrand, R.drawable.ic_shield, "识别到的系统",
            "按品牌跳转到对应的权限与自启动设置页"
        ) { showBrand() }

        bindStatic(
            binding.rowBackground, R.drawable.ic_status, "后台运行白名单",
            "小米 / OPPO / vivo 会限制后台，开启后登录态与上传更稳"
        ) {
            val ok = Permissions.openOemBackgroundSettings(this)
            toast(if (ok) "已打开系统的后台管理页" else "未找到厂商页面，已打开本应用设置")
        }

        bindStatic(
            binding.rowAppSettings, R.drawable.ic_settings, "本应用系统设置",
            "查看或修改已授予的权限"
        ) { Permissions.openAppSettings(this) }
    }

    private fun bind(
        row: ItemSettingsRowBinding,
        icon: Int,
        group: Permissions.Group,
        onClick: () -> Unit
    ) {
        row.ivIcon.setImageResource(icon)
        row.tvTitle.text = group.title
        row.tvSub.text = group.purpose
        row.tvSub.visibility = View.VISIBLE
        row.ivChevron.visibility = View.VISIBLE
        row.root.setOnClickListener { onClick() }
    }

    private fun bindStatic(
        row: ItemSettingsRowBinding,
        icon: Int,
        title: String,
        sub: String,
        onClick: () -> Unit
    ) {
        row.ivIcon.setImageResource(icon)
        row.tvTitle.text = title
        row.tvSub.text = sub
        row.tvSub.visibility = View.VISIBLE
        row.ivChevron.visibility = View.VISIBLE
        row.root.setOnClickListener { onClick() }
    }

    // ---------------- 状态刷新 ----------------

    private fun refresh() {
        val groups = Permissions.Group.values()
        val granted = groups.count { Permissions.isGranted(this, it) }
        binding.tvSummary.text = "已允许 $granted/${groups.size} 项"
        binding.btnGrantAll.visibility = if (granted == groups.size) View.GONE else View.VISIBLE

        binding.tvDevice.text = buildString {
            append("机型 ").append(Build.MANUFACTURER.replaceFirstChar { it.uppercase() })
            append(" ").append(Build.MODEL).append(" · Android ").append(Build.VERSION.RELEASE)
            append("\n系统适配 ").append(Permissions.brandLabel())
            append(" · 相机 ").append(DeviceProbe.cameraSummary(this@PermissionActivity))
        }

        setStatus(binding.rowLocation, Permissions.Group.LOCATION)
        setStatus(binding.rowGallery, Permissions.Group.GALLERY)
        setStatus(binding.rowCamera, Permissions.Group.CAMERA)
        setStatus(binding.rowBluetooth, Permissions.Group.BLUETOOTH)

        binding.rowBrand.tvValue.text = Permissions.brandLabel()
        binding.rowBrand.tvValue.visibility = View.VISIBLE

        binding.tvNote.text = buildString {
            append("本应用只申请以下四项权限，没有申请通讯录、短信、通话记录、")
            append("后台定位、通知等任何其它权限。\n\n")
            append("· 定位：估算运费、匹配就近自提点，仅在打开相关页面或点击本页「定位」时读取一次。\n")
            append("· 相册：来图定制上传参考图、更换头像，只读取你主动选择的图片。\n")
            append("· 相机：下单时现场拍照、扫描商品溯源码。\n")
            append("· 蓝牙：连接蓝牙标签打印机与门店近场设备。\n\n")
            append("所有权限都可以随时拒绝，拒绝后仅对应功能不可用，其余功能照常使用。")
        }
        binding.tvNote.append("\n\n" + Permissions.backgroundTip())
    }

    private fun setStatus(row: ItemSettingsRowBinding, group: Permissions.Group) {
        val status = Permissions.status(this, group)
        row.tvValue.text = Permissions.statusText(this, group)
        row.tvValue.visibility = View.VISIBLE
        val color = when (status) {
            Permissions.Status.GRANTED -> R.color.shine_mint_dark
            Permissions.Status.PARTIAL -> R.color.shine_pink
            Permissions.Status.DENIED -> R.color.shine_sub
            Permissions.Status.UNAVAILABLE -> R.color.shine_sub
        }
        row.tvValue.setTextColor(androidx.core.content.ContextCompat.getColor(this, color))
    }

    // ---------------- 申请 ----------------

    private fun grantAll() {
        val need = Permissions.allMissing(this)
        if (need.isEmpty()) {
            toast("四项权限均已允许")
            refresh()
            return
        }
        pendingGroup = null
        permLauncher.launch(need)
    }

    private fun request(group: Permissions.Group) {
        val need = Permissions.missing(this, group)
        if (need.isEmpty()) {
            // 系统不再弹窗（已选「不再询问」）时，直接引导去设置
            afterRequest(group)
            return
        }
        pendingGroup = group
        permLauncher.launch(need)
    }

    /** 申请回调之后：拿到就用，被拒就引导去系统设置（国产 ROM 常勾「不再询问」） */
    private fun afterRequest(group: Permissions.Group) {
        if (Permissions.isGranted(this, group)) {
            toast("${group.title}权限已允许")
            return
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("${group.title}权限未开启")
            .setMessage(
                "需要「${group.title}」权限才能使用：${group.purpose}。\n\n" +
                    "如果刚才的弹窗里选了「拒绝」或不再弹出提示，请到系统设置里手动打开。\n\n" +
                    Permissions.backgroundTip()
            )
            .setNegativeButton("暂不开启", null)
            .setPositiveButton("去设置") { _, _ -> Permissions.openAppSettings(this) }
            .show()
    }

    // ---------------- 就地验证 ----------------

    private fun locate() {
        toast("正在获取当前位置…")
        DeviceProbe.locate(this) { text ->
            MaterialAlertDialogBuilder(this)
                .setTitle("定位可用")
                .setMessage(text)
                .setPositiveButton("好", null)
                .show()
        }
    }

    private fun showPhotos() {
        val text = DeviceProbe.photoCount(this)
        MaterialAlertDialogBuilder(this)
            .setTitle("相册权限")
            .setMessage("$text\n\n来图定制页面上传参考图时，会调用同一套相册权限。")
            .setNegativeButton("关闭", null)
            .setPositiveButton("重新检查") { _, _ -> refresh(); showPhotos() }
            .show()
    }

    private fun shoot() {
        val dir = File(cacheDir, "captures").apply { mkdirs() }
        val f = File(dir, "shot_${System.currentTimeMillis()}.jpg")
        captureFile = f
        val uri: Uri = try {
            FileProvider.getUriForFile(this, "$packageName.fileprovider", f)
        } catch (e: Exception) {
            toast("无法创建拍照缓存：${e.message}")
            return
        }
        try {
            takePicture.launch(uri)
        } catch (e: Exception) {
            toast("没有可用的相机应用")
        }
    }

    private fun scanBluetooth() {
        val info = DeviceProbe.bluetoothSummary(this)
        if (!info.ok) {
            MaterialAlertDialogBuilder(this)
                .setTitle(info.title)
                .setMessage(info.detail)
                .setNegativeButton("关闭", null)
                .setPositiveButton("打开蓝牙设置") { _, _ ->
                    runCatching { startActivity(Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS)) }
                }
                .show()
            return
        }
        toast("正在扫描周边蓝牙设备（约 6 秒）…")
        DeviceProbe.scanBluetooth(this) { text ->
            MaterialAlertDialogBuilder(this)
                .setTitle("蓝牙可用")
                .setMessage("${info.detail}\n\n$text")
                .setPositiveButton("好", null)
                .show()
        }
    }

    private fun showBrand() {
        MaterialAlertDialogBuilder(this)
            .setTitle(Permissions.brandLabel())
            .setMessage(
                "当前系统：${Build.MANUFACTURER} ${Build.MODEL}（Android ${Build.VERSION.RELEASE}）\n\n" +
                    "已按该品牌适配：\n" +
                    "· 权限弹窗按系统版本自动选择正确的权限集合\n" +
                    "· 相册支持「仅选中的照片」（Android 14 起）\n" +
                    "· 可一键跳到该品牌的后台 / 自启动白名单页\n\n" +
                    Permissions.backgroundTip()
            )
            .setNegativeButton("关闭", null)
            .setPositiveButton("打开后台设置") { _, _ ->
                Permissions.openOemBackgroundSettings(this)
            }
            .show()
    }

    private fun toast(text: String) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
    }
}
