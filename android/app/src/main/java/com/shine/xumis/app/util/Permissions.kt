package com.shine.xumis.app.util

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat

/**
 * 四类系统权限的统一入口：定位 / 相册 / 相机 / 蓝牙。
 *
 * 设计要点（针对小米、OPPO、vivo 等国产 ROM）：
 * 1. 权限清单按系统版本动态计算，不在代码里写死——Android 12 把蓝牙拆成扫描/连接，
 *    Android 13 把存储拆成图片/视频/音频，Android 14 又加了「仅选中的照片」。
 * 2. 相册区分「全部授权」与「部分授权」：ColorOS / HyperOS 上用户常只选几张照片，
 *    这种情况不能当成未授权，否则每次进页面都会反复弹窗。
 * 3. 硬件缺失（部分入门机型无蓝牙）判定为「不适用」，而不是「被拒绝」。
 * 4. 拒绝且勾选「不再询问」后，只能引导去系统设置；国产 ROM 的权限页与自启动页
 *    包名各不相同，这里按品牌逐个尝试，失败再退回本应用的系统详情页。
 */
object Permissions {

    enum class Group(val title: String, val purpose: String) {
        LOCATION("定位", "估算运费、匹配就近自提点，站点地图定位"),
        GALLERY("手机相册", "来图定制上传参考图、更换头像"),
        CAMERA("手机相机", "下单时现场拍图、扫描商品溯源码"),
        BLUETOOTH("蓝牙", "连接蓝牙标签打印机、门店近场设备")
    }

    enum class Status { GRANTED, PARTIAL, DENIED, UNAVAILABLE }

    const val REQ_PERMISSIONS = 4401

    /** 当前系统版本下，某个分组实际要申请的权限 */
    fun manifestPerms(group: Group): Array<String> = when (group) {
        Group.LOCATION -> arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        Group.GALLERY -> when {
            Build.VERSION.SDK_INT >= 34 -> arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
            )

            Build.VERSION.SDK_INT >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
            else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        Group.CAMERA -> arrayOf(Manifest.permission.CAMERA)

        // Android 12 起才需要运行时申请；11 及以下是安装即授予的普通权限
        Group.BLUETOOTH -> if (Build.VERSION.SDK_INT >= 31) arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT
        ) else emptyArray()
    }

    private fun grantedOne(ctx: Context, perm: String): Boolean =
        ContextCompat.checkSelfPermission(ctx, perm) == PackageManager.PERMISSION_GRANTED

    /** 该分组是否已经可以正常工作（部分授权也算可用） */
    fun status(ctx: Context, group: Group): Status {
        if (!hardwareAvailable(ctx, group)) return Status.UNAVAILABLE
        val perms = manifestPerms(group)
        if (perms.isEmpty()) return Status.GRANTED
        if (perms.all { grantedOne(ctx, it) }) return Status.GRANTED
        if (group == Group.GALLERY && Build.VERSION.SDK_INT >= 34 &&
            grantedOne(ctx, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
        ) {
            return Status.PARTIAL
        }
        // 定位：粗略定位也算能用（部分 ROM 只给「大致位置」）
        if (group == Group.LOCATION &&
            grantedOne(ctx, Manifest.permission.ACCESS_COARSE_LOCATION)
        ) {
            return Status.PARTIAL
        }
        return Status.DENIED
    }

    fun isGranted(ctx: Context, group: Group): Boolean =
        status(ctx, group) == Status.GRANTED || status(ctx, group) == Status.PARTIAL

    fun statusText(ctx: Context, group: Group): String = when (status(ctx, group)) {
        Status.GRANTED -> "已允许"
        Status.PARTIAL -> if (group == Group.GALLERY) "部分照片" else "大致位置"
        Status.DENIED -> "未允许"
        Status.UNAVAILABLE -> "本机不支持"
    }

    /** 还需要申请的权限（已全部授予时为空数组） */
    fun missing(ctx: Context, group: Group): Array<String> {
        if (!hardwareAvailable(ctx, group)) return emptyArray()
        return manifestPerms(group).filter { !grantedOne(ctx, it) }.toTypedArray()
    }

    fun grantedCount(ctx: Context): Int =
        Group.values().count { status(ctx, it) == Status.GRANTED || status(ctx, it) == Status.PARTIAL }

    fun summary(ctx: Context): String = "${grantedCount(ctx)}/${Group.values().size} 项已允许"

    /**
     * 算出「一键开启」时应该申请哪些权限（跳过硬件不支持的项）。
     *
     * 注意：这里只负责计算，真正的申请必须由界面用
     * `registerForActivityResult(RequestMultiplePermissions())` 发起。
     * 直接调 ActivityCompat.requestPermissions 在接入 ActivityResult 的 Activity 上
     * 回调会被注册表吞掉，用户点「拒绝」后拿不到任何通知。
     */
    fun allMissing(ctx: Context): Array<String> =
        Group.values()
            .flatMap { missing(ctx, it).toList() }
            .distinct()
            .toTypedArray()

    // ---------------- 硬件判断 ----------------

    fun hardwareAvailable(ctx: Context, group: Group): Boolean = when (group) {
        Group.CAMERA -> ctx.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
        Group.BLUETOOTH -> try {
            BluetoothAdapter.getDefaultAdapter() != null
        } catch (e: Exception) {
            false
        }
        // 定位与相册不依赖可选硬件：定位靠网络也能拿到大致位置，相册一定存在
        else -> true
    }

    // ---------------- 跳系统设置 ----------------

    /** 本应用的系统详情页（所有 ROM 都有，是最稳的兜底） */
    fun openAppSettings(ctx: Context) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", ctx.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { ctx.startActivity(intent) }
            .onFailure { runCatching { ctx.startActivity(appSettingsFallback(ctx)) } }
    }

    private fun appSettingsFallback(ctx: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
        }

    /**
     * 跳转到厂商的「自启动 / 后台运行」白名单页。
     * 小米、OPPO、vivo 默认会杀掉后台进程，导致内嵌网页的登录态与上传中断，
     * 这一步不申请任何额外权限，只是把用户送到系统已有的设置页。
     *
     * @return 是否成功打开了厂商页面（false 表示已退回本应用详情页）
     */
    fun openOemBackgroundSettings(ctx: Context): Boolean {
        val brand = brandKey()
        val candidates = when (brand) {
            "xiaomi" -> listOf(
                ComponentName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.autostart.AutoStartManagementActivity"
                ),
                ComponentName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.permissions.PermissionsEditorActivity"
                )
            )

            "oppo" -> listOf(
                ComponentName(
                    "com.coloros.safecenter",
                    "com.coloros.safecenter.permission.startup.StartupAppListActivity"
                ),
                ComponentName(
                    "com.coloros.safecenter",
                    "com.coloros.safecenter.startupapp.StartupAppListActivity"
                ),
                ComponentName(
                    "com.oppo.safe",
                    "com.oppo.safe.permission.startup.StartupAppListActivity"
                )
            )

            "vivo" -> listOf(
                ComponentName(
                    "com.vivo.permissionmanager",
                    "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
                ),
                ComponentName(
                    "com.iqoo.secure",
                    "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"
                ),
                ComponentName(
                    "com.iqoo.secure",
                    "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager"
                )
            )

            "huawei" -> listOf(
                ComponentName(
                    "com.huawei.systemmanager",
                    "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
                ),
                ComponentName(
                    "com.huawei.systemmanager",
                    "com.huawei.systemmanager.optimize.process.ProtectActivity"
                )
            )

            else -> emptyList()
        }
        for (c in candidates) {
            val intent = Intent().apply {
                component = c
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                // 小米权限编辑页需要包名才能定位到本应用
                putExtra("extra_pkgname", ctx.packageName)
                putExtra("package_name", ctx.packageName)
            }
            val ok = runCatching { ctx.startActivity(intent) }.isSuccess
            if (ok) return true
        }
        openAppSettings(ctx)
        return false
    }

    /** 品牌标识：只用于选择跳转哪个系统设置页，不做任何数据上报 */
    fun brandKey(): String {
        val s = (Build.BRAND + " " + Build.MANUFACTURER).lowercase()
        return when {
            s.contains("xiaomi") || s.contains("redmi") || s.contains("poco") -> "xiaomi"
            s.contains("oppo") || s.contains("realme") || s.contains("oneplus") -> "oppo"
            s.contains("vivo") || s.contains("iqoo") -> "vivo"
            s.contains("huawei") || s.contains("honor") -> "huawei"
            else -> "other"
        }
    }

    fun brandLabel(): String = when (brandKey()) {
        "xiaomi" -> "小米 / Redmi"
        "oppo" -> "OPPO / realme / 一加"
        "vivo" -> "vivo / iQOO"
        "huawei" -> "华为 / 荣耀"
        else -> "通用安卓"
    }

    /**
     * 国产 ROM 的「后台限制」提示语。不同品牌的叫法不一样，
     * 直接写品牌里的真实菜单名，用户才找得到。
     */
    fun backgroundTip(): String = when (brandKey()) {
        "xiaomi" -> "小米：设置 → 应用设置 → 应用管理 → shine 星心 → 自启动 / 省电策略 → 无限制"
        "oppo" -> "OPPO：设置 → 应用管理 → shine 星心 → 耗电管理 → 允许后台运行"
        "vivo" -> "vivo：设置 → 应用与权限 → 权限管理 → 自启动 → 允许"
        "huawei" -> "华为：设置 → 应用 → 应用启动管理 → shine 星心 → 手动管理"
        else -> "设置 → 应用 → shine 星心 → 电池 → 允许后台运行"
    }
}
