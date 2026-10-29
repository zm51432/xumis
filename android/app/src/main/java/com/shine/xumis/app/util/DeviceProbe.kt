package com.shine.xumis.app.util

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.hardware.camera2.CameraManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 用真机能力证明「权限确实生效」：
 * 定位取一次坐标、蓝牙扫一轮周边设备、相机列出可用镜头。
 *
 * 全部带异常兜底——国产 ROM 上这些系统服务偶发 SecurityException / NullPointerException，
 * 不能让测试按钮把 App 带崩。
 */
object DeviceProbe {

    // ---------------- 定位 ----------------

    /**
     * 取一次当前位置，三级兜底：
     * 1. Android 11+ 的 getCurrentLocation（一次性请求，最省电）；
     * 2. 它拿不到就主动 requestLocationUpdates 等一帧（国产 ROM 上 getCurrentLocation
     *    经常直接回 null，但回调通道是通的）；
     * 3. 仍拿不到就退回 lastKnownLocation，并明确告诉用户是「暂无定位记录」而不是权限问题。
     * 结果统一回调在主线程。
     */
    @SuppressLint("MissingPermission")
    fun locate(ctx: Context, onResult: (String) -> Unit) {
        if (!Permissions.isGranted(ctx, Permissions.Group.LOCATION)) {
            onResult("尚未允许定位权限")
            return
        }
        val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        if (lm == null) {
            onResult("本机无定位服务")
            return
        }
        val handler = Handler(Looper.getMainLooper())
        var done = false
        var listener: android.location.LocationListener? = null

        fun finish(text: String) {
            if (done) return
            done = true
            listener?.let { runCatching { lm.removeUpdates(it) } }
            handler.post { onResult(text) }
        }

        fun lastKnown(): String {
            val providers = listOf(
                LocationManager.GPS_PROVIDER,
                LocationManager.NETWORK_PROVIDER,
                LocationManager.PASSIVE_PROVIDER
            )
            var best: Location? = null
            for (p in providers) {
                val loc = try {
                    lm.getLastKnownLocation(p)
                } catch (e: Exception) {
                    null
                }
                if (loc != null && (best == null || loc.time > best.time)) best = loc
            }
            return if (best == null) "已允许定位，但系统暂无定位记录\n请在系统设置里打开「位置信息」后重试"
            else format(best)
        }

        fun enabledProviders(): List<String> = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER
        ).filter { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }

        // 第三级：主动等一帧定位
        fun requestOnce(providers: List<String>) {
            if (providers.isEmpty()) {
                finish("系统定位开关未打开\n" + lastKnown())
                return
            }
            val l = object : android.location.LocationListener {
                override fun onLocationChanged(location: Location) {
                    finish(format(location))
                }

                @Deprecated("Deprecated in Java")
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
                override fun onProviderEnabled(provider: String) = Unit
                override fun onProviderDisabled(provider: String) = Unit
            }
            listener = l
            val ok = runCatching {
                lm.requestLocationUpdates(providers.first(), 0L, 0f, l, Looper.getMainLooper())
            }.isSuccess
            if (!ok) finish(lastKnown())
            // 6 秒没有回点就认了
            handler.postDelayed({ finish(lastKnown()) }, 6000)
        }

        val provs = enabledProviders()
        if (provs.isEmpty()) {
            finish("系统定位开关未打开\n" + lastKnown())
            return
        }

        if (Build.VERSION.SDK_INT >= 30) {
            val called = runCatching {
                lm.getCurrentLocation(provs.first(), null, ctx.mainExecutor) { loc ->
                    if (loc != null) finish(format(loc)) else requestOnce(provs)
                }
            }.isSuccess
            if (!called) requestOnce(provs)
        } else {
            requestOnce(provs)
        }
    }

    private fun format(loc: Location): String {
        val time = SimpleDateFormat("HH:mm:ss", Locale.CHINA).format(Date(loc.time))
        val acc = if (loc.hasAccuracy()) "±${loc.accuracy.toInt()} m" else "精度未知"
        val src = when (loc.provider) {
            LocationManager.GPS_PROVIDER -> "GPS"
            LocationManager.NETWORK_PROVIDER -> "网络"
            LocationManager.PASSIVE_PROVIDER -> "被动"
            else -> loc.provider ?: "未知"
        }
        return String.format(
            Locale.US,
            "纬度 %.5f\n经度 %.5f\n精度 %s · 来源 %s\n定位时间 %s",
            loc.latitude, loc.longitude, acc, src, time
        )
    }

    // ---------------- 蓝牙 ----------------

    data class BtInfo(
        val ok: Boolean,
        val title: String,
        val detail: String
    )

    /** 蓝牙状态 + 已配对数量（不需要扫描，速度快） */
    @SuppressLint("MissingPermission")
    fun bluetoothSummary(ctx: Context): BtInfo {
        val adapter = try {
            val mgr = ctx.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            mgr?.adapter ?: BluetoothAdapter.getDefaultAdapter()
        } catch (e: Exception) {
            null
        } ?: return BtInfo(false, "本机无蓝牙硬件", "该机型不带蓝牙模块，相关功能已自动隐藏")

        val enabled = try {
            adapter.isEnabled
        } catch (e: SecurityException) {
            false
        }
        if (!enabled) return BtInfo(false, "蓝牙未开启", "请先在系统控制中心打开蓝牙，再回来扫描")

        val bonded = try {
            Permissions.isGranted(ctx, Permissions.Group.BLUETOOTH)
            adapter.bondedDevices?.size ?: 0
        } catch (e: Exception) {
            0
        }
        return BtInfo(
            true,
            "蓝牙已开启",
            "已配对设备 $bonded 台 · 适配器 ${adapter.name ?: "未命名"} · 支持 BLE ${ctx.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_BLUETOOTH_LE)}"
        )
    }

    /**
     * 扫描一轮周边 BLE 设备（约 6 秒）。
     * Android 12+ 需要 BLUETOOTH_SCAN；11 及以下 BLE 扫描依赖定位权限，已在清单里一并处理。
     */
    @SuppressLint("MissingPermission")
    fun scanBluetooth(ctx: Context, onResult: (String) -> Unit) {
        val adapter = try {
            (ctx.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
                ?: BluetoothAdapter.getDefaultAdapter()
        } catch (e: Exception) {
            null
        }
        if (adapter == null) {
            onResult("本机无蓝牙硬件")
            return
        }
        if (!Permissions.isGranted(ctx, Permissions.Group.BLUETOOTH)) {
            onResult("尚未允许蓝牙权限")
            return
        }
        val scanner = try {
            adapter.bluetoothLeScanner
        } catch (e: Exception) {
            null
        }
        if (scanner == null) {
            onResult("蓝牙未开启，无法扫描")
            return
        }

        val handler = Handler(Looper.getMainLooper())
        val found = LinkedHashMap<String, String>()
        var stopped = false

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                val dev = result?.device ?: return
                val name = try {
                    result.scanRecord?.deviceName ?: dev.name
                } catch (e: Exception) {
                    null
                }
                found[dev.address] = name ?: "未命名设备"
            }

            override fun onScanFailed(errorCode: Int) {
                if (stopped) return
                stopped = true
                handler.post { onResult("扫描失败（错误码 $errorCode），请确认蓝牙已开启") }
            }
        }

        val ok = runCatching { scanner.startScan(callback) }.isSuccess
        if (!ok) {
            onResult("无法启动扫描，请确认蓝牙已开启")
            return
        }

        handler.postDelayed({
            if (stopped) return@postDelayed
            stopped = true
            runCatching { scanner.stopScan(callback) }
            onResult(
                if (found.isEmpty()) "扫描完成，周边暂无蓝牙设备"
                else "扫描到 ${found.size} 台蓝牙设备：\n" +
                    found.values.take(8).joinToString("\n") { "· $it" }
            )
        }, 6000)
    }

    // ---------------- 相机 ----------------

    /** 可用镜头数量（不申请权限也能读，用于判断硬件是否存在） */
    fun cameraSummary(ctx: Context): String {
        if (!ctx.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_CAMERA_ANY)) {
            return "本机无摄像头"
        }
        val count = try {
            (ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager).cameraIdList.size
        } catch (e: Exception) {
            0
        }
        val flash = ctx.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_CAMERA_FLASH)
        val countText = if (count > 0) "$count 个镜头" else "镜头数量未知"
        return "$countText · " + if (flash) "支持闪光灯" else "无闪光灯"
    }

    // ---------------- 相册 ----------------

    /**
     * 相册里能读到多少张图片。
     * 这是检验「相册权限是否真的生效」最直接的办法：
     * 未授权时查询会抛 SecurityException 或返回 0；
     * Android 14 选了「部分照片」时，这里只会统计到用户选中的那几张。
     */
    fun photoCount(ctx: Context): String {
        if (!Permissions.isGranted(ctx, Permissions.Group.GALLERY)) {
            return "尚未获得相册权限"
        }
        return try {
            val uri = android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            val projection = arrayOf(android.provider.MediaStore.Images.Media._ID)
            var count = 0
            ctx.contentResolver.query(uri, projection, null, null, null)?.use { c ->
                count = c.count
            }
            if (count == 0) "权限已允许，但相册里暂无可读图片"
            else "可读取相册图片 $count 张"
        } catch (e: SecurityException) {
            "权限被系统拒绝，请到系统设置里重新授予"
        } catch (e: Exception) {
            "读取相册失败：${e.message ?: "未知错误"}"
        }
    }
}
