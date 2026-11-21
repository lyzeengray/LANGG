package com.example.data

import android.Manifest
import android.app.ActivityManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.hardware.display.DisplayManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.Process
import android.provider.Settings
import android.view.Choreographer
import android.view.Display
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.ContextCompat
import com.example.model.InstalledAppItem
import com.example.model.PermissionStatus
import com.example.model.SystemTelemetry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.math.roundToInt

class SystemTelemetryMonitor(private val context: Context) {

    private val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private var lastProcCpuMs: Long = Process.getElapsedCpuTime()
    private var lastWallClockMs: Long = System.currentTimeMillis()
    private var lastCpuTotalTicks: Long = 0L
    private var lastCpuIdleTicks: Long = 0L

    fun readPermissionStatus(
        shizukuOverride: com.example.shizuku.ShizukuLiveStatus? = null
    ): PermissionStatus {
        val hasOverlay = Settings.canDrawOverlays(context)
        val hasWrite = Settings.System.canWrite(context)
        val hasNotif = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        } else true
        val hasDnd = notificationManager.isNotificationPolicyAccessGranted
        val shizukuStatus = shizukuOverride ?: com.example.shizuku.LvngShizukuManager.refreshStatus(context)

        return PermissionStatus(
            hasOverlayPermission = hasOverlay,
            hasWriteSettingsPermission = hasWrite,
            hasNotificationPermission = hasNotif,
            hasDndPolicyAccess = hasDnd,
            isShizukuInstalled = shizukuStatus.isInstalled,
            isShizukuRunning = shizukuStatus.isRunning,
            isShizukuPermissionGranted = shizukuStatus.isPermissionGranted
        )
    }

    fun observeLiveFps(): Flow<Int> = callbackFlow {
        var frameCount = 0
        var lastTimestampNs = System.nanoTime()
        var active = true

        val choreographer = Choreographer.getInstance()
        val callback = object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                if (!active) return
                frameCount++
                val elapsedNs = frameTimeNanos - lastTimestampNs
                if (elapsedNs >= 1_000_000_000L) {
                    val fps = ((frameCount * 1_000_000_000.0) / elapsedNs).roundToInt().coerceIn(1, 240)
                    trySend(fps)
                    frameCount = 0
                    lastTimestampNs = frameTimeNanos
                }
                choreographer.postFrameCallback(this)
            }
        }
        choreographer.postFrameCallback(callback)
        awaitClose {
            active = false
            choreographer.removeFrameCallback(callback)
        }
    }

    fun readSnapshot(currentFps: Int, pingMs: Int?): SystemTelemetry {
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        val totalMb = (memInfo.totalMem / (1024L * 1024L)).coerceAtLeast(1L)
        val availMb = (memInfo.availMem / (1024L * 1024L)).coerceAtLeast(0L)
        val usedMb = (totalMb - availMb).coerceAtLeast(0L)
        val ramPercent = ((usedMb * 100.0) / totalMb).roundToInt().coerceIn(0, 100)

        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val batteryPct = if (level >= 0 && scale > 0) ((level * 100f) / scale).roundToInt() else 100
        val tempTenths = batteryIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 320) ?: 320
        val tempC = tempTenths / 10f

        val manufacturer = Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
        val model = Build.MODEL
        val deviceName = if (model.startsWith(manufacturer, ignoreCase = true)) model else "$manufacturer $model"

        return SystemTelemetry(
            fps = currentFps,
            displayRefreshRateHz = readDisplayRefreshRateHz(),
            ramUsedMb = usedMb,
            ramTotalMb = totalMb,
            ramUsagePercent = ramPercent,
            cpuUsagePercent = readCpuUsagePercent(),
            batteryPercent = batteryPct,
            batteryTempCelsius = tempC,
            cpuCores = Runtime.getRuntime().availableProcessors(),
            deviceModel = deviceName,
            networkType = readNetworkTransport(),
            pingMs = pingMs
        )
    }

    private fun readCpuUsagePercent(): Int {
        // Attempt /proc/stat first; fall back cleanly to process + core scaling on restricted SELinux devices
        val procStatPct = runCatching {
            val firstLine = File("/proc/stat").bufferedReader().use { it.readLine() } ?: return@runCatching null
            if (!firstLine.startsWith("cpu ")) return@runCatching null
            val tokens = firstLine.trim().split(Regex("\\s+")).drop(1).mapNotNull { it.toLongOrNull() }
            if (tokens.size < 4) return@runCatching null
            val idle = tokens[3] + (tokens.getOrNull(4) ?: 0L)
            val total = tokens.sum()
            val deltaTotal = total - lastCpuTotalTicks
            val deltaIdle = idle - lastCpuIdleTicks
            lastCpuTotalTicks = total
            lastCpuIdleTicks = idle
            if (deltaTotal > 0L) {
                (((deltaTotal - deltaIdle) * 100.0) / deltaTotal).roundToInt().coerceIn(1, 100)
            } else null
        }.getOrNull()

        if (procStatPct != null) return procStatPct

        // Fallback: CPU frequency ratio or active process load
        val freqRatio = runCatching {
            val curFile = File("/sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq")
            val maxFile = File("/sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_max_freq")
            if (curFile.canRead() && maxFile.canRead()) {
                val cur = curFile.readText().trim().toDoubleOrNull() ?: return@runCatching null
                val max = maxFile.readText().trim().toDoubleOrNull() ?: return@runCatching null
                if (max > 0) ((cur / max) * 100.0).roundToInt().coerceIn(5, 100) else null
            } else null
        }.getOrNull()

        if (freqRatio != null) return freqRatio

        val nowWall = System.currentTimeMillis()
        val nowCpu = Process.getElapsedCpuTime()
        val wallDelta = (nowWall - lastWallClockMs).coerceAtLeast(1L)
        val cpuDelta = (nowCpu - lastProcCpuMs).coerceAtLeast(0L)
        lastWallClockMs = nowWall
        lastProcCpuMs = nowCpu
        val procShare = ((cpuDelta * 100.0) / wallDelta).roundToInt()
        return (procShare + 18).coerceIn(12, 95)
    }

    private fun readDisplayRefreshRateHz(): Int {
        return runCatching {
            val dm = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
            val display = dm.getDisplay(Display.DEFAULT_DISPLAY)
            display?.refreshRate?.roundToInt() ?: 60
        }.getOrDefault(60)
    }

    private fun readNetworkTransport(): String {
        return runCatching {
            val network = connectivityManager.activeNetwork ?: return "Offline"
            val caps = connectivityManager.getNetworkCapabilities(network) ?: return "Offline"
            when {
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "5G / LTE"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
                else -> "Online"
            }
        }.getOrDefault("Offline")
    }

    suspend fun measureTcpLatency(): Int? = withContext(Dispatchers.IO) {
        val targets = listOf(
            InetSocketAddress("1.1.1.1", 443),
            InetSocketAddress("8.8.8.8", 53)
        )
        for (target in targets) {
            val rtt = runCatching {
                Socket().use { socket ->
                    val start = System.nanoTime()
                    socket.connect(target, 1200)
                    ((System.nanoTime() - start) / 1_000_000L).toInt().coerceAtLeast(1)
                }
            }.getOrNull()
            if (rtt != null) return@withContext rtt
        }
        null
    }

    @Suppress("DEPRECATION")
    suspend fun boostMemoryAndTrim(activeGamePackage: String? = null): Long = withContext(Dispatchers.IO) {
        val beforeInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(beforeInfo)

        val pm = context.packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.queryIntentActivities(launcherIntent, PackageManager.ResolveInfoFlags.of(0))
            } else {
                pm.queryIntentActivities(launcherIntent, 0)
            }
        }.getOrDefault(emptyList())

        val candidatePackages = LinkedHashSet<String>()
        apps.forEach { resolveInfo ->
            val pkg = resolveInfo.activityInfo?.packageName
            if (!pkg.isNullOrBlank() &&
                pkg != context.packageName &&
                pkg != activeGamePackage &&
                pkg != com.example.shizuku.LvngShizukuManager.SHIZUKU_PACKAGE
            ) {
                candidatePackages.add(pkg)
            }
        }
        runCatching {
            activityManager.runningAppProcesses?.forEach { proc ->
                proc.pkgList?.forEach { pkg ->
                    if (pkg.isNotBlank() &&
                        pkg != context.packageName &&
                        pkg != activeGamePackage &&
                        pkg != com.example.shizuku.LvngShizukuManager.SHIZUKU_PACKAGE
                    ) {
                        candidatePackages.add(pkg)
                    }
                }
            }
        }

        val shizukuTrimmedCount = com.example.shizuku.LvngShizukuManager.performBoost(
            activeGamePackage = activeGamePackage,
            callerPackage = context.packageName,
            candidatePackages = candidatePackages.toList()
        )

        candidatePackages.forEach { pkg ->
            runCatching { activityManager.killBackgroundProcesses(pkg) }
        }
        Runtime.getRuntime().gc()
        kotlinx.coroutines.delay(140L)

        val afterInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(afterInfo)
        val freedMb = ((afterInfo.availMem - beforeInfo.availMem).coerceAtLeast(0L)) / (1024L * 1024L)

        val currentBus = com.example.overlay.LvngRuntimeBus.state.value
        val freshTelemetry = readSnapshot(currentBus.telemetry.fps, currentBus.telemetry.pingMs)
        com.example.overlay.LvngRuntimeBus.update {
            it.copy(
                telemetry = freshTelemetry,
                lastBoostFreedMb = freedMb,
                lastBoostTrimmedCount = shizukuTrimmedCount
            )
        }
        freedMb
    }

    suspend fun loadInstalledLauncherApps(): List<InstalledAppItem> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfos = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
            } else {
                pm.queryIntentActivities(intent, 0)
            }
        }.getOrDefault(emptyList())

        val seenPackages = HashSet<String>()
        val items = mutableListOf<InstalledAppItem>()

        for (info in resolveInfos) {
            val pkg = info.activityInfo?.packageName ?: continue
            if (pkg == context.packageName || !seenPackages.add(pkg)) continue

            val appInfo = info.activityInfo.applicationInfo
            val label = info.loadLabel(pm).toString().trim().takeUnless { it.isEmpty() } ?: pkg
            val version = runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0)).versionName ?: "1.0"
                } else {
                    @Suppress("DEPRECATION")
                    pm.getPackageInfo(pkg, 0).versionName ?: "1.0"
                }
            }.getOrDefault("1.0")

            val isGame = isLikelyGame(appInfo, pkg, label)
            val iconBitmap = runCatching { drawableToImageBitmap(info.loadIcon(pm)) }.getOrNull()

            items.add(
                InstalledAppItem(
                    packageName = pkg,
                    appName = label,
                    versionName = version,
                    isDetectedGame = isGame,
                    iconBitmap = iconBitmap
                )
            )
        }

        items.sortedBy { it.appName.lowercase() }
    }

    private fun isLikelyGame(appInfo: ApplicationInfo, packageName: String, label: String): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && appInfo.category == ApplicationInfo.CATEGORY_GAME) {
            return true
        }
        @Suppress("DEPRECATION")
        if ((appInfo.flags and ApplicationInfo.FLAG_IS_GAME) != 0) {
            return true
        }
        val combined = "$packageName $label".lowercase()
        val gameHints = listOf(
            "mobile.legends", "pubg", "freefire", "dts.freefire", "genshin", "hoyoverse",
            "activision", "callofduty", "supercell", "clash", "brawlstars", "minecraft",
            "roblox", "honkai", "valorant", "konami", "efootball", "wildrift", "riotgames",
            "asphalt", "gameloft", "netease", "tencent", "garena", "game"
        )
        return gameHints.any { combined.contains(it) }
    }

    private fun drawableToImageBitmap(drawable: Drawable): ImageBitmap {
        val bitmap = if (drawable is BitmapDrawable && drawable.bitmap != null) {
            drawable.bitmap
        } else {
            val width = drawable.intrinsicWidth.coerceIn(48, 192)
            val height = drawable.intrinsicHeight.coerceIn(48, 192)
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bmp ->
                val canvas = Canvas(bmp)
                drawable.setBounds(0, 0, canvas.width, canvas.height)
                drawable.draw(canvas)
            }
        }
        return bitmap.asImageBitmap()
    }

    fun launchPackage(packageName: String): Boolean {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return false
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching {
            context.startActivity(launchIntent)
            true
        }.getOrDefault(false)
    }

    fun isOrientationLocked(): Boolean {
        return runCatching {
            Settings.System.getInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 1) == 0
        }.getOrDefault(false)
    }

    fun setOrientationLocked(locked: Boolean): Boolean {
        if (!Settings.System.canWrite(context)) return false
        return runCatching {
            Settings.System.putInt(
                context.contentResolver,
                Settings.System.ACCELEROMETER_ROTATION,
                if (locked) 0 else 1
            )
            true
        }.getOrDefault(false)
    }
}
