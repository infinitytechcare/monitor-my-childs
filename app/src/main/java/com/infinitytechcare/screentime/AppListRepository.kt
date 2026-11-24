package com.infinitytechcare.screentime

import android.content.Context
import android.content.Intent

data class InstalledApp(val pkg: String, val label: String)

object AppListRepository {

    /** User-launchable apps on this phone, sorted by name. Excludes this app itself. */
    fun launchableApps(context: Context): List<InstalledApp> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = pm.queryIntentActivities(intent, 0)
        val seen = HashSet<String>()
        val out = ArrayList<InstalledApp>()
        for (ri in resolved) {
            val pkg = ri.activityInfo.packageName
            if (pkg == context.packageName) continue
            if (!seen.add(pkg)) continue
            val label = ri.loadLabel(pm).toString()
            out.add(InstalledApp(pkg, label))
        }
        out.sortBy { it.label.lowercase() }
        return out
    }
}
