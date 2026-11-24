package com.infinitytechcare.screentime

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import java.util.Calendar

private data class BlockInfo(val type: String, val message: String)

/**
 * A visible foreground service that enforces the parent's rules by drawing a
 * full-screen lock over a blocked app. It never records or sends anything —
 * it only reads which app is in the foreground and shows a lock when a rule says so.
 */
class MonitorService : LifecycleService() {

    private val handler = Handler(Looper.getMainLooper())
    private var windowManager: WindowManager? = null
    private var overlay: View? = null
    private var overlayText: TextView? = null
    private var homePackage: String? = null

    private val intervalMs = 1500L

    // forced-break tracking (in-memory; resets if the service restarts)
    private var continuousActiveMs = 0L
    private var lastActiveWall = 0L
    private var breakUntil = 0L

    // alert edge-detection so we notify once per event, not every tick
    private var lastAlertKey: String? = null

    private val tick = object : Runnable {
        override fun run() {
            try {
                evaluate()
            } catch (_: Exception) {
                // never let the loop die on a transient error
            }
            handler.postDelayed(this, intervalMs)
        }
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        homePackage = resolveHomePackage()
        startForegroundNotification()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        handler.removeCallbacks(tick)
        handler.post(tick)
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        removeOverlay()
        super.onDestroy()
    }

    // --- core loop ---

    private fun evaluate() {
        if (!Prefs.isServiceEnabled(this)) {
            removeOverlay()
            stopSelf()
            return
        }

        val fg = foregroundApp()
        val isSystemish = fg == null || fg == packageName ||
            fg == homePackage || fg == "com.android.systemui"

        updateBreakCounters(active = !isSystemish)

        if (isSystemish) {
            removeOverlay()
            return
        }

        val info = blockReason(fg!!)
        if (info != null) {
            maybeAlert(info, fg)
            showOverlay(info.message)
        } else {
            lastAlertKey = null
            removeOverlay()
        }
    }

    private fun updateBreakCounters(active: Boolean) {
        val now = System.currentTimeMillis()
        if (active) {
            continuousActiveMs += intervalMs
            lastActiveWall = now
        } else {
            // a 2-minute gap of no foreground app counts as a real break
            if (now - lastActiveWall > 2 * 60_000) continuousActiveMs = 0
        }
        val breakAfter = Prefs.breakAfterMinutes(this)
        if (breakAfter > 0 && continuousActiveMs / 60_000 >= breakAfter && now >= breakUntil) {
            breakUntil = now + Prefs.breakForMinutes(this) * 60_000L
            continuousActiveMs = 0
        }
    }

    private fun blockReason(pkg: String): BlockInfo? {
        val now = System.currentTimeMillis()

        // 1. Forced break
        if (now < breakUntil) {
            val mins = ((breakUntil - now) / 60_000) + 1
            return BlockInfo("break", "Break time — take a rest for about $mins more min.")
        }

        // 2. Scheduled lock windows (bedtime / school / study)
        val cal = Calendar.getInstance()
        val nowMin = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        for (w in Prefs.windows(this)) {
            val inWindow = if (w.startMin <= w.endMin) {
                nowMin in w.startMin until w.endMin
            } else {
                nowMin >= w.startMin || nowMin < w.endMin
            }
            if (inWindow) return BlockInfo("win:${w.label}", "${w.label} — phone is locked right now.")
        }

        // 3. Total daily screen-time cap
        val cap = Prefs.totalCapMinutes(this)
        if (cap > 0 && totalTodayMinutes() >= cap) {
            return BlockInfo("cap", "Daily screen-time limit reached ($cap min).")
        }

        // 4. Whitelist: only allowed apps may run
        if (Prefs.isWhitelistEnabled(this) && !Prefs.allowed(this).contains(pkg)) {
            return BlockInfo("wl:$pkg", "This app is not on the allowed list.")
        }

        // 5. Per-app daily limit
        val limitMin = Prefs.limits(this)[pkg] ?: return null
        if (todayMinutes(pkg) >= limitMin) {
            return BlockInfo("lim:$pkg", "Time's up for today ($limitMin min).")
        }
        return null
    }

    // --- foreground app + usage ---

    private fun foregroundApp(): String? {
        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val events = usm.queryEvents(now - 10_000, now)
        var last: String? = null
        val e = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            if (e.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND ||
                e.eventType == UsageEvents.Event.ACTIVITY_RESUMED
            ) {
                last = e.packageName
            }
        }
        return last
    }

    private fun startOfToday(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private fun todayMinutes(pkg: String): Int {
        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val stats = usm.queryAndAggregateUsageStats(startOfToday(), System.currentTimeMillis())
        return ((stats[pkg]?.totalTimeInForeground ?: 0L) / 60_000).toInt()
    }

    private fun totalTodayMinutes(): Int {
        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val stats = usm.queryAndAggregateUsageStats(startOfToday(), System.currentTimeMillis())
        var ms = 0L
        for ((pkg, us) in stats) {
            if (pkg == packageName || pkg == homePackage || pkg == "com.android.systemui") continue
            ms += us.totalTimeInForeground
        }
        return (ms / 60_000).toInt()
    }

    // --- overlay ---

    private fun showOverlay(message: String) {
        if (!Settings.canDrawOverlays(this)) return
        if (overlay != null) {
            overlayText?.text = message
            return
        }
        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#F20D1F2D"))
            isClickable = true
        }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }
        val title = TextView(this).apply {
            text = getString(R.string.app_name)
            setTextColor(Color.WHITE)
            textSize = 22f
            gravity = Gravity.CENTER
        }
        val msg = TextView(this).apply {
            text = message
            setTextColor(Color.WHITE)
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(48, 24, 48, 36)
        }
        overlayText = msg
        val btn = Button(this).apply {
            text = "Go to home screen"
            setOnClickListener {
                startActivity(
                    Intent(Intent.ACTION_MAIN).apply {
                        addCategory(Intent.CATEGORY_HOME)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                )
            }
        }
        col.addView(title)
        col.addView(msg)
        col.addView(btn)
        root.addView(
            col,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
        )

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            PixelFormat.TRANSLUCENT
        )
        try {
            windowManager?.addView(root, params)
            overlay = root
        } catch (_: Exception) {
            overlay = null
            overlayText = null
        }
    }

    private fun removeOverlay() {
        val v = overlay ?: return
        try {
            windowManager?.removeView(v)
        } catch (_: Exception) {
        }
        overlay = null
        overlayText = null
    }

    // --- local alerts (fired on this phone only) ---

    private fun maybeAlert(info: BlockInfo, pkg: String) {
        if (!Prefs.isAlertsEnabled(this)) return
        if (info.type == lastAlertKey) return
        lastAlertKey = info.type
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val n = NotificationCompat.Builder(this, "alerts")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(info.message)
            .setAutoCancel(true)
            .build()
        nm.notify(info.type.hashCode(), n)
    }

    // --- misc ---

    private fun resolveHomePackage(): String? {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return packageManager.resolveActivity(intent, 0)?.activityInfo?.packageName
    }

    private fun startForegroundNotification() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel("monitor", "Screen time protection", NotificationManager.IMPORTANCE_LOW)
            )
            nm.createNotificationChannel(
                NotificationChannel("alerts", "Alerts", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val notification: Notification = NotificationCompat.Builder(this, "monitor")
            .setContentTitle(getString(R.string.app_name))
            .setContentText("Screen-time rules are active on this phone.")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setContentIntent(openIntent)
            .build()

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        ServiceCompat.startForeground(this, 1, notification, type)
    }

    companion object {
        fun start(context: Context) {
            val i = Intent(context, MonitorService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(i)
            } else {
                context.startService(i)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, MonitorService::class.java))
        }
    }
}
