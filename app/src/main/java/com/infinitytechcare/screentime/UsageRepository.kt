package com.infinitytechcare.screentime

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import java.util.Calendar

/** One app's foreground time for a day. */
data class AppUsage(
    val packageName: String,
    val label: String,
    val millis: Long
)

object UsageRepository {

    /**
     * Whether the user has turned on "Usage access" for this app in Settings.
     * This cannot be enabled programmatically, so the app can never read usage
     * without the owner of the phone explicitly allowing it.
     */
    fun hasUsageAccess(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /**
     * App usage for a single day. dayOffset 0 = today, -1 = yesterday, etc.
     * Returns apps that had any foreground time, most-used first.
     */
    fun usageForDay(context: Context, dayOffset: Int): List<AppUsage> {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val pm = context.packageManager

        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, dayOffset)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val start = cal.timeInMillis
        val end = start + 24L * 60 * 60 * 1000

        val stats = usm.queryAndAggregateUsageStats(start, end)
        val result = ArrayList<AppUsage>()
        for ((pkg, us) in stats) {
            val time = us.totalTimeInForeground
            if (time <= 0) continue
            if (pkg == context.packageName) continue
            val label = try {
                pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
            } catch (e: PackageManager.NameNotFoundException) {
                pkg
            }
            result.add(AppUsage(pkg, label, time))
        }
        result.sortByDescending { it.millis }
        return result
    }
}
